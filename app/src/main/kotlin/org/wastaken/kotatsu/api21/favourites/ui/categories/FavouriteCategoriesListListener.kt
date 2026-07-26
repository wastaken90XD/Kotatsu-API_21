package org.wastaken.kotatsu.api21.favourites.ui.categories

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import org.wastaken.kotatsu.api21.core.model.FavouriteCategory
import org.wastaken.kotatsu.api21.core.ui.list.OnListItemClickListener

interface FavouriteCategoriesListListener : OnListItemClickListener<FavouriteCategory?> {

	fun onDragHandleTouch(holder: RecyclerView.ViewHolder): Boolean

	fun onEditClick(item: FavouriteCategory, view: View)

	fun onShowAllClick(isChecked: Boolean)
}
