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
import androidx.core.net.toUri
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.wastaken.kotatsu.api21.R
import org.wastaken.kotatsu.api21.core.util.ext.getDisplayMessage
import org.wastaken.kotatsu.api21.databinding.LayoutBooruVideoOverlayBinding
import org.wastaken.kotatsu.api21.reader.domain.PageLoader
import org.wastaken.kotatsu.api21.reader.ui.pager.ReaderPage

/**
 * Explicit-load video support for booru reader pages.
 *
 * Videos must never load or play automatically: when a booru page URL ends in a
 * video extension (.mp4/.webm/.gifv, see BooruMedia.kt) this overlay shows a
 * static placeholder with a "Load video" button and the normal page pipeline is
 * suppressed (no prefetch, no page download). Tapping the button offers the choice
 * to play in-app or open the original URL externally.
 *
 * In-app playback downloads the file first through the regular page loader (source
 * headers / proxy / fallback apply, result lands in the page cache) and then plays
 * it in a plain [VideoView] — download-then-play is deliberate: streaming with
 * source headers cannot be expressed on [VideoView] and starting playback before
 * the file is cached makes scrubbing unreliable. Fails over to an external player
 * if the device cannot decode the stream.
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
	private var videoView: VideoView? = null

	private val audioManager: AudioManager by lazy(LazyThreadSafetyMode.NONE) {
		binding.root.context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
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
		binding.root.isVisible = true
		binding.panelVideo.isVisible = true
		binding.progressVideo.isGone = true
		binding.buttonLoadVideo.isVisible = true
		binding.videoContainer.isGone = true
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
		binding.root.isGone = true
	}

	private fun showChoiceDialog(page: ReaderPage) {
		val options = arrayOf(
			binding.root.resources.getString(R.string.play_in_app),
			binding.root.resources.getString(R.string.open_external),
		)
		MaterialAlertDialogBuilder(binding.root.context)
			.setTitle(R.string.load_video)
			.setItems(options) { _, which ->
				when (which) {
					0 -> playInApp(page)
					1 -> openExternalOrError(page)
				}
			}
			.setNegativeButton(android.R.string.cancel, null)
			.show()
	}

	private fun playInApp(page: ReaderPage) {
		if (loadJob?.isActive == true) {
			return
		}
		loadJob = lifecycleOwner.lifecycleScope.launch {
			binding.buttonLoadVideo.isGone = true
			binding.progressVideo.isVisible = true
			try {
				val uri = loader.loadPage(page.toMangaPage(), force = false)
				startPlayback(uri)
			} catch (e: CancellationException) {
				throw e
			} catch (e: Throwable) {
				binding.progressVideo.isGone = true
				binding.buttonLoadVideo.isVisible = true
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
		val appContext = binding.root.context.applicationContext
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

	private fun stopVideo() {
		videoView?.stopPlayback()
		if (videoView != null) {
			abandonAudioFocus()
		}
		binding.videoContainer.isGone = true
		if (isHandling) {
			// back to the static placeholder: explicit load is required again
			binding.panelVideo.isVisible = true
			binding.progressVideo.isGone = true
			binding.buttonLoadVideo.isVisible = true
		}
	}

	private fun onPlaybackError() {
		stopVideo()
		val page = currentPage
		if (page == null || !openExternal(page)) {
			// no fallback: the device cannot decode the clip and nothing can play it externally either
			Snackbar.make(binding.root, R.string.error_occurred, Snackbar.LENGTH_LONG).show()
		}
	}

	private fun openExternalOrError(page: ReaderPage) {
		if (!openExternal(page)) {
			Snackbar.make(binding.root, R.string.error_occurred, Snackbar.LENGTH_SHORT).show()
		}
	}

	private fun openExternal(page: ReaderPage): Boolean {
		val intent = Intent(Intent.ACTION_VIEW)
			.setDataAndType(page.url.toUri(), page.url.videoMimeType())
		val context = binding.root.context
		return if (intent.resolveActivity(context.packageManager) != null) {
			context.startActivity(intent)
			true
		} else {
			false
		}
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
}
