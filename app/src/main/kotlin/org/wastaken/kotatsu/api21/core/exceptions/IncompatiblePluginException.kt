package org.wastaken.kotatsu.api21.core.exceptions

class IncompatiblePluginException(
	val name: String?,
	cause: Throwable?,
) : RuntimeException(cause)
