package org.wastaken.kotatsu.api21.core.exceptions

import okio.IOException

class WrapperIOException(override val cause: Exception) : IOException(cause)
