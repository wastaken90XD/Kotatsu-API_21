package org.wastaken.kotatsu.api21.list.ui.adapter

import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegate
import org.wastaken.kotatsu.api21.R
import org.wastaken.kotatsu.api21.list.ui.model.ListModel
import org.wastaken.kotatsu.api21.list.ui.model.LoadingFooter

fun loadingFooterAD() = adapterDelegate<LoadingFooter, ListModel>(R.layout.item_loading_footer) {
}