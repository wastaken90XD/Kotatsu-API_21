package org.wastaken.kotatsu.api21.reader.ui.media

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.wastaken.kotatsu.api21.R
import org.wastaken.kotatsu.api21.core.util.ext.getDisplayMessage
import org.wastaken.kotatsu.api21.core.util.ext.isNetworkUri
import org.wastaken.kotatsu.api21.databinding.LayoutBooruVideoOverlayBinding
import org.wastaken.kotatsu.api21.reader.ui.pager.ReaderPage
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date

/**
 * Explicit-load video support for booru reader pages. **Booru sources only**
 * (source check first, always — see BooruMedia.kt).
 *
 * Videos never load or play automatically and the reader UI never changes for
 * them: a static placeholder with a "Load video" button replaces the page and
 * the normal image pipeline stays suppressed (no prefetch, no page download).
 * There is deliberately NO in-app player UI in the reader.
 *
 * Tapping the button offers exactly two destinations for the file:
 *  - Play externally: hands the ORIGINAL URL to VLC (title extra so the player
 *    shows the file name) and transparently falls back to any other video app,
 *    resolving the intent first so a device without players just gets a message.
 *  - Download video: the one and only case where the full file lands on the
 *    device — streamed (in chunks, never held whole in memory) straight from
 *    the source with its headers into the configured download folder. Video
 *    URLs deliberately bypass the image proxy chain (it cannot optimize video
 *    and wedged downloads were the "keeps loading forever" symptom).
 *
 * Quality selection appears only when the source publishes its variants
 * (see [String.videoStreamVariants]); the network request itself is what
 * decides whether a picked variant exists at runtime.
 */
class VideoPageOverlay(
	private val binding: LayoutBooruVideoOverlayBinding,
	private val lifecycleOwner: LifecycleOwner,
) {

	var isHandling = false
		private set

	private var loadJob: Job? = null
	private var currentStreamUrl: String? = null

	private val appContext: Context
		get() = binding.root.context.applicationContext

	private val entryPoint: ReaderMediaEntryPoint by lazy(LazyThreadSafetyMode.NONE) {
		EntryPointAccessors.fromApplication(appContext, ReaderMediaEntryPoint::class.java)
	}

	/** @return true if this overlay handles the page and the normal page load must be suppressed. */
	fun onBind(page: ReaderPage): Boolean {
		reset()
		// source check comes first, always (see BooruMedia.kt)
		isHandling = page.isBooru() && page.url.looksLikeVideo()
		if (!isHandling) {
			return false
		}
		binding.root.isVisible = true
		showButtonState()
		binding.buttonLoadVideo.setOnClickListener { showChoiceDialog(page) }
		return true
	}

	fun onRecycled() {
		reset()
	}

	private fun reset() {
		loadJob?.cancel()
		loadJob = null
		currentStreamUrl = null
		binding.root.isGone = true
	}

	// region download to storage

	private fun downloadVideo(page: ReaderPage, streamUrl: String, mimeType: String) {
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
					val doc = dir.createFile(mimeType, "$baseName.$extension")
						?: throw IOException("Cannot create destination file")
					if (streamUrl.toUri().isNetworkUri()) {
						// direct streaming download, chunked, no proxy chain, no page cache
						entryPoint.originalImageDownloader().download(streamUrl, page.source, doc.uri)
					} else {
						throw IOException("Only network video URLs can be downloaded")
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
			res.getString(R.string.play_in_vlc),
			res.getString(R.string.download_video),
			res.getString(R.string.open_external),
		)
		currentStreamUrl = streamUrl
		MaterialAlertDialogBuilder(binding.root.context)
			.setTitle(R.string.load_video)
			.setItems(items) { _, which ->
				when (which) {
					0 -> {
						// VLC first; transparently fall back to any external handler
						if (!openExternal(streamUrl, VLC_PACKAGE) && !openExternal(streamUrl)) {
							showErrorSnackbar(Snackbar.LENGTH_SHORT)
						}
					}
					1 -> downloadVideo(page, streamUrl, streamUrl.videoMimeType())
					2 -> if (!openExternal(streamUrl)) {
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

	private fun showLoadingState() {
		binding.panelVideo.isGone = true
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

	private companion object {

		private const val VLC_PACKAGE = "org.videolan.vlc"
		private const val SAVE_BASE_NAME = "booru-video-"
		private const val FALLBACK_EXTENSION = "mp4"

		// all accesses happen on the main thread (same pattern as PageSaveHelper)
		private val nameDateFormat = SimpleDateFormat("yyyy-MM-dd_HHmm")
	}
}
