package org.wastaken.kotatsu.api21.search.ui.suggestion.adapter

import android.view.View
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import org.wastaken.kotatsu.api21.databinding.ItemSearchSuggestionQueryHintBinding
import org.wastaken.kotatsu.api21.search.domain.SearchKind
import org.wastaken.kotatsu.api21.search.ui.suggestion.SearchSuggestionListener
import org.wastaken.kotatsu.api21.search.ui.suggestion.model.SearchSuggestionItem

fun searchSuggestionQueryHintAD(
	listener: SearchSuggestionListener,
) = adapterDelegateViewBinding<SearchSuggestionItem.Hint, SearchSuggestionItem, ItemSearchSuggestionQueryHintBinding>(
	{ inflater, parent -> ItemSearchSuggestionQueryHintBinding.inflate(inflater, parent, false) },
) {

	val viewClickListener = View.OnClickListener { _ ->
		listener.onQueryClick(item.query, SearchKind.SIMPLE, true)
	}

	binding.root.setOnClickListener(viewClickListener)

	bind {
		binding.root.text = item.query
	}
}
