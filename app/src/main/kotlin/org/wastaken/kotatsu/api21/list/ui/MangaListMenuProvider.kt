package org.wastaken.kotatsu.api21.list.ui

import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import org.wastaken.kotatsu.api21.R
import org.wastaken.kotatsu.api21.core.nav.router
import org.wastaken.kotatsu.api21.favourites.ui.list.FavouritesListFragment
import org.wastaken.kotatsu.api21.history.ui.HistoryListFragment
import org.wastaken.kotatsu.api21.list.ui.config.ListConfigSection
import org.wastaken.kotatsu.api21.suggestions.ui.SuggestionsFragment
import org.wastaken.kotatsu.api21.tracker.ui.updates.UpdatesFragment

class MangaListMenuProvider(
	private val fragment: Fragment,
) : MenuProvider {

	override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
		menuInflater.inflate(R.menu.opt_list, menu)
	}

	override fun onMenuItemSelected(menuItem: MenuItem): Boolean = when (menuItem.itemId) {
		R.id.action_list_mode -> {
			val section: ListConfigSection = when (fragment) {
				is HistoryListFragment -> ListConfigSection.History
				is SuggestionsFragment -> ListConfigSection.Suggestions
				is FavouritesListFragment -> ListConfigSection.Favorites(fragment.categoryId)
				is UpdatesFragment -> ListConfigSection.Updated
				else -> ListConfigSection.General
			}
			fragment.router.showListConfigSheet(section)
			true
		}

		else -> false
	}
}
