package org.wastaken.kotatsu.api21.favourites.data

import org.wastaken.kotatsu.api21.core.db.entity.toManga
import org.wastaken.kotatsu.api21.core.db.entity.toMangaTags
import org.wastaken.kotatsu.api21.core.model.FavouriteCategory
import org.wastaken.kotatsu.api21.list.domain.ListSortOrder
import java.time.Instant

fun FavouriteCategoryEntity.toFavouriteCategory(id: Long = categoryId.toLong()) = FavouriteCategory(
	id = id,
	title = title,
	sortKey = sortKey,
	order = ListSortOrder(order, ListSortOrder.NEWEST),
	createdAt = Instant.ofEpochMilli(createdAt),
	isTrackingEnabled = track,
	isVisibleInLibrary = isVisibleInLibrary,
)

fun FavouriteManga.toManga() = manga.toManga(tags.toMangaTags(), null)

fun Collection<FavouriteManga>.toMangaList() = map { it.toManga() }
