package org.wastaken.kotatsu.api21.explore.ui.model

import org.wastaken.kotatsu.api21.core.model.MangaSourceInfo
import org.wastaken.kotatsu.api21.list.ui.model.ListModel
import org.koitharu.kotatsu.parsers.util.longHashCode

data class MangaSourceItem(
	val source: MangaSourceInfo,
	val isGrid: Boolean,
) : ListModel {

	val id: Long = source.name.longHashCode()

	override fun areItemsTheSame(other: ListModel): Boolean {
		return other is MangaSourceItem && other.source == source
	}
}
