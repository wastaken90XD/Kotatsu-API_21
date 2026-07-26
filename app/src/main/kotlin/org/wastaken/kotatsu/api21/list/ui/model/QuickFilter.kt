package org.wastaken.kotatsu.api21.list.ui.model

import org.wastaken.kotatsu.api21.core.ui.widgets.ChipsView
import org.wastaken.kotatsu.api21.list.ui.ListModelDiffCallback

data class QuickFilter(
	val items: List<ChipsView.ChipModel>,
) : ListModel {

	override fun areItemsTheSame(other: ListModel): Boolean = other is QuickFilter

	override fun getChangePayload(previousState: ListModel) = ListModelDiffCallback.PAYLOAD_NESTED_LIST_CHANGED
}
