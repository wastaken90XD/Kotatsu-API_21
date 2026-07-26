package org.wastaken.kotatsu.api21.details.ui.scrobbling

import org.wastaken.kotatsu.api21.core.nav.AppRouter
import org.wastaken.kotatsu.api21.core.ui.BaseListAdapter
import org.wastaken.kotatsu.api21.list.ui.model.ListModel

class ScrollingInfoAdapter(
	router: AppRouter,
) : BaseListAdapter<ListModel>() {

	init {
		delegatesManager.addDelegate(scrobblingInfoAD(router))
	}
}
