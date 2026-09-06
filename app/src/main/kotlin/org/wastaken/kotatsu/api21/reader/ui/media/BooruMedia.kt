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
