package org.wastaken.kotatsu.api21.core.util

import android.content.Context
import kotlinx.coroutines.CoroutineExceptionHandler
import org.wastaken.kotatsu.api21.core.crash.CrashReportHandler
import org.wastaken.kotatsu.api21.core.util.ext.printStackTraceDebug
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

class AcraCoroutineErrorHandler : AbstractCoroutineContextElement(CoroutineExceptionHandler),
	CoroutineExceptionHandler {

	override fun handleException(context: CoroutineContext, exception: Throwable) {
		exception.printStackTraceDebug()
		try {
			val app = try {
				Class.forName("android.app.ActivityThread")
					.getMethod("currentApplication")
					.invoke(null) as? android.app.Application
			} catch (_: Throwable) {
				null
			}
			val ctx = app?.applicationContext ?: return
			val report = CrashReportHandler.buildReport(Thread.currentThread(), exception)
			CrashReportHandler.crashFile(ctx).writeText(report)
		} catch (_: Throwable) {
			// Last-resort: if writing fails, still don't crash the process.
		}
	}
}
