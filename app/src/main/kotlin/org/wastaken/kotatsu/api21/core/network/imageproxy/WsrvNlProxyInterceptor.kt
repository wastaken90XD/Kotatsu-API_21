package org.wastaken.kotatsu.api21.core.network.imageproxy

import coil3.request.ImageRequest
import coil3.size.Dimension
import coil3.size.isOriginal
import okhttp3.HttpUrl
import okhttp3.Request
import org.wastaken.kotatsu.api21.core.prefs.AppSettings
import javax.inject.Inject

class WsrvNlProxyInterceptor @Inject constructor(
	private val settings: AppSettings,
) : BaseImageProxyInterceptor() {

	override suspend fun onInterceptImageRequest(request: ImageRequest, url: HttpUrl): ImageRequest {
		val newUrl = HttpUrl.Builder()
			.scheme("https")
			.host("wsrv.nl")
			.addQueryParameter("url", url.toString())

		if (settings.wsrvLossless) {
			newUrl.addQueryParameter("ll", null)
		} else {
			val q = settings.wsrvQuality
			if (q > 0) newUrl.addQueryParameter("q", q.toString())
		}

		val fmt = settings.wsrvFormat
		if (fmt != null && fmt != "original") {
			newUrl.addQueryParameter("output", fmt)
		}

		val maxW = settings.wsrvMaxWidth
		if (maxW > 0) {
			newUrl.addQueryParameter("w", maxW.toString())
		}

		if (settings.wsrvNeverUpscale) {
			newUrl.addQueryParameter("we", null)
		}

		val sharp = settings.wsrvSharpen
		if (sharp > 0) {
			newUrl.addQueryParameter("sharp", sharp.toString())
		}

		val size = request.sizeResolver.size()
		if (!size.isOriginal) {
			newUrl.addQueryParameter("crop", "cover")
			if (maxW <= 0) {
				(size.height as? Dimension.Pixels)?.let { newUrl.addQueryParameter("h", it.toString()) }
				(size.width as? Dimension.Pixels)?.let { newUrl.addQueryParameter("w", it.toString()) }
			}
		}

		return request.newBuilder()
			.data(newUrl.build())
			.build()
	}

	override suspend fun onInterceptPageRequest(request: Request): Request {
		val sourceUrl = request.url
		val targetUrl = HttpUrl.Builder()
			.scheme("https")
			.host("wsrv.nl")
			.addQueryParameter("url", sourceUrl.toString())
			.addQueryParameter("we", null)
		return request.newBuilder()
			.url(targetUrl.build())
			.build()
	}
}
