package org.wastaken.kotatsu.api21.core.util

import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import org.wastaken.kotatsu.api21.core.util.ext.printStackTraceDebug
import org.koitharu.kotatsu.parsers.exception.toCrashReport
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

class AcraCoroutineErrorHandler : AbstractCoroutineContextElement(CoroutineExceptionHandler),
	CoroutineExceptionHandler {

	override fun handleException(context: CoroutineContext, exception: Throwable) {
		exception.printStackTraceDebug()
		Log.e("CoroutineError", exception.toCrashReport())
	}
}
