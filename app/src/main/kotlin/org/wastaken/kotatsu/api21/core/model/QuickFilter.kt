package org.wastaken.kotatsu.api21.core.model

import org.wastaken.kotatsu.api21.core.ui.widgets.ChipsView
import org.wastaken.kotatsu.api21.list.domain.ListFilterOption

fun ListFilterOption.toChipModel(isChecked: Boolean) = ChipsView.ChipModel(
	title = titleText,
	titleResId = titleResId,
	icon = iconResId,
	iconData = getIconData(),
	isChecked = isChecked,
	counter = if (this is ListFilterOption.Branch) chaptersCount else 0,
	data = this,
)
