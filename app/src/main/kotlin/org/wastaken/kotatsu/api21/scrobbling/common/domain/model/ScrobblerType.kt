package org.wastaken.kotatsu.api21.scrobbling.common.domain.model

import javax.inject.Qualifier

@Qualifier
annotation class ScrobblerType(
	val service: ScrobblerService
)
