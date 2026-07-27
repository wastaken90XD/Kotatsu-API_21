package org.wastaken.kotatsu.api21.settings.sources.cookies.adapter

import android.text.format.DateUtils
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import org.wastaken.kotatsu.api21.R
import org.wastaken.kotatsu.api21.databinding.ItemCookieBinding
import org.wastaken.kotatsu.api21.list.ui.model.ListModel
import org.wastaken.kotatsu.api21.settings.sources.cookies.CookiesListListener
import org.wastaken.kotatsu.api21.settings.sources.cookies.model.CookieListModel

fun cookieAD(
	listener: CookiesListListener,
) = adapterDelegateViewBinding<CookieListModel, ListModel, ItemCookieBinding>(
	{ inflater, parent -> ItemCookieBinding.inflate(inflater, parent, false) },
) {

	itemView.setOnClickListener { listener.onCookieClick(item) }
	binding.buttonDelete.setOnClickListener { listener.onCookieDeleteClick(item) }

	bind {
		binding.textViewName.text = item.name
		binding.textViewValue.text = item.value
		binding.textViewSubtitle.text = buildString {
			append(item.domain)
			append(item.path)
			append(" \u2022 ")
			append(
				if (item.isPersistent) {
					DateUtils.getRelativeTimeSpanString(item.expiresAt).toString()
				} else {
					getString(R.string.session_only)
				},
			)
		}
	}
}
