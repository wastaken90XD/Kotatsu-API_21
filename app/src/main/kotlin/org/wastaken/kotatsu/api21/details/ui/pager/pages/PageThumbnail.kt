package org.wastaken.kotatsu.api21.details.ui.pager.pages

import org.wastaken.kotatsu.api21.list.ui.model.ListModel
import org.wastaken.kotatsu.api21.reader.ui.pager.ReaderPage

data class PageThumbnail(
	val isCurrent: Boolean,
	val page: ReaderPage,
) : ListModel {

	val number
		get() = page.index + 1

	override fun areItemsTheSame(other: ListModel): Boolean {
		return other is PageThumbnail && page == other.page
	}
}
