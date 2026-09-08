package org.wastaken.kotatsu.api21.booru.media

import org.koitharu.kotatsu.parsers.model.Manga
import org.wastaken.kotatsu.api21.core.parser.MangaRepository
import org.wastaken.kotatsu.api21.reader.ui.media.looksLikeGif
import org.wastaken.kotatsu.api21.reader.ui.media.looksLikeVideo
import org.wastaken.kotatsu.api21.reader.ui.media.urlFileName

/**
 * One network-level resolution step shared by every navigation entry point:
 * given a booru post (manga), fetch details + first page and build the queue
 * item for the media player. Returns null for static posts and on any failure
 * (callers fall back to the original navigation target).
 */
object BooruMediaResolver {

	suspend fun resolve(factory: MangaRepository.Factory, manga: Manga): BooruMediaItem? {
		val repository = factory.create(manga.source)
		val details = if (manga.chapters.isNullOrEmpty()) repository.getDetails(manga) else manga
		val chapter = details.chapters?.firstOrNull() ?: return null
		val pages = repository.getPages(chapter)
		val page = pages.firstOrNull() ?: return null
		val type = when {
			page.url.looksLikeGif() -> BooruMediaType.GIF
			page.url.looksLikeVideo() -> BooruMediaType.VIDEO
			else -> return null
		}
		return BooruMediaItem(
			id = BooruMediaItem.uidOf(page.url),
			url = page.url,
			source = manga.source,
			title = manga.title.ifEmpty { page.url.urlFileName() },
			thumbnailUrl = manga.publicUrl.ifEmpty { null },
			mediaType = type,
		)
	}
}
