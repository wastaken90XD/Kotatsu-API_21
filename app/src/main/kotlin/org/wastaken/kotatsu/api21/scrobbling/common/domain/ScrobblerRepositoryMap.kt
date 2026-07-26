package org.wastaken.kotatsu.api21.scrobbling.common.domain

import org.wastaken.kotatsu.api21.scrobbling.anilist.data.AniListRepository
import org.wastaken.kotatsu.api21.scrobbling.common.data.ScrobblerRepository
import org.wastaken.kotatsu.api21.scrobbling.common.domain.model.ScrobblerService
import org.wastaken.kotatsu.api21.scrobbling.kitsu.data.KitsuRepository
import org.wastaken.kotatsu.api21.scrobbling.mal.data.MALRepository
import org.wastaken.kotatsu.api21.scrobbling.shikimori.data.ShikimoriRepository
import javax.inject.Inject
import javax.inject.Provider

class ScrobblerRepositoryMap @Inject constructor(
	private val shikimoriRepository: Provider<ShikimoriRepository>,
	private val aniListRepository: Provider<AniListRepository>,
	private val malRepository: Provider<MALRepository>,
	private val kitsuRepository: Provider<KitsuRepository>,
) {

	operator fun get(scrobblerService: ScrobblerService): ScrobblerRepository = when (scrobblerService) {
		ScrobblerService.SHIKIMORI -> shikimoriRepository
		ScrobblerService.ANILIST -> aniListRepository
		ScrobblerService.MAL -> malRepository
		ScrobblerService.KITSU -> kitsuRepository
	}.get()
}
