package org.wastaken.kotatsu.api21.list.ui.model

import org.wastaken.kotatsu.api21.core.ui.model.MangaOverride
import org.koitharu.kotatsu.parsers.model.Manga

/**
 * List model for booru sources grid tiles (see BooruGridAdapter).
 * Intentionally a direct subclass of [MangaListModel] (not of MangaGridModel):
 * adapter delegates are matched by class, and this class must never be handled
 * by the standard manga tile delegates. Carries no progress/badges data to keep
 * binding cheap on weak hardware — booru posts have no reading progress.
 */
data class BooruGridModel(
	override val manga: Manga,
	override val override: MangaOverride?,
) : MangaListModel() {

	override val counter: Int
		get() = 0
}
