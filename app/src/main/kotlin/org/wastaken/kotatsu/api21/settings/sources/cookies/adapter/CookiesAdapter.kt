package org.wastaken.kotatsu.api21.settings.sources.cookies.adapter

import org.wastaken.kotatsu.api21.core.ui.BaseListAdapter
import org.wastaken.kotatsu.api21.list.ui.adapter.ListItemType
import org.wastaken.kotatsu.api21.list.ui.adapter.emptyStateListAD
import org.wastaken.kotatsu.api21.list.ui.model.ListModel
import org.wastaken.kotatsu.api21.settings.sources.cookies.CookiesListListener

class CookiesAdapter(
	listener: CookiesListListener,
) : BaseListAdapter<ListModel>() {

	init {
		addDelegate(ListItemType.COOKIE, cookieAD(listener))
		addDelegate(ListItemType.STATE_EMPTY, emptyStateListAD(null))
	}
}
