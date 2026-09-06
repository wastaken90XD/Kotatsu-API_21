package org.wastaken.kotatsu.api21.list.ui.adapter

import android.graphics.drawable.ColorDrawable
import coil3.size.Size
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import org.wastaken.kotatsu.api21.core.ui.list.AdapterDelegateClickListenerAdapter
import org.wastaken.kotatsu.api21.core.ui.list.OnListItemClickListener
import org.wastaken.kotatsu.api21.core.util.ext.getThemeColor
import org.wastaken.kotatsu.api21.core.util.ext.setTooltipCompat
import org.wastaken.kotatsu.api21.databinding.ItemBooruGridBinding
import org.wastaken.kotatsu.api21.list.ui.model.BooruGridModel
import org.wastaken.kotatsu.api21.list.ui.model.ListModel
import org.wastaken.kotatsu.api21.list.ui.model.MangaListModel
import com.google.android.material.R as materialR

/**
 * Tile delegate for the booru square grid (see BooruGridAdapter).
 * - [imageSize] is a fixed pixel decode size: Coil never decodes full-resolution
 *   thumbnails into memory (critical on low-RAM devices).
 * - Placeholder is a plain color, not a drawable resource: no extra bitmap allocations.
 */
fun booruGridItemAD(
	imageSize: Int,
	clickListener: OnListItemClickListener<MangaListModel>,
) = adapterDelegateViewBinding<BooruGridModel, ListModel, ItemBooruGridBinding>(
	{ inflater, parent -> ItemBooruGridBinding.inflate(inflater, parent, false) },
) {

	AdapterDelegateClickListenerAdapter(this, clickListener).attach(itemView)

	val placeholder = ColorDrawable(context.getThemeColor(materialR.attr.colorSurfaceContainer))
	binding.imageViewCover.let { iv ->
		iv.exactImageSize = Size(imageSize, imageSize)
		iv.placeholderDrawable = placeholder
		iv.errorDrawable = placeholder
		iv.fallbackDrawable = placeholder
	}

	bind { _ ->
		itemView.setTooltipCompat(item.getSummary(context))
		binding.imageViewCover.setImageAsync(item.coverUrl, item.manga)
	}
}
