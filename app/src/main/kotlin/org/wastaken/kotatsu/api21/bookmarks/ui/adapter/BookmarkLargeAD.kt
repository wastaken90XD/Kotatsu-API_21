package org.wastaken.kotatsu.api21.bookmarks.ui.adapter

import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import org.wastaken.kotatsu.api21.bookmarks.domain.Bookmark
import org.wastaken.kotatsu.api21.core.ui.list.AdapterDelegateClickListenerAdapter
import org.wastaken.kotatsu.api21.core.ui.list.OnListItemClickListener
import org.wastaken.kotatsu.api21.databinding.ItemBookmarkLargeBinding
import org.wastaken.kotatsu.api21.list.ui.model.ListModel

fun bookmarkLargeAD(
	clickListener: OnListItemClickListener<Bookmark>,
) = adapterDelegateViewBinding<Bookmark, ListModel, ItemBookmarkLargeBinding>(
	{ inflater, parent -> ItemBookmarkLargeBinding.inflate(inflater, parent, false) },
) {
	AdapterDelegateClickListenerAdapter(this, clickListener).attach(itemView)

	bind {
		binding.imageViewThumb.setImageAsync(item)
		binding.progressView.setProgress(item.percent, false)
	}
}
