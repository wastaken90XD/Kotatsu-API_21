package org.wastaken.kotatsu.api21.reader.ui

import org.wastaken.kotatsu.api21.bookmarks.domain.Bookmark
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.wastaken.kotatsu.api21.reader.ui.pager.ReaderPage

interface ReaderNavigationCallback {

	fun onPageSelected(page: ReaderPage): Boolean

	fun onChapterSelected(chapter: MangaChapter): Boolean

	fun onBookmarkSelected(bookmark: Bookmark): Boolean = onPageSelected(
		ReaderPage(bookmark.toMangaPage(), bookmark.page, bookmark.chapterId),
	)
}
