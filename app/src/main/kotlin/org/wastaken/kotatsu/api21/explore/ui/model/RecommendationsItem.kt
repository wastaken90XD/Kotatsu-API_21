package org.wastaken.kotatsu.api21.explore.ui.model

import org.wastaken.kotatsu.api21.list.ui.model.ListModel
import org.wastaken.kotatsu.api21.list.ui.model.MangaCompactListModel

data class RecommendationsItem(
	val manga: List<MangaCompactListModel>
) : ListModel {

	override fun areItemsTheSame(other: ListModel): Boolean {
		return other is RecommendationsItem
	}
}
