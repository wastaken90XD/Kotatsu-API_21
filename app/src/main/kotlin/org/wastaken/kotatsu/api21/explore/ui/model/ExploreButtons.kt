package org.wastaken.kotatsu.api21.explore.ui.model

import org.wastaken.kotatsu.api21.list.ui.model.ListModel

data class ExploreButtons(
	val isRandomLoading: Boolean,
) : ListModel {

	override fun areItemsTheSame(other: ListModel): Boolean {
		return other is ExploreButtons
	}
}
