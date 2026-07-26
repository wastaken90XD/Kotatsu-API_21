package org.wastaken.kotatsu.api21.stats.ui

import android.content.res.ColorStateList
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import org.wastaken.kotatsu.api21.R
import org.wastaken.kotatsu.api21.core.ui.list.OnListItemClickListener
import org.wastaken.kotatsu.api21.core.util.KotatsuColors
import org.wastaken.kotatsu.api21.databinding.ItemStatsBinding
import org.koitharu.kotatsu.parsers.model.Manga
import org.wastaken.kotatsu.api21.stats.domain.StatsRecord

fun statsAD(
	listener: OnListItemClickListener<Manga>,
) = adapterDelegateViewBinding<StatsRecord, StatsRecord, ItemStatsBinding>(
	{ layoutInflater, parent -> ItemStatsBinding.inflate(layoutInflater, parent, false) },
) {

	binding.root.setOnClickListener { v ->
		listener.onItemClick(item.manga ?: return@setOnClickListener, v)
	}

	bind {
		binding.textViewTitle.text = item.manga?.title ?: getString(R.string.other_manga)
		binding.textViewSummary.text = item.time.format(context.resources)
		binding.imageViewBadge.imageTintList = ColorStateList.valueOf(KotatsuColors.ofManga(context, item.manga))
		binding.root.isClickable = item.manga != null
	}
}
