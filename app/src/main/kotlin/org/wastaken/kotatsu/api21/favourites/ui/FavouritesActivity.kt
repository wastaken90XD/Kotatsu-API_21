package org.wastaken.kotatsu.api21.favourites.ui

import android.os.Bundle
import org.wastaken.kotatsu.api21.core.nav.AppRouter
import org.wastaken.kotatsu.api21.core.ui.FragmentContainerActivity
import org.wastaken.kotatsu.api21.favourites.ui.list.FavouritesListFragment

class FavouritesActivity : FragmentContainerActivity(FavouritesListFragment::class.java) {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		val categoryTitle = intent.getStringExtra(AppRouter.KEY_TITLE)
		if (categoryTitle != null) {
			title = categoryTitle
		}
	}
}
