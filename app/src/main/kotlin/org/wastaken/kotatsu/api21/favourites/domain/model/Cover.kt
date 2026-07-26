package org.wastaken.kotatsu.api21.favourites.domain.model

import org.wastaken.kotatsu.api21.core.model.MangaSource

data class Cover(
	val url: String?,
	val source: String,
) {
	val mangaSource by lazy { MangaSource(source) }
}
