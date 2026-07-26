package org.wastaken.kotatsu.api21.history.ui

import android.content.Context
import org.wastaken.kotatsu.api21.core.ui.list.fastscroll.FastScroller
import org.wastaken.kotatsu.api21.list.ui.adapter.MangaListAdapter
import org.wastaken.kotatsu.api21.list.ui.adapter.MangaListListener
import org.wastaken.kotatsu.api21.list.ui.size.ItemSizeResolver

class HistoryListAdapter(
	listener: MangaListListener,
	sizeResolver: ItemSizeResolver,
) : MangaListAdapter(listener, sizeResolver), FastScroller.SectionIndexer {

	override fun getSectionText(context: Context, position: Int): CharSequence? {
		return findHeader(position)?.getText(context)
	}
}
