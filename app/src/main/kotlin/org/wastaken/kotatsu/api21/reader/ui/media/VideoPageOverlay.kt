package org.wastaken.kotatsu.api21.reader.ui.media

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.VideoView
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.wastaken.kotatsu.api21.R
import org.wastaken.kotatsu.api21.core.util.ext.getDisplayMessage
import org.wastaken.kotatsu.api21.core.util.ext.isNetworkUri
import org.wastaken.kotatsu.api21.databinding.LayoutBooruVideoOverlayBinding
import org.wastaken.kotatsu.api21.reader.domain.PageLoader
import org.wastaken.kotatsu.api21.reader.ui.pager.ReaderPage
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date

/**
 * Explicit-load video support for booru reader pages.
 *
 * Videos never load or play automatically: when a booru page URL ends in a video
 * extension (.mp4/.webm/.gifv, see BooruMedia.kt) this overlay shows a static
 * placeholder with a "Load video" button and the normal page pipeline is
 * suppressed (no prefetch, no page download). Tapping the button offers:
 *  - Play in app: downloads the file through the regular page loader (source
 *    headers / proxy / fallback apply, result lands in the page cache) and plays
 *    it in a [VideoView] with a custom controller: play/pause, seek bar with
 *    live position/duration, ±10 s skip buttons and double-tap zone gestures,
 *    loop toggle and playback-speed cycle (API 23+, hidden on API 21-22).
 *    A buffering spinner reflects prepare/seek stalls. Download-then-play is
 *    deliberate: streaming with source headers is impossible on [VideoView].
 *    Decode failures fall back to an external player.
 *  - Download video: saves the file into the app's configured download folder,
 *    honoring the "save original bytes" preference (untouched source download
 *    straight to storage instead of the page-cache copy).
 *  - Play in VLC / Open external: hands the original URL to VLC (with a proper
 *    title extra; falls back to any external handler when VLC is not installed).
 *
 * Quality selection is automatic: [String.videoStreamVariants] returning more
 * than one entry (Danbooru 720p/480p/360p siblings, etc.) inserts a quality
 * pick step before the action dialog; single-variant pages skip it.
 *
 * [VideoView] is created with the application context: using the Activity
 * context leaks SubtitleController instances on API 21. Playback is stopped
 * (and the player released) as soon as the page is paused, stopped or recycled —
 * decoded video surfaces and heap buffers must not be kept on low-RAM devices.
 * Controls auto-hide while playing; a single tap on the video toggles them.
 */
