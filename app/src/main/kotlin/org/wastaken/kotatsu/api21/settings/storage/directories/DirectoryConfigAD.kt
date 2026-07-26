package org.wastaken.kotatsu.api21.settings.storage.directories

import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import org.wastaken.kotatsu.api21.R
import org.wastaken.kotatsu.api21.core.ui.list.OnListItemClickListener
import org.wastaken.kotatsu.api21.core.util.ext.drawableStart
import org.wastaken.kotatsu.api21.core.util.ext.setTooltipCompat
import org.wastaken.kotatsu.api21.core.util.ext.textAndVisible
import org.wastaken.kotatsu.api21.databinding.ItemStorageConfigBinding
import org.wastaken.kotatsu.api21.settings.storage.DirectoryModel

fun directoryConfigAD(
	clickListener: OnListItemClickListener<DirectoryModel>,
) = adapterDelegateViewBinding<DirectoryModel, DirectoryModel, ItemStorageConfigBinding>(
	{ layoutInflater, parent -> ItemStorageConfigBinding.inflate(layoutInflater, parent, false) },
) {

	binding.buttonRemove.setOnClickListener { v -> clickListener.onItemClick(item, v) }
	binding.buttonRemove.setTooltipCompat(binding.buttonRemove.contentDescription)

	bind {
		binding.textViewTitle.text = item.title ?: getString(item.titleRes)
		binding.textViewSubtitle.textAndVisible = item.file?.absolutePath
		binding.buttonRemove.isVisible = item.isRemovable
		binding.buttonRemove.isEnabled = !item.isChecked
		binding.textViewTitle.drawableStart = if (!item.isAvailable) {
			ContextCompat.getDrawable(context, R.drawable.ic_alert_outline)?.apply {
				setTint(ContextCompat.getColor(context, R.color.warning))
			}
		} else if (item.isChecked) {
			ContextCompat.getDrawable(context, R.drawable.ic_download)
		} else {
			null
		}
	}
}
