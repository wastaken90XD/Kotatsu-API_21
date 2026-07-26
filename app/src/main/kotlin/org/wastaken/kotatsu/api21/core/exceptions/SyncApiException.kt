package org.wastaken.kotatsu.api21.core.exceptions

class SyncApiException(
	message: String,
	val code: Int,
) : RuntimeException(message)
