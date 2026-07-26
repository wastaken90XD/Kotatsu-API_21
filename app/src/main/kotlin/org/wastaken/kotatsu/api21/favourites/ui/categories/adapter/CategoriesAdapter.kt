package org.wastaken.kotatsu.api21.favourites.ui.categories.adapter

import org.wastaken.kotatsu.api21.core.ui.ReorderableListAdapter
import org.wastaken.kotatsu.api21.favourites.ui.categories.FavouriteCategoriesListListener
import org.wastaken.kotatsu.api21.list.ui.adapter.ListItemType
import org.wastaken.kotatsu.api21.list.ui.adapter.ListStateHolderListener
import org.wastaken.kotatsu.api21.list.ui.adapter.emptyStateListAD
import org.wastaken.kotatsu.api21.list.ui.adapter.loadingStateAD
import org.wastaken.kotatsu.api21.list.ui.model.ListModel

class CategoriesAdapter(
	onItemClickListener: FavouriteCategoriesListListener,
	listListener: ListStateHolderListener,
) : ReorderableListAdapter<ListModel>() {

	init {
		addDelegate(ListItemType.CATEGORY_LARGE, categoryAD(onItemClickListener))
		addDelegate(ListItemType.NAV_ITEM, allCategoriesAD(onItemClickListener))
		addDelegate(ListItemType.STATE_EMPTY, emptyStateListAD(listListener))
		addDelegate(ListItemType.STATE_LOADING, loadingStateAD())
	}
}
