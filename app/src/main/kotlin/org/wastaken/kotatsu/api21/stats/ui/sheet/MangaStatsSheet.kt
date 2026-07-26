package org.wastaken.kotatsu.api21.stats.ui.sheet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.collection.IntList
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import org.wastaken.kotatsu.api21.R
import org.wastaken.kotatsu.api21.core.nav.router
import org.wastaken.kotatsu.api21.core.ui.sheet.BaseAdaptiveSheet
import org.wastaken.kotatsu.api21.core.util.KotatsuColors
import org.wastaken.kotatsu.api21.core.util.ext.consume
import org.wastaken.kotatsu.api21.core.util.ext.observe
import org.wastaken.kotatsu.api21.core.util.ext.textAndVisible
import org.wastaken.kotatsu.api21.databinding.SheetStatsMangaBinding
import org.koitharu.kotatsu.parsers.util.format
import org.wastaken.kotatsu.api21.stats.ui.views.BarChartView

@AndroidEntryPoint
class MangaStatsSheet : BaseAdaptiveSheet<SheetStatsMangaBinding>(), View.OnClickListener {

	private val viewModel: MangaStatsViewModel by viewModels()

	override fun onCreateViewBinding(inflater: LayoutInflater, container: ViewGroup?): SheetStatsMangaBinding {
		return SheetStatsMangaBinding.inflate(inflater, container, false)
	}

	override fun onViewBindingCreated(binding: SheetStatsMangaBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		binding.textViewTitle.text = viewModel.manga.title
		binding.chartView.barColor = KotatsuColors.ofManga(binding.root.context, viewModel.manga)
		viewModel.stats.observe(viewLifecycleOwner, ::onStatsChanged)
		viewModel.startDate.observe(viewLifecycleOwner) {
			binding.textViewStart.textAndVisible = it?.format(binding.root.context)
		}
		viewModel.totalPagesRead.observe(viewLifecycleOwner) {
			binding.textViewPages.text = getString(R.string.pages_read_s, it.format())
		}
		binding.buttonOpen.setOnClickListener(this)
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val typeMask = WindowInsetsCompat.Type.systemBars()
		viewBinding?.scrollView?.updatePadding(
			bottom = insets.getInsets(typeMask).bottom,
		)
		return insets.consume(v, typeMask, bottom = true)
	}

	override fun onClick(v: View) {
		router.openDetails(viewModel.manga)
	}

	private fun onStatsChanged(stats: IntList) {
		val chartView = viewBinding?.chartView ?: return
		if (stats.isEmpty()) {
			chartView.setData(emptyList())
			return
		}
		val bars = ArrayList<BarChartView.Bar>(stats.size)
		stats.forEach { pages ->
			bars.add(
				BarChartView.Bar(
					value = pages,
					label = pages.toString(),
				),
			)
		}
		chartView.setData(bars)
	}
}
