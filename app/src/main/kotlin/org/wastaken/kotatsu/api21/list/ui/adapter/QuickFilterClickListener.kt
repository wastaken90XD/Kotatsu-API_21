package org.wastaken.kotatsu.api21.list.ui.adapter

import org.wastaken.kotatsu.api21.list.domain.ListFilterOption

interface QuickFilterClickListener {

	fun onFilterOptionClick(option: ListFilterOption)
}
