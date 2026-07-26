package org.wastaken.kotatsu.api21.history.domain.model

import org.wastaken.kotatsu.api21.core.model.MangaHistory
import org.koitharu.kotatsu.parsers.model.Manga

data class MangaWithHistory(
	val manga: Manga,
	val history: MangaHistory
)
