package org.wastaken.kotatsu.api21.settings.nav.model

import androidx.annotation.StringRes
import org.wastaken.kotatsu.api21.core.prefs.NavItem
import org.wastaken.kotatsu.api21.list.ui.model.ListModel

data class NavItemConfigModel(
	val item: NavItem,
	@StringRes val disabledHintResId: Int,
) : ListModel {

	override fun areItemsTheSame(other: ListModel): Boolean {
		return other is NavItemConfigModel && other.item == item
	}
}
