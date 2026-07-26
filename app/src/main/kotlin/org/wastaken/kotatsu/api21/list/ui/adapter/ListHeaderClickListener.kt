package org.wastaken.kotatsu.api21.list.ui.adapter

import android.view.View
import org.wastaken.kotatsu.api21.list.ui.model.ListHeader

interface ListHeaderClickListener {

	fun onListHeaderClick(item: ListHeader, view: View)
}
