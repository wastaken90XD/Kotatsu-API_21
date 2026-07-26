package org.wastaken.kotatsu.api21.history.data

import dagger.Reusable
import org.wastaken.kotatsu.api21.core.db.MangaDatabase
import org.wastaken.kotatsu.api21.core.db.entity.toManga
import org.wastaken.kotatsu.api21.core.db.entity.toMangaTags
import org.wastaken.kotatsu.api21.history.domain.model.MangaWithHistory
import org.wastaken.kotatsu.api21.list.domain.ListFilterOption
import org.wastaken.kotatsu.api21.list.domain.ListSortOrder
import org.wastaken.kotatsu.api21.local.data.index.LocalMangaIndex
import org.wastaken.kotatsu.api21.local.domain.LocalObserveMapper
import org.koitharu.kotatsu.parsers.model.Manga
import javax.inject.Inject

@Reusable
class HistoryLocalObserver @Inject constructor(
	localMangaIndex: LocalMangaIndex,
	private val db: MangaDatabase,
) : LocalObserveMapper<HistoryWithManga, MangaWithHistory>(localMangaIndex) {

	fun observeAll(
		order: ListSortOrder,
		filterOptions: Set<ListFilterOption>,
		limit: Int
	) = db.getHistoryDao().observeAll(order, filterOptions, limit).mapToLocal()

	override fun toManga(e: HistoryWithManga) = e.manga.toManga(e.tags.toMangaTags(), null)

	override fun toResult(e: HistoryWithManga, manga: Manga) = MangaWithHistory(
		manga = manga,
		history = e.history.toMangaHistory(),
	)
}
