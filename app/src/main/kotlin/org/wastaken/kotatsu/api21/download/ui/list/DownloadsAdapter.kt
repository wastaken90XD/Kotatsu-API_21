package org.wastaken.kotatsu.api21.download.ui.list

import androidx.lifecycle.LifecycleOwner
import org.wastaken.kotatsu.api21.core.ui.BaseListAdapter
import org.wastaken.kotatsu.api21.list.ui.adapter.ListItemType
import org.wastaken.kotatsu.api21.list.ui.adapter.emptyStateListAD
import org.wastaken.kotatsu.api21.list.ui.adapter.listHeaderAD
import org.wastaken.kotatsu.api21.list.ui.adapter.loadingStateAD
import org.wastaken.kotatsu.api21.list.ui.model.ListModel

class DownloadsAdapter(
	lifecycleOwner: LifecycleOwner,
	listener: DownloadItemListener,
) : BaseListAdapter<ListModel>() {

	init {
		addDelegate(ListItemType.DOWNLOAD, downloadItemAD(lifecycleOwner, listener))
		addDelegate(ListItemType.STATE_LOADING, loadingStateAD())
		addDelegate(ListItemType.STATE_EMPTY, emptyStateListAD(null))
		addDelegate(ListItemType.HEADER, listHeaderAD(null))
	}
}
