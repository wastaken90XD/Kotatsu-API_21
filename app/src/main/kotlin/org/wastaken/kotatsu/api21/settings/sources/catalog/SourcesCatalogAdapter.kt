package org.wastaken.kotatsu.api21.settings.sources.catalog

import android.content.Context
import org.wastaken.kotatsu.api21.core.model.getTitle
import org.wastaken.kotatsu.api21.core.ui.BaseListAdapter
import org.wastaken.kotatsu.api21.core.ui.list.OnListItemClickListener
import org.wastaken.kotatsu.api21.core.ui.list.fastscroll.FastScroller
import org.wastaken.kotatsu.api21.list.ui.adapter.ListItemType
import org.wastaken.kotatsu.api21.list.ui.adapter.loadingStateAD
import org.wastaken.kotatsu.api21.list.ui.model.ListModel

class SourcesCatalogAdapter(
	listener: OnListItemClickListener<SourceCatalogItem.Source>,
) : BaseListAdapter<ListModel>(), FastScroller.SectionIndexer {

	init {
		addDelegate(ListItemType.CHAPTER_LIST, sourceCatalogItemSourceAD(listener))
		addDelegate(ListItemType.HINT_EMPTY, sourceCatalogItemHintAD())
		addDelegate(ListItemType.STATE_LOADING, loadingStateAD())
	}

	override fun getSectionText(context: Context, position: Int): CharSequence? {
		return (items.getOrNull(position) as? SourceCatalogItem.Source)?.source?.getTitle(context)?.take(1)
	}
}
