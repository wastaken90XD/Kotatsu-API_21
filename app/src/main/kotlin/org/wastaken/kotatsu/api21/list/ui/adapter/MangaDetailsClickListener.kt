package org.wastaken.kotatsu.api21.list.ui.adapter

import android.view.View
import org.wastaken.kotatsu.api21.core.ui.list.OnListItemClickListener
import org.wastaken.kotatsu.api21.list.ui.model.MangaListModel
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaTag

interface MangaDetailsClickListener : OnListItemClickListener<MangaListModel> {

	fun onReadClick(manga: Manga, view: View)

	fun onTagClick(manga: Manga, tag: MangaTag, view: View)
}
