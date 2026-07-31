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

	fun buildUrl(url: String, includeCoilSize: Boolean = false, coilW: Int = 0, coilH: Int = 0): HttpUrl {
		val newUrl = HttpUrl.Builder()
			.scheme("https")
			.host("wsrv.nl")
			.addQueryParameter("url", url)

		if (settings.wsrvLossless) {
			newUrl.addQueryParameter("ll", null)
		} else {
			val q = settings.wsrvQuality
			if (q > 0) newUrl.addQueryParameter("q", q.toString())
		}

		val fmt = settings.wsrvFormat
		if (fmt != null && fmt != "original") {
			newUrl.addQueryParameter("output", fmt)
			if (fmt == "png") {
				val pngLevel = settings.wsrvPngLevel
				if (pngLevel > 0) newUrl.addQueryParameter("l", pngLevel.toString())
				if (settings.wsrvPngFilter) newUrl.addQueryParameter("af", null)
			}
		}

		if (settings.wsrvProgressive) {
			newUrl.addQueryParameter("il", null)
		}

		val maxW = settings.wsrvMaxWidth
		if (maxW > 0) newUrl.addQueryParameter("w", maxW.toString())
		
		val maxH = settings.wsrvMaxHeight
		if (maxH > 0) newUrl.addQueryParameter("h", maxH.toString())

		val dpr = settings.wsrvDpr
		if (dpr > 0f) newUrl.addQueryParameter("dpr", dpr.toString())

		if (settings.wsrvNeverUpscale) {
			newUrl.addQueryParameter("we", null)
		}

		val fit = settings.wsrvFit
		if (fit != "cover" && fit.isNotEmpty()) {
			newUrl.addQueryParameter("fit", fit)
		}
		
		val alignment = settings.wsrvAlignment
		if (alignment != "center" && alignment.isNotEmpty()) {
			newUrl.addQueryParameter("a", alignment)
		}

		val sharp = settings.wsrvSharpen
		if (sharp > 0) newUrl.addQueryParameter("sharp", sharp.toString())
		
		val blur = settings.wsrvBlur
		if (blur > 0f) newUrl.addQueryParameter("blur", blur.toString())
		
		val contrast = settings.wsrvContrast
		if (contrast != 0) newUrl.addQueryParameter("con", contrast.toString())
		
		val saturation = settings.wsrvSaturation
		if (saturation != 0) newUrl.addQueryParameter("sat", saturation.toString())
		
		val gamma = settings.wsrvGamma
		if (gamma > 0f) newUrl.addQueryParameter("gam", gamma.toString())
		
		val hue = settings.wsrvHue
		if (hue != 0) newUrl.addQueryParameter("hue", hue.toString())
		
		val brightness = settings.wsrvBrightness
		if (brightness > 0f) newUrl.addQueryParameter("mod", brightness.toString())

		val tint = settings.wsrvTint
		if (tint.isNotBlank()) newUrl.addQueryParameter("tint", tint)

		if (includeCoilSize) {
			if (fit == "cover" || fit.isEmpty()) {
				newUrl.addQueryParameter("crop", "cover")
			}
			if (maxW <= 0 && coilW > 0) newUrl.addQueryParameter("w", coilW.toString())
			if (maxH <= 0 && coilH > 0) newUrl.addQueryParameter("h", coilH.toString())
		}

		return newUrl.build()
	}

	override suspend fun onInterceptImageRequest(request: ImageRequest, url: HttpUrl): ImageRequest {
		var coilW = 0
		var coilH = 0
		val size = request.sizeResolver.size()
		val includeCoilSize = !size.isOriginal
		if (includeCoilSize) {
			coilW = (size.width as? Dimension.Pixels)?.px ?: 0
			coilH = (size.height as? Dimension.Pixels)?.px ?: 0
		}
		return request.newBuilder()
			.data(buildUrl(url.toString(), includeCoilSize, coilW, coilH))
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
