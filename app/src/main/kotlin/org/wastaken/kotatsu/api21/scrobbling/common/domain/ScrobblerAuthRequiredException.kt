package org.wastaken.kotatsu.api21.scrobbling.common.domain

import okio.IOException
import org.wastaken.kotatsu.api21.scrobbling.common.domain.model.ScrobblerService

class ScrobblerAuthRequiredException(
	val scrobbler: ScrobblerService,
) : IOException()
