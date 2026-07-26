package org.wastaken.kotatsu.api21.tracker.ui.feed.model

import org.wastaken.kotatsu.api21.list.ui.ListModelDiffCallback
import org.wastaken.kotatsu.api21.list.ui.model.ListModel
import org.wastaken.kotatsu.api21.list.ui.model.MangaListModel

data class UpdatedMangaHeader(
	val list: List<MangaListModel>,
) : ListModel {

	override fun areItemsTheSame(other: ListModel): Boolean {
		return other is UpdatedMangaHeader
	}

	override fun getChangePayload(previousState: ListModel): Any {
		return ListModelDiffCallback.PAYLOAD_NESTED_LIST_CHANGED
	}
}
