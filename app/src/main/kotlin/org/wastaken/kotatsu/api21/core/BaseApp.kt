package org.wastaken.kotatsu.api21.core

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.annotation.WorkerThread
import androidx.appcompat.app.AppCompatDelegate
import androidx.hilt.work.HiltWorkerFactory
import androidx.room.InvalidationTracker
import androidx.work.Configuration
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.acra.ACRA
import org.acra.data.StringFormat
import org.acra.ktx.initAcra
import org.conscrypt.Conscrypt
import org.wastaken.kotatsu.api21.BuildConfig
import org.wastaken.kotatsu.api21.R
import org.wastaken.kotatsu.api21.core.crash.CrashReportHandler
import org.wastaken.kotatsu.api21.core.db.MangaDatabase
import org.wastaken.kotatsu.api21.core.os.AppValidator
import org.wastaken.kotatsu.api21.core.os.RomCompat
import org.wastaken.kotatsu.api21.core.prefs.AppSettings
import org.wastaken.kotatsu.api21.core.util.WorkServiceStopHelper
import org.wastaken.kotatsu.api21.core.util.ext.processLifecycleScope
import org.wastaken.kotatsu.api21.local.data.LocalStorageChanges
import org.wastaken.kotatsu.api21.local.data.index.LocalMangaIndex
import org.wastaken.kotatsu.api21.local.domain.model.LocalManga
import org.koitharu.kotatsu.parsers.util.suspendlazy.getOrNull
import org.wastaken.kotatsu.api21.settings.work.WorkScheduleManager
import java.security.Security
import javax.inject.Inject
import javax.inject.Provider

@HiltAndroidApp
open class BaseApp : Application(), Configuration.Provider {

	@Inject
	lateinit var databaseObserversProvider: Provider<Set<@JvmSuppressWildcards InvalidationTracker.Observer>>

	@Inject
	lateinit var activityLifecycleCallbacks: Set<@JvmSuppressWildcards ActivityLifecycleCallbacks>

	@Inject
	lateinit var database: Provider<MangaDatabase>

	@Inject
	lateinit var settings: AppSettings

	@Inject
	lateinit var workerFactory: HiltWorkerFactory

	@Inject
	lateinit var appValidator: AppValidator

	@Inject
	lateinit var workScheduleManager: WorkScheduleManager

	@Inject
	lateinit var workManagerProvider: Provider<WorkManager>

	@Inject
	lateinit var localMangaIndexProvider: Provider<LocalMangaIndex>

	@Inject
	@LocalStorageChanges
	lateinit var localStorageChanges: MutableSharedFlow<LocalManga?>

	override val workManagerConfiguration: Configuration
		get() = Configuration.Builder()
			.setWorkerFactory(workerFactory)
			.build()

	override fun onCreate() {
		super.onCreate()
		CrashReportHandler.install(this)
		if (ACRA.isACRASenderServiceProcess()) {
			return
		}
		// If the previous launch crashed, show the report before anything else
		CrashReportHandler.peekCrashReport(this)?.let { report ->
			startActivity(CrashReportHandler.launchIntent(this, report))
		}
		AppCompatDelegate.setDefaultNightMode(settings.theme)
		// TLS 1.3 support for Android < 10
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
			Security.insertProviderAt(Conscrypt.newProvider(), 1)
		}
		setupActivityLifecycleCallbacks()
		processLifecycleScope.launch {
			ACRA.errorReporter.putCustomData("isOriginalApp", appValidator.isOriginalApp.getOrNull().toString())
			ACRA.errorReporter.putCustomData("isMiui", RomCompat.isMiui.getOrNull().toString())
		}
		processLifecycleScope.launch(Dispatchers.Default) {
			setupDatabaseObservers()
			localStorageChanges.collect(localMangaIndexProvider.get())
		}
		workScheduleManager.init()
		WorkServiceStopHelper(workManagerProvider).setup()
	}

	override fun attachBaseContext(base: Context) {
		super.attachBaseContext(base)
		if (ACRA.isACRASenderServiceProcess()) {
			return
		}
		initAcra {
			buildConfigClass = BuildConfig::class.java
			reportFormat = StringFormat.JSON
			// Personal fork: no crash dialog and no reporting to the
			// upstream developer's server.  ACRA is kept initialised so
			// AcraScreenLogger can still attach diagnostics to in-app
			// error reports.
		}
	}

	@WorkerThread
	private fun setupDatabaseObservers() {
		val tracker = database.get().invalidationTracker
		databaseObserversProvider.get().forEach {
			tracker.addObserver(it)
		}
	}

	private fun setupActivityLifecycleCallbacks() {
		activityLifecycleCallbacks.forEach {
			registerActivityLifecycleCallbacks(it)
		}
	}
}
