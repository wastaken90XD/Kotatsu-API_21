package org.wastaken.kotatsu.api21.details.ui.related

import android.view.Menu
import android.view.MenuInflater
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import org.wastaken.kotatsu.api21.R
import org.wastaken.kotatsu.api21.core.ui.list.ListSelectionController
import org.wastaken.kotatsu.api21.list.ui.MangaListFragment

@AndroidEntryPoint
class RelatedListFragment : MangaListFragment() {

	override val viewModel by viewModels<RelatedListViewModel>()
	override val isSwipeRefreshEnabled = false

	override fun onScrolledToEnd() = Unit

	override fun onCreateActionMode(
		controller: ListSelectionController,
		menuInflater: MenuInflater,
		menu: Menu
	): Boolean {
		menuInflater.inflate(R.menu.mode_remote, menu)
		return super.onCreateActionMode(controller, menuInflater, menu)
	}
}

