package org.wastaken.kotatsu.api21.core.crash

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import org.wastaken.kotatsu.api21.BuildConfig
import java.io.File
import kotlin.system.exitProcess

/**
 * Catches uncaught exceptions, writes a crash report to a file, and
 * terminates the process.  On next launch [peekCrashReport] detects
 * the file so the UI can display it.
 */
class CrashReportHandler private constructor(
	private val appContext: Context,
	private val previousHandler: Thread.UncaughtExceptionHandler?,
) : Thread.UncaughtExceptionHandler {

	override fun uncaughtException(thread: Thread, throwable: Throwable) {
		try {
			val report = CrashReportHandler.buildReport(thread, throwable)
		CrashReportHandler.crashFile(appContext).writeText(report)
		} catch (_: Throwable) {
			// Last-resort: if even writing the report fails, still die.
		}
		previousHandler?.uncaughtException(thread, throwable)
			?: Process.killProcess(Process.myPid()).also { exitProcess(1) }
	}

	companion object {

		private const val FILE_NAME = "crash_report.txt"

		fun install(context: Context) {
			val previous = Thread.getDefaultUncaughtExceptionHandler()
			Thread.setDefaultUncaughtExceptionHandler(
				CrashReportHandler(context.applicationContext, previous),
			)
		}

		/** Returns the saved crash report text, or null if none. Deletes the file. */
		fun peekCrashReport(context: Context): String? {
			val file = crashFile(context)
			if (!file.exists()) return null
			val text = runCatching { file.readText() }.getOrNull()
			file.delete()
			return text
		}

		fun launchIntent(context: Context, report: String): Intent {
			return Intent(context, CrashReportActivity::class.java).apply {
				addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
				putExtra(CrashReportActivity.EXTRA_REPORT, report)
			}
		}

		fun crashFile(context: Context) = File(context.filesDir, FILE_NAME)

		fun buildReport(thread: Thread, throwable: Throwable): String = buildString {
			append("Thread: ").append(thread.name).append('\n')
			append("App: ").append(BuildConfig.APPLICATION_ID)
			append(" v").append(BuildConfig.VERSION_NAME)
			append(" (").append(BuildConfig.BUILD_TYPE).append(")\n")
			append("Android: ").append(Build.VERSION.RELEASE)
			append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
			append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
			append('\n')
			append(throwable.stackTraceToString())
		}
	}
}
