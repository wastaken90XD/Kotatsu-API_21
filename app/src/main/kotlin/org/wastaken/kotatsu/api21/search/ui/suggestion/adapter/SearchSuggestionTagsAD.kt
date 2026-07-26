package org.wastaken.kotatsu.api21.search.ui.suggestion.adapter

import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import org.wastaken.kotatsu.api21.core.ui.widgets.ChipsView
import org.wastaken.kotatsu.api21.databinding.ItemSearchSuggestionTagsBinding
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.wastaken.kotatsu.api21.search.ui.suggestion.SearchSuggestionListener
import org.wastaken.kotatsu.api21.search.ui.suggestion.model.SearchSuggestionItem

fun searchSuggestionTagsAD(
	listener: SearchSuggestionListener,
) = adapterDelegateViewBinding<SearchSuggestionItem.Tags, SearchSuggestionItem, ItemSearchSuggestionTagsBinding>(
	{ layoutInflater, parent -> ItemSearchSuggestionTagsBinding.inflate(layoutInflater, parent, false) },
) {

	binding.chipsGenres.onChipClickListener = ChipsView.OnChipClickListener { _, data ->
		listener.onTagClick(data as? MangaTag ?: return@OnChipClickListener)
	}

	bind {
		binding.chipsGenres.setChips(item.tags)
	}
}
