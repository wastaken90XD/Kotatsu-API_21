package org.wastaken.kotatsu.api21.reader.ui.media

import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
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

/**
 * Stream-quality variants for a booru video page URL.
 *
 * Booru page data from the parser model carries only the original file URL (plus
 * the still-image preview), so today only the original stream is known. Some
 * booru engines do serve lower-quality siblings (e.g. 720p/480p samples) — for
 * those, derive the variant URLs from the URL pattern here. The video overlay
 * automatically shows a quality picker as soon as this returns more than one
 * entry; with a single entry the picker step is skipped.
 */
internal fun String.videoStreamVariants(): List<StreamVariant> {
	return listOf(StreamVariant("Original (auto)", this))
}

internal fun ReaderPage.isBooru(): Boolean {
	return (source.unwrap() as? MangaParserSource)?.contentType == ContentType.BOORU
}

internal fun ReaderPage.isBooruGif(): Boolean {
	return isBooru() && url.looksLikeGif()
}

internal fun ReaderPage.isBooruVideo(): Boolean {
	return isBooru() && url.looksLikeVideo()
}

internal fun ReaderPage.isBooruMedia(): Boolean {
	return isBooruGif() || isBooruVideo()
}
