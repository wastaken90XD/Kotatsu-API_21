package org.wastaken.kotatsu.api21.reader.ui.media

import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.wastaken.kotatsu.api21.core.model.unwrap
import org.wastaken.kotatsu.api21.reader.ui.pager.ReaderPage

/**
 * Shared media-URL detection for the booru explicit-load features
 * (GIF overlay, video player). Pages from booru sources may point to .gif
 * animations or video files instead of still images.
 */

private val VIDEO_EXTENSIONS = setOf("mp4", "webm", "gifv")

internal fun String.urlExtension(): String {
	return substringBefore('#').substringBefore('?').substringAfterLast('.', "")
}

internal fun String.looksLikeGif(): Boolean {
	return urlExtension().equals("gif", ignoreCase = true)
}

internal fun String.looksLikeVideo(): Boolean {
	return urlExtension().lowercase() in VIDEO_EXTENSIONS
}

internal fun String.looksLikeMedia(): Boolean {
	return looksLikeGif() || looksLikeVideo()
}

/** Best-effort mime type for a video page URL, used for ACTION_VIEW intents. */
internal fun String.videoMimeType(): String {
	return when (urlExtension().lowercase()) {
		"webm" -> "video/webm"
		else -> "video/mp4" // mp4, gifv and unknown extensions
	}
}

/** A playable stream variant (quality level) for a booru video page. */
internal data class StreamVariant(
	val label: String,
	val url: String,
)

/** Display name of the file a page URL points at ("abc123.mp4"), without query/fragment. */
internal fun String.urlFileName(): String {
	return substringBefore('#').substringBefore('?').substringAfterLast('/')
}

/**
 * Stream-quality variants for a booru video page URL.
 *
 * The parser model only exposes the original file URL, but several booru CDN
 * patterns are deterministic enough to derive the quality siblings the source
 * itself publishes; those providers live below. The video overlay automatically
 * shows a quality picker as soon as more than one variant is returned.
 */
internal fun String.videoStreamVariants(): List<StreamVariant> {
	danbooruVideoVariants()?.let { return it }
	return listOf(StreamVariant("Original (auto)", this))
}

private const val DANBOORU_CDN_HOST = "cdn.donmai.us"
private const val DANBOORU_ORIGINAL_SEGMENT = "/original/"
private val DANBOORU_VIDEO_VARIANT_TAGS = listOf("720p", "480p", "360p")

/**
 * Danbooru media assets are published as
 *   https://cdn.donmai.us/original/{h1}/{h2}/{file}.{ext}
 * with server-generated video variants (always H.264 mp4) at
 *   https://cdn.donmai.us/{720p|480p|360p}/{h1}/{h2}/{file}.mp4
 */
private fun String.danbooruVideoVariants(): List<StreamVariant>? {
	val clean = substringBefore('#').substringBefore('?')
	val schemeSep = clean.indexOf("://").takeIf { it > 0 } ?: return null
	val hostEnd = clean.indexOf('/', startIndex = schemeSep + 3).takeIf { it > 0 } ?: return null
	val host = clean.substring(schemeSep + 3, hostEnd)
	if (!host.equals(DANBOORU_CDN_HOST, ignoreCase = true)) {
		return null
	}
	val path = clean.substring(hostEnd) // "/original/xx/yy/file.mp4"
	if (!path.startsWith(DANBOORU_ORIGINAL_SEGMENT) || !looksLikeVideo()) {
		return null
	}
	val baseOrigin = clean.substring(0, hostEnd)
	val suffix = path.substring(DANBOORU_ORIGINAL_SEGMENT.length) // "xx/yy/file.mp4"
	val baseName = suffix.substringBeforeLast('.')
	return buildList {
		add(StreamVariant("Original", clean))
		for (tag in DANBOORU_VIDEO_VARIANT_TAGS) {
			add(StreamVariant(tag, "$baseOrigin/$tag/$baseName.mp4"))
		}
	}
}

/** True only for booru parser sources; every reader-media gate originates here. */
internal fun MangaSource.isBooruSource(): Boolean {
	return (unwrap() as? MangaParserSource)?.contentType == ContentType.BOORU
}

internal fun ReaderPage.isBooru(): Boolean {
	// source check ALWAYS comes first: extensions alone must never be trusted,
	// otherwise ordinary comic pages with gif/video in the URL would be gated
	return source.isBooruSource()
}

internal fun ReaderPage.isBooruMedia(): Boolean {
	return isBooru() && url.looksLikeMedia()
}

/** Single gate for media detection: booru source check first, URL check second. */
internal fun MangaPage.isBooruMedia(): Boolean {
	return source.isBooruSource() && url.looksLikeMedia()
}
