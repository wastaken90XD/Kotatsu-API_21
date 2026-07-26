package org.wastaken.kotatsu.api21.settings.nav.model

import org.wastaken.kotatsu.api21.list.ui.model.ListModel

data class NavItemAddModel(
	val canAdd: Boolean,
) : ListModel {

	override fun areItemsTheSame(other: ListModel): Boolean = other is NavItemAddModel
}
