package org.wastaken.kotatsu.api21.remotelist.ui

import android.content.SharedPreferences
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.core.view.MenuProvider
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.drop
import org.wastaken.kotatsu.api21.R
import org.wastaken.kotatsu.api21.core.model.getTitle
import org.wastaken.kotatsu.api21.core.nav.router
import org.wastaken.kotatsu.api21.core.prefs.ListMode
import org.wastaken.kotatsu.api21.core.ui.list.ListSelectionController
import org.wastaken.kotatsu.api21.core.ui.util.MenuInvalidator
import org.wastaken.kotatsu.api21.core.util.ext.addMenuProvider
import org.wastaken.kotatsu.api21.core.util.ext.getCauseUrl
import org.wastaken.kotatsu.api21.core.util.ext.isHttpUrl
import org.wastaken.kotatsu.api21.core.util.ext.observe
import org.wastaken.kotatsu.api21.core.util.ext.observeEvent
import org.wastaken.kotatsu.api21.core.util.ext.withArgs
import org.wastaken.kotatsu.api21.databinding.FragmentListBinding
import org.wastaken.kotatsu.api21.filter.ui.FilterCoordinator
import org.wastaken.kotatsu.api21.list.ui.MangaListFragment
import org.wastaken.kotatsu.api21.core.prefs.AppSettings
import org.wastaken.kotatsu.api21.list.ui.adapter.BooruGridAdapter
import org.wastaken.kotatsu.api21.list.ui.adapter.ListItemType
import org.wastaken.kotatsu.api21.list.ui.adapter.MangaListAdapter
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.wastaken.kotatsu.api21.search.domain.SearchKind

@AndroidEntryPoint
class RemoteListFragment : MangaListFragment(), FilterCoordinator.Owner {

	override val viewModel by viewModels<RemoteListViewModel>()

	private var booruAdapter: BooruGridAdapter? = null
	private val booruSpanSizeLookup = BooruSpanSizeLookup()

