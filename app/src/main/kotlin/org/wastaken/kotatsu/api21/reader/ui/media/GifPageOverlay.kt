package org.wastaken.kotatsu.api21.reader.ui.media

import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.lifecycle
import coil3.util.CoilUtils
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.wastaken.kotatsu.api21.core.util.ext.getDisplayMessage
import org.wastaken.kotatsu.api21.databinding.LayoutBooruGifOverlayBinding
import org.wastaken.kotatsu.api21.reader.domain.PageLoader
import org.wastaken.kotatsu.api21.reader.ui.pager.ReaderPage

/**
 * Explicit-load GIF support for booru reader pages.
 *
 * GIFs must never load or animate automatically: when a booru page URL ends in
 * .gif this overlay shows a static placeholder with a "Load GIF" button and the
 * normal page pipeline is suppressed (no prefetch, no page download). The GIF is
 * fetched (through the regular page loader, so source headers / proxy / fallback
 * apply) and animated inline only after an explicit tap. State is per-binding:
 * navigating away and back resets to the static placeholder by design — decoded
 * GIF frames are large and must not be kept around on low-RAM devices.
 * Memory-cache for GIF requests is disabled for the same reason.
 */
class GifPageOverlay(
	private val binding: LayoutBooruGifOverlayBinding,
	private val loader: PageLoader,
	private val lifecycleOwner: LifecycleOwner,
) {

	var isHandling = false
		private set

	private val coil by lazy(LazyThreadSafetyMode.NONE) {
		EntryPointAccessors.fromApplication(
			binding.root.context.applicationContext,
			ReaderMediaEntryPoint::class.java,
		).imageLoader()
	}

	private var loadJob: Job? = null

	/** @return true if this overlay handles the page and the normal page load must be suppressed. */
	fun onBind(page: ReaderPage): Boolean {
		reset()
		isHandling = page.isBooruGif()
		if (!isHandling) {
			return false
		}
		binding.root.isVisible = true
		binding.panelGif.isVisible = true
		binding.progressGif.isGone = true
		binding.buttonLoadGif.isVisible = true
		binding.gifImageView.isGone = true
		binding.buttonLoadGif.setOnClickListener { load(page) }
		return true
	}

	fun onRecycled() {
		reset()
	}

	private fun reset() {
		loadJob?.cancel()
		loadJob = null
		CoilUtils.dispose(binding.gifImageView)
		// drop the MovieDrawable and its decoded frames immediately
		binding.gifImageView.setImageDrawable(null)
		binding.root.isGone = true
	}

	private fun load(page: ReaderPage) {
		if (loadJob?.isActive == true) {
			return
		}
		loadJob = lifecycleOwner.lifecycleScope.launch {
			binding.buttonLoadGif.isGone = true
			binding.progressGif.isVisible = true
			try {
				val uri = loader.loadPage(page.toMangaPage(), force = false)
				val request = ImageRequest.Builder(binding.root.context)
					.data(uri)
					.lifecycle(lifecycleOwner.lifecycle)
					.memoryCachePolicy(CachePolicy.DISABLED)
					.target(binding.gifImageView)
					.build()
				when (val result = coil.execute(request)) {
					is SuccessResult -> {
						binding.panelGif.isGone = true
						binding.gifImageView.isVisible = true
					}

					is ErrorResult -> onError(result.throwable)
				}
			} catch (e: CancellationException) {
				throw e
			} catch (e: Throwable) {
				onError(e)
			}
		}
	}

	private fun onError(e: Throwable) {
		binding.progressGif.isGone = true
		binding.buttonLoadGif.isVisible = true
		Snackbar.make(
			binding.root,
			e.getDisplayMessage(binding.root.resources),
			Snackbar.LENGTH_SHORT,
		).show()
	}
}
