package org.wastaken.kotatsu.api21.list.ui.adapter

import android.view.View
import org.wastaken.kotatsu.api21.core.ui.widgets.TipView

interface MangaListListener : MangaDetailsClickListener, ListStateHolderListener, ListHeaderClickListener,
	TipView.OnButtonClickListener, QuickFilterClickListener {

	fun onFilterClick(view: View?)
}
