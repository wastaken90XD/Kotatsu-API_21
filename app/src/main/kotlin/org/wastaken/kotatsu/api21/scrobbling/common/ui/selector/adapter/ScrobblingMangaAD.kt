package org.wastaken.kotatsu.api21.scrobbling.common.ui.selector.adapter

import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import org.wastaken.kotatsu.api21.R
import org.wastaken.kotatsu.api21.core.ui.list.OnListItemClickListener
import org.wastaken.kotatsu.api21.core.util.ext.textAndVisible
import org.wastaken.kotatsu.api21.databinding.ItemMangaListBinding
import org.wastaken.kotatsu.api21.list.ui.model.ListModel
import org.wastaken.kotatsu.api21.scrobbling.common.domain.model.ScrobblerManga

fun scrobblingMangaAD(
	clickListener: OnListItemClickListener<ScrobblerManga>,
) = adapterDelegateViewBinding<ScrobblerManga, ListModel, ItemMangaListBinding>(
	{ inflater, parent -> ItemMangaListBinding.inflate(inflater, parent, false) },
) {
	itemView.setOnClickListener {
		clickListener.onItemClick(item, it)
	}

	bind {
		binding.textViewTitle.text = item.name
		val endIcon = if (item.isBestMatch) R.drawable.ic_star_small else 0
		binding.textViewTitle.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, endIcon, 0)
		binding.textViewSubtitle.textAndVisible = item.altName
		binding.imageViewCover.setImageAsync(item.cover, null)
	}
}
