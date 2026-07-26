package org.wastaken.kotatsu.api21.favourites.ui.categories.select.adapter

import org.wastaken.kotatsu.api21.core.ui.BaseListAdapter
import org.wastaken.kotatsu.api21.core.ui.list.OnListItemClickListener
import org.wastaken.kotatsu.api21.favourites.ui.categories.select.model.MangaCategoryItem
import org.wastaken.kotatsu.api21.list.ui.adapter.ListItemType
import org.wastaken.kotatsu.api21.list.ui.adapter.emptyStateListAD
import org.wastaken.kotatsu.api21.list.ui.adapter.loadingStateAD
import org.wastaken.kotatsu.api21.list.ui.model.ListModel

class MangaCategoriesAdapter(
	clickListener: OnListItemClickListener<MangaCategoryItem>,
) : BaseListAdapter<ListModel>() {

	init {
		addDelegate(ListItemType.NAV_ITEM, mangaCategoryAD(clickListener))
		addDelegate(ListItemType.STATE_LOADING, loadingStateAD())
		addDelegate(ListItemType.STATE_EMPTY, emptyStateListAD(null))
	}
}
