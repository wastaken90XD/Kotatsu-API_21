package org.wastaken.kotatsu.api21.settings.userdata

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.viewModels
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.TwoStatePreference
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import org.wastaken.kotatsu.api21.R
import org.wastaken.kotatsu.api21.backups.domain.BackupUtils
import org.wastaken.kotatsu.api21.backups.ui.backup.BackupService
import org.wastaken.kotatsu.api21.core.exceptions.resolve.SnackbarErrorObserver
import org.wastaken.kotatsu.api21.core.nav.router
import org.wastaken.kotatsu.api21.core.os.AppShortcutManager
import org.wastaken.kotatsu.api21.core.prefs.AppSettings
import org.wastaken.kotatsu.api21.core.prefs.ScreenshotsPolicy
import org.wastaken.kotatsu.api21.core.prefs.SearchSuggestionType
import org.wastaken.kotatsu.api21.core.prefs.TriStateOption
import org.wastaken.kotatsu.api21.core.ui.BasePreferenceFragment
import org.wastaken.kotatsu.api21.core.util.FileSize
import org.wastaken.kotatsu.api21.core.util.ext.observe
import org.wastaken.kotatsu.api21.core.util.ext.observeEvent
import org.wastaken.kotatsu.api21.core.util.ext.setDefaultValueCompat
import org.wastaken.kotatsu.api21.core.util.ext.tryLaunch
import org.koitharu.kotatsu.parsers.util.mapToSet
import org.koitharu.kotatsu.parsers.util.names
import org.wastaken.kotatsu.api21.settings.protect.ProtectSetupActivity
import org.wastaken.kotatsu.api21.settings.utils.MultiSummaryProvider
import javax.inject.Inject

@AndroidEntryPoint
class UserDataSettingsFragment : BasePreferenceFragment(R.string.data_and_privacy),
	SharedPreferences.OnSharedPreferenceChangeListener,
	ActivityResultCallback<Uri?> {

	@Inject
	lateinit var appShortcutManager: AppShortcutManager

	private val viewModel: UserDataSettingsViewModel by viewModels()

	private val backupSelectCall = registerForActivityResult(
		ActivityResultContracts.OpenDocument(),
		this,
	)

	private val backupCreateCall = registerForActivityResult(
		ActivityResultContracts.CreateDocument("application/zip"),
	) { uri ->
		if (uri != null) {
			if (!BackupService.start(requireContext(), uri)) {
				Snackbar.make(
					listView, R.string.operation_not_supported, Snackbar.LENGTH_SHORT,
				).show()
			}
		}
	}

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		addPreferencesFromResource(R.xml.pref_user_data)
		findPreference<Preference>(AppSettings.KEY_SHORTCUTS)?.isVisible =
			appShortcutManager.isDynamicShortcutsAvailable()
		findPreference<TwoStatePreference>(AppSettings.KEY_PROTECT_APP)
			?.isChecked = !settings.appPassword.isNullOrEmpty()
		findPreference<ListPreference>(AppSettings.KEY_SCREENSHOTS_POLICY)?.run {
			entryValues = ScreenshotsPolicy.entries.names()
			setDefaultValueCompat(ScreenshotsPolicy.ALLOW.name)
		}
		findPreference<ListPreference>(AppSettings.KEY_INCOGNITO_NSFW)?.run {
			entryValues = TriStateOption.entries.names()
			setDefaultValueCompat(TriStateOption.ASK.name)
		}
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		bindPeriodicalBackupSummary()
		findPreference<MultiSelectListPreference>(AppSettings.KEY_SEARCH_SUGGESTION_TYPES)?.let { pref ->
			pref.entryValues = SearchSuggestionType.entries.names()
			pref.entries = SearchSuggestionType.entries.map { pref.context.getString(it.titleResId) }.toTypedArray()
			pref.summaryProvider = MultiSummaryProvider(R.string.none)
			pref.values = settings.searchSuggestionTypes.mapToSet { it.name }
		}
		findPreference<Preference>(AppSettings.KEY_STORAGE_USAGE)?.let { pref ->
			viewModel.storageUsage.observe(viewLifecycleOwner) { size ->
				pref.summary = if (size < 0L) {
					pref.context.getString(R.string.computing_)
				} else {
					FileSize.BYTES.format(pref.context, size)
				}
			}
		}
		viewModel.onError.observeEvent(viewLifecycleOwner, SnackbarErrorObserver(listView, this))
		settings.subscribe(this)
	}

	override fun onDestroyView() {
		settings.unsubscribe(this)
		super.onDestroyView()
	}

	override fun onPreferenceTreeClick(preference: Preference): Boolean {
		return when (preference.key) {
			AppSettings.KEY_BACKUP -> {
				if (!backupCreateCall.tryLaunch(BackupUtils.generateFileName(preference.context))) {
					Snackbar.make(
						listView, R.string.operation_not_supported, Snackbar.LENGTH_SHORT,
					).show()
				}
				true
			}

			AppSettings.KEY_RESTORE -> {
				if (!backupSelectCall.tryLaunch(arrayOf("*/*"))) {
					Snackbar.make(
						listView, R.string.operation_not_supported, Snackbar.LENGTH_SHORT,
					).show()
				}
				true
			}

			AppSettings.KEY_PROTECT_APP -> {
				val pref = (preference as? TwoStatePreference ?: return false)
				if (pref.isChecked) {
					pref.isChecked = false
					startActivity(Intent(preference.context, ProtectSetupActivity::class.java))
				} else {
					settings.appPassword = null
				}
				true
			}

			else -> super.onPreferenceTreeClick(preference)
		}
	}

	override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
		when (key) {
			AppSettings.KEY_APP_PASSWORD -> {
				findPreference<TwoStatePreference>(AppSettings.KEY_PROTECT_APP)
					?.isChecked = !settings.appPassword.isNullOrEmpty()
			}
		}
	}

	override fun onActivityResult(result: Uri?) {
		if (result != null) {
			router.showBackupRestoreDialog(result)
		}
	}

	private fun bindPeriodicalBackupSummary() {
		val preference = findPreference<Preference>(AppSettings.KEY_BACKUP_PERIODICAL_ENABLED) ?: return
		val entries = resources.getStringArray(R.array.backup_frequency)
		val entryValues = resources.getStringArray(R.array.values_backup_frequency)
		viewModel.periodicalBackupFrequency.observe(viewLifecycleOwner) { freq ->
			preference.summary = if (freq == 0L) {
				getString(R.string.disabled)
			} else {
				val index = entryValues.indexOf(freq.toString())
				entries.getOrNull(index)
			}
		}
	}
}