	/** Live-applies the booru grid column setting (existing AppSettings listener pattern). */
	private val booruColumnsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
		if (key == AppSettings.KEY_BOORU_GRID_COLUMNS) {
			onBooruGridColumnsChanged()
		}
	}

	override val filterCoordinator: FilterCoordinator
		get() = viewModel.filterCoordinator

	override fun onViewBindingCreated(binding: FragmentListBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		addMenuProvider(RemoteListMenuProvider())
		addMenuProvider(MangaSearchMenuProvider(filterCoordinator, viewModel))
		viewModel.isRandomLoading.observe(viewLifecycleOwner, MenuInvalidator(requireActivity()))
		viewModel.onOpenManga.observeEvent(viewLifecycleOwner) { router.openDetails(it) }
		settings.subscribe(booruColumnsListener)
		filterCoordinator.observe().distinctUntilChangedBy { it.listFilter.isEmpty() }
			.drop(1)
			.observe(viewLifecycleOwner) {
				activity?.invalidateMenu()
			}
	}

	override fun onCreateAdapter(): MangaListAdapter {
		return if (viewModel.isBooru) {
			// booru sources are browsed as a square-thumbnail grid (see BooruGridAdapter);
			// all other sources keep the standard manga tiles untouched
			BooruGridAdapter(this, settings.booruGridColumns).also { booruAdapter = it }
		} else {
			super.onCreateAdapter()
		}
	}

	override fun onListModeChanged(mode: ListMode) {
		if (!viewModel.isBooru) {
			super.onListModeChanged(mode)
			return
		}
		val columns = settings.booruGridColumns
		booruSpanSizeLookup.fullSpan = columns
		with(requireViewBinding().recyclerView) {
			layoutManager = GridLayoutManager(context, columns).also {
				it.spanSizeLookup = booruSpanSizeLookup
			}
			setItemViewCacheSize(BOORU_VIEW_CACHE_SIZE)
		}
	}

	override fun onGridScaleChanged(scale: Float) {
		// the global grid-size scale does not apply to the booru grid: it follows its
		// own column-count setting (booru_grid_columns) instead
		if (!viewModel.isBooru) {
			super.onGridScaleChanged(scale)
		}
	}

	private fun onBooruGridColumnsChanged() {
		if (!viewModel.isBooru) {
			return
		}
		val binding = requireViewBinding()
		val columns = settings.booruGridColumns
		(binding.recyclerView.layoutManager as? GridLayoutManager)?.let { manager: GridLayoutManager ->
			manager.spanCount = columns
			booruSpanSizeLookup.fullSpan = columns
			// setSpanCount already invalidates the span-index cache; explicit call keeps
			// the lookup state consistent
			booruSpanSizeLookup.invalidateSpanIndexCache()
		}
		booruAdapter?.notifyDataSetChanged()
	}

	override fun onDestroyView() {
		settings.unsubscribe(booruColumnsListener)
		booruAdapter = null
		super.onDestroyView()
	}

	private inner class BooruSpanSizeLookup : GridLayoutManager.SpanSizeLookup() {

		/** Mirrors the current column count: state/footer rows are always full-width. */
		var fullSpan: Int = 3

		override fun getSpanSize(position: Int): Int {
			return when (booruAdapter?.getItemViewType(position)) {
				ListItemType.BOORU_GRID.ordinal -> 1
				else -> fullSpan
			}
		}
	}

	override fun onScrolledToEnd() {
		viewModel.loadNextPage()
	}

	override fun onCreateActionMode(
		controller: ListSelectionController,
		menuInflater: MenuInflater,
		menu: Menu
	): Boolean {
		menuInflater.inflate(R.menu.mode_remote, menu)
		return super.onCreateActionMode(controller, menuInflater, menu)
	}

	override fun onFilterClick(view: View?) {
		router.showFilterSheet()
	}

	override fun onEmptyActionClick() {
		if (filterCoordinator.isFilterApplied) {
			filterCoordinator.reset()
		} else {
			openInBrowser(null) // should never be called
		}
	}

	override fun onFooterButtonClick() {
		val filter = filterCoordinator.snapshot().listFilter
		when {
			!filter.query.isNullOrEmpty() -> router.openSearch(filter.query.orEmpty(), SearchKind.SIMPLE)
			!filter.author.isNullOrEmpty() -> router.openSearch(filter.author.orEmpty(), SearchKind.AUTHOR)
			filter.tags.size == 1 -> router.openSearch(filter.tags.singleOrNull()?.title.orEmpty(), SearchKind.TAG)
		}
	}

	override fun onSecondaryErrorActionClick(error: Throwable) {
		openInBrowser(error.getCauseUrl())
	}

	private fun openInBrowser(url: String?) {
		if (url?.isHttpUrl() == true) {
			router.openBrowser(
				url = url,
				source = viewModel.source,
				title = viewModel.source.getTitle(requireContext()),
			)
		} else {
			Snackbar.make(requireViewBinding().recyclerView, R.string.operation_not_supported, Snackbar.LENGTH_SHORT)
				.show()
		}
	}

	private inner class RemoteListMenuProvider : MenuProvider {

		override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
			menuInflater.inflate(R.menu.opt_list_remote, menu)
		}

		override fun onMenuItemSelected(menuItem: MenuItem): Boolean = when (menuItem.itemId) {
			R.id.action_source_settings -> {
				router.openSourceSettings(viewModel.source)
				true
			}

			R.id.action_random -> {
				viewModel.openRandom()
				true
			}

			R.id.action_filter -> {
				onFilterClick(null)
				true
			}

			R.id.action_filter_reset -> {
				filterCoordinator.reset()
				true
			}

			else -> false
		}

		override fun onPrepareMenu(menu: Menu) {
			super.onPrepareMenu(menu)
			menu.findItem(R.id.action_random)?.isEnabled = !viewModel.isRandomLoading.value
			menu.findItem(R.id.action_filter_reset)?.isVisible = filterCoordinator.isFilterApplied
		}
	}

	companion object {

		const val ARG_SOURCE = "provider"

		/** Extra recycled views kept around to avoid rebinding on slow scroll (weak hardware). */
		private const val BOORU_VIEW_CACHE_SIZE = 6

		fun newInstance(source: MangaSource) = RemoteListFragment().withArgs(1) {
			putString(ARG_SOURCE, source.name)
		}
	}
}
