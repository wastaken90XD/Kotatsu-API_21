package org.wastaken.kotatsu.api21.reader.ui.media

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.MediaController
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
import okio.sink
import okio.source
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.wastaken.kotatsu.api21.R
import org.wastaken.kotatsu.api21.core.util.ext.getDisplayMessage
import org.wastaken.kotatsu.api21.core.util.ext.isNetworkUri
import org.wastaken.kotatsu.api21.core.util.ext.writeAllCancellable
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
 *    it in a [VideoView] with a [MediaController]. Download-then-play is
 *    deliberate: streaming with source headers is impossible on [VideoView].
 *    Decode failures fall back to an external player.
 *  - Download video: saves the file into the app's configured download folder,
 *    honoring the "save original bytes" preference (untouched source download
 *    straight to storage instead of the page-cache copy).
 *  - Play in VLC / Open external: hands the original URL to VLC (falls back to
 *    any external handler when VLC is not installed).
 *
 * Quality selection is automatic: [String.videoStreamVariants] returning more
 * than one entry inserts a quality pick step before the action dialog
 * (single-variant pages, the common case today, skip it).
 *
 * [VideoView] and [MediaController] are created with the application context:
 * using the Activity context leaks SubtitleController instances on API 21.
 * Playback is stopped (and the player released) as soon as the page is paused,
 * stopped or recycled — decoded video surfaces and heap buffers must not be kept
 * around on low-RAM devices.
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

	private val appContext: Context
		get() = binding.root.context.applicationContext

	private val entryPoint: ReaderMediaEntryPoint by lazy(LazyThreadSafetyMode.NONE) {
		EntryPointAccessors.fromApplication(appContext, ReaderMediaEntryPoint::class.java)
	}

	private val audioManager: AudioManager by lazy(LazyThreadSafetyMode.NONE) {
		appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
	}

	private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { what ->
		when (what) {
			AudioManager.AUDIOFOCUS_LOSS -> stopVideo()
			AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
			AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> videoView?.pause()
		}
	}

	init {
		lifecycleOwner.lifecycle.addObserver(this)
	}

	/** @return true if this overlay handles the page and the normal page load must be suppressed. */
	fun onBind(page: ReaderPage): Boolean {
		reset()
		isHandling = page.isBooruVideo()
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
		currentPage = null
		currentStreamUrl = null
		binding.root.isGone = true
	}

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
		binding.progressVideo.isGone = true
		binding.panelVideo.isGone = true
		binding.videoContainer.isVisible = true
		// playback actually starts in the OnPreparedListener after audio focus is granted
		view.setVideoURI(uri)
	}

	private fun requireVideoView(): VideoView {
		videoView?.let { return it }
		// application context on purpose: an Activity context would leak on API 21
		val view = VideoView(appContext)
		view.layoutParams = FrameLayout.LayoutParams(
			ViewGroup.LayoutParams.MATCH_PARENT,
			ViewGroup.LayoutParams.MATCH_PARENT,
			Gravity.CENTER,
		)
		val controller = MediaController(appContext)
		controller.setAnchorView(view)
		view.setMediaController(controller)
		view.setOnPreparedListener {
			requestAudioFocus()
			view.start()
		}
		view.setOnErrorListener { _, _, _ ->
			onPlaybackError()
			true
		}
		binding.videoContainer.addView(view)
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
		videoView?.stopPlayback()
		if (videoView != null) {
			abandonAudioFocus()
		}
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
		out.sink().buffer().use { sink ->
			sourceUri.toFile().source().use { input ->
				sink.writeAllCancellable(input)
			}
		}
	}

	// endregion

	// region external players

	private fun openExternal(url: String, packageName: String? = null): Boolean {
		val intent = Intent(Intent.ACTION_VIEW)
			.setDataAndType(url.toUri(), url.videoMimeType())
		if (packageName != null) {
			intent.setPackage(packageName)
		}
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

		// all accesses happen on the main thread (same pattern as PageSaveHelper)
		private val nameDateFormat = SimpleDateFormat("yyyy-MM-dd_HHmm")
	}
}
