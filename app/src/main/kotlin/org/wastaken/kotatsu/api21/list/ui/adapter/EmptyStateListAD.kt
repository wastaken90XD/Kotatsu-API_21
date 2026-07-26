package org.wastaken.kotatsu.api21.list.ui.adapter

import androidx.core.view.isVisible
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import org.wastaken.kotatsu.api21.core.util.ext.setTextAndVisible
import org.wastaken.kotatsu.api21.databinding.ItemEmptyStateBinding
import org.wastaken.kotatsu.api21.list.ui.model.EmptyState
import org.wastaken.kotatsu.api21.list.ui.model.ListModel

fun emptyStateListAD(
	listener: ListStateHolderListener?,
) = adapterDelegateViewBinding<EmptyState, ListModel, ItemEmptyStateBinding>(
	{ inflater, parent -> ItemEmptyStateBinding.inflate(inflater, parent, false) },
) {

	if (listener != null) {
		binding.buttonRetry.setOnClickListener { listener.onEmptyActionClick() }
	}

	bind {
		if (item.icon == 0) {
			binding.icon.isVisible = false
			binding.icon.disposeImage()
		} else {
			binding.icon.isVisible = true
			binding.icon.setImageAsync(item.icon)
		}
		binding.textPrimary.setText(item.textPrimary)
		binding.textSecondary.setTextAndVisible(item.textSecondary)
		if (listener != null) {
			binding.buttonRetry.setTextAndVisible(item.actionStringRes)
		}
	}
}
