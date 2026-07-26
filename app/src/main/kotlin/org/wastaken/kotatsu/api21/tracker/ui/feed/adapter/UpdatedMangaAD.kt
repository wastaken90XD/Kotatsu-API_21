package org.wastaken.kotatsu.api21.tracker.ui.feed.adapter

import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import org.wastaken.kotatsu.api21.R
import org.wastaken.kotatsu.api21.core.ui.BaseListAdapter
import org.wastaken.kotatsu.api21.core.ui.list.OnListItemClickListener
import org.wastaken.kotatsu.api21.databinding.ItemListGroupBinding
import org.wastaken.kotatsu.api21.list.ui.adapter.ListHeaderClickListener
import org.wastaken.kotatsu.api21.list.ui.adapter.ListItemType
import org.wastaken.kotatsu.api21.list.ui.adapter.mangaGridItemAD
import org.wastaken.kotatsu.api21.list.ui.model.ListHeader
import org.wastaken.kotatsu.api21.list.ui.model.ListModel
import org.wastaken.kotatsu.api21.list.ui.model.MangaListModel
import org.wastaken.kotatsu.api21.list.ui.size.ItemSizeResolver
import org.wastaken.kotatsu.api21.tracker.ui.feed.model.UpdatedMangaHeader

fun updatedMangaAD(
	sizeResolver: ItemSizeResolver,
	listener: OnListItemClickListener<MangaListModel>,
	headerClickListener: ListHeaderClickListener,
) = adapterDelegateViewBinding<UpdatedMangaHeader, ListModel, ItemListGroupBinding>(
	{ layoutInflater, parent -> ItemListGroupBinding.inflate(layoutInflater, parent, false) },
) {

	val adapter = BaseListAdapter<ListModel>()
		.addDelegate(ListItemType.MANGA_GRID, mangaGridItemAD(sizeResolver, listener))
	binding.recyclerView.adapter = adapter
	binding.buttonMore.setOnClickListener { v ->
		headerClickListener.onListHeaderClick(ListHeader(0, payload = item), v)
	}
	binding.textViewTitle.setText(R.string.updates)
	binding.buttonMore.setText(R.string.more)

	bind {
		adapter.items = item.list
	}
}