class VideoPageOverlay(
	private val binding: LayoutBooruVideoOverlayBinding,
	private val loader: PageLoader,
	private val lifecycleOwner: LifecycleOwner,
) : DefaultLifecycleObserver {

	var isHandling = false
		private set

	private var loadJob: Job? = null
	private var currentPage: ReaderPage? = null
	private var currentStreamUrl: String? = null

	private var videoView: VideoView? = null
	private var mediaPlayer: MediaPlayer? = null
	private var isLoopEnabled = false
	private var isPlaybackCompleted = false
	private var isSeekBarDragged = false
	private var speedIndex = DEFAULT_SPEED_INDEX

	private val appContext: Context
		get() = binding.root.context.applicationContext

	private val entryPoint: ReaderMediaEntryPoint by lazy(LazyThreadSafetyMode.NONE) {
		EntryPointAccessors.fromApplication(appContext, ReaderMediaEntryPoint::class.java)
	}

	private val audioManager: AudioManager by lazy(LazyThreadSafetyMode.NONE) {
		appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
	}

	private val handler = Handler(Looper.getMainLooper())

	private val positionTicker = object : Runnable {
		override fun run() {
			updatePositionViews()
			handler.postDelayed(this, POSITION_TICK_MS)
		}
	}

	private val hideControlsAction = Runnable { binding.controlsBar.isGone = true }

	@Suppress("DEPRECATION") // constructor with explicit Looper requires API 33
	private val gestureDetector = GestureDetector(appContext, object : GestureDetector.SimpleOnGestureListener() {

		override fun onDown(e: MotionEvent): Boolean = true

		override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
			toggleControlsVisibility()
			return true
		}

		override fun onDoubleTap(e: MotionEvent): Boolean {
			val width = binding.videoContainer.width
			if (width <= 0) {
				return false
			}
			return when {
				e.x < width / 3f -> {
					seekBy(-SKIP_MS)
					true
				}
				e.x > width * 2f / 3f -> {
					seekBy(SKIP_MS)
					true
				}
				else -> false
			}
		}
	})

	private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { what ->
		when (what) {
			AudioManager.AUDIOFOCUS_LOSS -> stopVideo()
			AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
			AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> videoView?.pause()
		}
	}

	init {
		lifecycleOwner.lifecycle.addObserver(this)
		bindControllerViews()
	}

	/** @return true if this overlay handles the page and the normal page load must be suppressed. */
	fun onBind(page: ReaderPage): Boolean {
		reset()
		// source check comes first, always (see BooruMedia.kt): a non-booru page
		// with a video-looking URL is just an ordinary page for this overlay
		isHandling = page.isBooru() && page.url.looksLikeVideo()
		if (!isHandling) {
			return false
		}
		currentPage = page
		currentStreamUrl = page.url
		binding.root.isVisible = true
		showButtonState()
		binding.buttonLoadVideo.setOnClickListener { showChoiceDialog(page) }
		return true
	}

	fun onRecycled() {
		reset()
	}

	override fun onPause(owner: LifecycleOwner) {
		stopVideo()
	}

	override fun onStop(owner: LifecycleOwner) {
		stopVideo()
	}

	override fun onDestroy(owner: LifecycleOwner) {
		stopVideo()
		lifecycleOwner.lifecycle.removeObserver(this)
	}

	private fun reset() {
		loadJob?.cancel()
		loadJob = null
		stopVideo()
		isLoopEnabled = false
		speedIndex = DEFAULT_SPEED_INDEX
		binding.buttonLoop.alpha = TOGGLE_INACTIVE_ALPHA
		binding.buttonSpeed.text = PLAYBACK_SPEED_LABELS[DEFAULT_SPEED_INDEX]
		currentPage = null
		currentStreamUrl = null
		binding.root.isGone = true
	}

	// region controller

	@SuppressLint("ClickableViewAccessibility")
	private fun bindControllerViews() {
		binding.videoContainer.setOnTouchListener { _, event ->
			gestureDetector.onTouchEvent(event)
			true
		}
		binding.controlsBar.isClickable = true // keep taps on the bar from toggling itself
		binding.buttonPlayPause.setOnClickListener { togglePlayPause() }
		binding.buttonSkipBack.setOnClickListener { seekBy(-SKIP_MS) }
		binding.buttonSkipForward.setOnClickListener { seekBy(SKIP_MS) }
		binding.buttonLoop.alpha = TOGGLE_INACTIVE_ALPHA
		binding.buttonLoop.setOnClickListener {
			isLoopEnabled = !isLoopEnabled
			mediaPlayer?.isLooping = isLoopEnabled
			binding.buttonLoop.alpha = if (isLoopEnabled) TOGGLE_ACTIVE_ALPHA else TOGGLE_INACTIVE_ALPHA
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			binding.buttonSpeed.setOnClickListener { cyclePlaybackSpeed() }
		} else {
			// MediaPlayer.setPlaybackParams requires API 23
			binding.buttonSpeed.isGone = true
		}
		binding.seekVideo.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {

			override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
				if (fromUser) {
					binding.textPosition.text = formatTime(progress)
				}
			}

			override fun onStartTrackingTouch(seekBar: SeekBar) {
				isSeekBarDragged = true
				handler.removeCallbacks(hideControlsAction)
			}

			override fun onStopTrackingTouch(seekBar: SeekBar) {
				isSeekBarDragged = false
				videoView?.seekTo(seekBar.progress)
				showControls(autoHide = videoView?.isPlaying == true)
			}
		})
	}

	private fun toggleControlsVisibility() {
		if (binding.videoContainer.isGone) {
			return
		}
		if (binding.controlsBar.isVisible) {
			binding.controlsBar.isGone = true
			handler.removeCallbacks(hideControlsAction)
		} else {
			showControls(autoHide = videoView?.isPlaying == true)
		}
	}

	private fun showControls(autoHide: Boolean) {
		binding.controlsBar.isVisible = true
		handler.removeCallbacks(hideControlsAction)
		if (autoHide) {
			handler.postDelayed(hideControlsAction, CONTROLS_HIDE_MS)
		}
	}

	private fun togglePlayPause() {
		val view = videoView ?: return
		if (view.isPlaying) {
			view.pause()
			showControls(autoHide = false)
		} else {
			if (isPlaybackCompleted) {
				view.seekTo(0)
				isPlaybackCompleted = false
			}
			view.start()
			showControls(autoHide = true)
		}
		updatePlayPauseIcon()
	}

	private fun seekBy(deltaMs: Int) {
		val view = videoView ?: return
		val duration = view.duration
		if (duration <= 0) {
			return
		}
		view.seekTo((view.currentPosition + deltaMs).coerceIn(0, duration))
		updatePositionViews()
		showControls(autoHide = view.isPlaying)
	}

	private fun cyclePlaybackSpeed() {
		val player = mediaPlayer ?: return
		speedIndex = (speedIndex + 1) % PLAYBACK_SPEEDS.size
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			runCatching { player.playbackParams = player.playbackParams.setSpeed(PLAYBACK_SPEEDS[speedIndex]) }
		}
		binding.buttonSpeed.text = PLAYBACK_SPEED_LABELS[speedIndex]
	}

	private fun updatePositionViews() {
		val view = videoView ?: return
		if (!isSeekBarDragged) {
			binding.seekVideo.progress = view.currentPosition.coerceAtLeast(0)
		}
		binding.textPosition.text = formatTime(
			if (isSeekBarDragged) binding.seekVideo.progress else view.currentPosition,
		)
		updatePlayPauseIcon()
	}

	private fun updatePlayPauseIcon() {
		val playing = videoView?.isPlaying == true
		binding.buttonPlayPause.setImageResource(
			if (playing) R.drawable.ic_action_pause else R.drawable.ic_play,
		)
		binding.buttonPlayPause.contentDescription = binding.root.resources.getString(
			if (playing) R.string.pause else R.string.play,
		)
	}

	private fun formatTime(ms: Int): String {
		val totalSeconds = ms.coerceAtLeast(0) / 1000
		return TIME_PATTERN.format(totalSeconds / 60, totalSeconds % 60)
	}

	// endregion

	// region in-app playback

	private fun playInApp(page: ReaderPage, streamUrl: String) {
		if (loadJob?.isActive == true) {
			return
		}
		loadJob = lifecycleOwner.lifecycleScope.launch {
			showLoadingState()
			try {
				val uri = loader.loadPage(mangaPageWithUrl(page, streamUrl), force = false)
				startPlayback(uri)
			} catch (e: CancellationException) {
				throw e
			} catch (e: Throwable) {
				showButtonState()
				Snackbar.make(
					binding.root,
					e.getDisplayMessage(binding.root.resources),
					Snackbar.LENGTH_SHORT,
				).show()
			}
		}
	}

	private fun startPlayback(uri: Uri) {
		val view = requireVideoView()
		isPlaybackCompleted = false
		binding.progressVideo.isGone = true
		binding.panelVideo.isGone = true
		binding.videoContainer.isVisible = true
		binding.progressBuffering.isVisible = true
		// playback actually starts in the OnPreparedListener after audio focus is granted
		view.setVideoURI(uri)
	}

	private fun requireVideoView(): VideoView {
		videoView?.let { return it }
		// application context on purpose: an Activity context would leak on API 21
		val view = VideoView(appContext)
		// index 0: keep the XML controller layer (buffering + controls bar) on top
		binding.videoContainer.addView(
			view,
			0,
			FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT,
				Gravity.CENTER,
			),
		)
		view.setOnPreparedListener { mp ->
			mediaPlayer = mp
			mp.isLooping = isLoopEnabled
			requestAudioFocus()
			binding.progressBuffering.isGone = true
			binding.seekVideo.max = view.duration.coerceAtLeast(0)
			binding.textDuration.text = formatTime(view.duration)
			view.start()
			updatePlayPauseIcon()
			showControls(autoHide = true)
			handler.removeCallbacks(positionTicker)
			handler.post(positionTicker)
		}
		view.setOnInfoListener { _, what, _ ->
			when (what) {
				MediaPlayer.MEDIA_INFO_BUFFERING_START -> binding.progressBuffering.isVisible = true
				MediaPlayer.MEDIA_INFO_BUFFERING_END -> binding.progressBuffering.isGone = true
			}
			false
		}
		view.setOnCompletionListener {
			isPlaybackCompleted = true
			updatePlayPauseIcon()
			showControls(autoHide = false)
		}
		view.setOnErrorListener { _, _, _ ->
			onPlaybackError()
			true
		}
		videoView = view
		return view
	}

	private fun onPlaybackError() {
		stopVideo()
		val url = currentStreamUrl
		if (url == null || !openExternal(url)) {
			// device cannot decode the clip and nothing can play it externally either
			showErrorSnackbar(Snackbar.LENGTH_LONG)
		}
	}

	private fun stopVideo() {
		handler.removeCallbacks(positionTicker)
		handler.removeCallbacks(hideControlsAction)
		videoView?.stopPlayback()
		if (videoView != null) {
			abandonAudioFocus()
		}
		mediaPlayer = null
		isPlaybackCompleted = false
		isSeekBarDragged = false
		binding.progressBuffering.isGone = true
		binding.controlsBar.isGone = true
		binding.videoContainer.isGone = true
		if (isHandling) {
			// back to the static placeholder: explicit load is required again
			showButtonState()
		}
	}

	// endregion

	// region download to storage

	private fun downloadVideo(page: ReaderPage, streamUrl: String) {
		if (loadJob?.isActive == true) {
			return
		}
		loadJob = lifecycleOwner.lifecycleScope.launch {
			showLoadingState()
			try {
				val dir = entryPoint.settings().getPagesSaveDir(appContext)
				if (dir == null) {
					Snackbar.make(binding.root, R.string.no_download_folder, Snackbar.LENGTH_LONG).show()
				} else {
					val baseName = SAVE_BASE_NAME + nameDateFormat.format(Date())
					val extension = streamUrl.urlExtension().ifEmpty { FALLBACK_EXTENSION }
					val doc = dir.createFile(streamUrl.videoMimeType(), "$baseName.$extension")
						?: throw IOException("Cannot create destination file")
					val settings = entryPoint.settings()
					if (settings.isPagesSaveOriginalEnabled && streamUrl.toUri().isNetworkUri()) {
						// save-bytes preference: untouched original straight from the source
						entryPoint.originalImageDownloader().download(streamUrl, page.source, doc.uri)
					} else {
						copyTo(loader.loadPage(mangaPageWithUrl(page, streamUrl), force = false), doc.uri)
					}
					Snackbar.make(binding.root, R.string.video_saved, Snackbar.LENGTH_LONG).show()
				}
				showButtonState()
			} catch (e: CancellationException) {
				throw e
			} catch (e: Throwable) {
				showButtonState()
				Snackbar.make(
					binding.root,
					e.getDisplayMessage(binding.root.resources),
					Snackbar.LENGTH_SHORT,
				).show()
			}
		}
	}

	private suspend fun copyTo(sourceUri: Uri, destination: Uri) = runInterruptible(Dispatchers.IO) {
		val out = appContext.contentResolver.openOutputStream(destination)
			?: throw IOException("Cannot open output stream for $destination")
		out.use { output ->
			sourceUri.toFile().inputStream().use { input ->
				input.copyTo(output)
			}
		}
	}

	// endregion

	// region dialogs

	private fun showChoiceDialog(page: ReaderPage) {
		val variants = page.url.videoStreamVariants()
		if (variants.size > 1) {
			showQualityDialog(page, variants)
		} else {
			showActionDialog(page, variants.first().url)
		}
	}

	private fun showQualityDialog(page: ReaderPage, variants: List<StreamVariant>) {
		MaterialAlertDialogBuilder(binding.root.context)
			.setTitle(R.string.select_quality)
			.setItems(variants.map { it.label }.toTypedArray()) { _, which ->
				showActionDialog(page, variants[which].url)
			}
			.setNegativeButton(android.R.string.cancel, null)
			.show()
	}

	private fun showActionDialog(page: ReaderPage, streamUrl: String) {
		val res = binding.root.resources
		val items = arrayOf(
			res.getString(R.string.play_in_app),
			res.getString(R.string.download_video),
			res.getString(R.string.play_in_vlc),
			res.getString(R.string.open_external),
		)
		currentStreamUrl = streamUrl
		MaterialAlertDialogBuilder(binding.root.context)
			.setTitle(R.string.load_video)
			.setItems(items) { _, which ->
				when (which) {
					0 -> playInApp(page, streamUrl)
					1 -> downloadVideo(page, streamUrl)
					2 -> {
						// VLC first; transparently fall back to any external handler
						if (!openExternal(streamUrl, VLC_PACKAGE) && !openExternal(streamUrl)) {
							showErrorSnackbar(Snackbar.LENGTH_SHORT)
						}
					}
					3 -> if (!openExternal(streamUrl)) {
						showErrorSnackbar(Snackbar.LENGTH_SHORT)
					}
				}
			}
			.setNegativeButton(android.R.string.cancel, null)
			.show()
	}

	// endregion

	// region external players

	private fun openExternal(url: String, packageName: String? = null): Boolean {
		val intent = Intent(Intent.ACTION_VIEW)
			.setDataAndType(url.toUri(), url.videoMimeType())
		if (packageName != null) {
			intent.setPackage(packageName)
		}
		// proper player metadata: external apps show the file name instead of the raw URL.
		// resolveActivity with a fixed package is unfiltered here because the manifest
		// holds QUERY_ALL_PACKAGES (Android 11 package visibility would otherwise hide VLC)
		intent.putExtra("title", url.urlFileName())
		val context = binding.root.context
		return if (intent.resolveActivity(context.packageManager) != null) {
			context.startActivity(intent)
			true
		} else {
			false
		}
	}

	private fun showErrorSnackbar(length: Int) {
		Snackbar.make(binding.root, R.string.error_occurred, length).show()
	}

	// endregion

	private fun mangaPageWithUrl(page: ReaderPage, url: String): MangaPage {
		return if (url == page.url) {
			page.toMangaPage()
		} else {
			// quality-variant streams are cached under their own URL
			MangaPage(id = page.id, url = url, preview = null, source = page.source)
		}
	}

	private fun showLoadingState() {
		binding.buttonLoadVideo.isGone = true
		binding.panelVideo.isVisible = true
		binding.progressVideo.isVisible = true
	}

	private fun showButtonState() {
		if (!isHandling) {
			return
		}
		binding.panelVideo.isVisible = true
		binding.progressVideo.isGone = true
		binding.buttonLoadVideo.isVisible = true
	}

	@Suppress("DEPRECATION") // AudioFocusRequest requires API 26, this fork targets API 21
	private fun requestAudioFocus() {
		audioManager.requestAudioFocus(
			focusChangeListener,
			AudioManager.STREAM_MUSIC,
			AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
		)
	}

	@Suppress("DEPRECATION")
	private fun abandonAudioFocus() {
		audioManager.abandonAudioFocus(focusChangeListener)
	}

	private companion object {

		private const val VLC_PACKAGE = "org.videolan.vlc"
		private const val SAVE_BASE_NAME = "booru-video-"
		private const val FALLBACK_EXTENSION = "mp4"
		private const val TIME_PATTERN = "%02d:%02d"
		private const val POSITION_TICK_MS = 250L
		private const val CONTROLS_HIDE_MS = 3500L
		private const val SKIP_MS = 10_000
		private const val TOGGLE_ACTIVE_ALPHA = 1f
		private const val TOGGLE_INACTIVE_ALPHA = 0.5f
		private const val DEFAULT_SPEED_INDEX = 0

		private val PLAYBACK_SPEEDS = floatArrayOf(1f, 1.25f, 1.5f, 2f, 0.5f)
		private val PLAYBACK_SPEED_LABELS = arrayOf("1x", "1.25x", "1.5x", "2x", "0.5x")

		// all accesses happen on the main thread (same pattern as PageSaveHelper)
		private val nameDateFormat = SimpleDateFormat("yyyy-MM-dd_HHmm")
	}
}
