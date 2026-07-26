package org.wastaken.kotatsu.api21.settings.sources.adapter

import org.wastaken.kotatsu.api21.core.ui.ReorderableListAdapter
import org.wastaken.kotatsu.api21.settings.sources.model.SourceConfigItem

class SourceConfigAdapter(
	listener: SourceConfigListener,
) : ReorderableListAdapter<SourceConfigItem>() {

	init {
		with(delegatesManager) {
			addDelegate(sourceConfigItemDelegate2(listener))
			addDelegate(sourceConfigEmptySearchDelegate())
			addDelegate(sourceConfigTipDelegate(listener))
		}
	}
}
