package org.wastaken.kotatsu.api21.settings

import android.os.Bundle
import android.view.View
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.EditTextPreference
import androidx.preference.SwitchPreferenceCompat
import dagger.hilt.android.AndroidEntryPoint
import org.wastaken.kotatsu.api21.R
import org.wastaken.kotatsu.api21.core.prefs.AppSettings
import org.wastaken.kotatsu.api21.core.ui.BasePreferenceFragment
import javax.inject.Inject

@AndroidEntryPoint
class WsrvQualitySettingsFragment : BasePreferenceFragment(R.string.wsrv_quality_settings) {

	@Inject
	lateinit var settings: AppSettings

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		addPreferencesFromResource(R.xml.pref_wsrv_quality)
		updateQualityEnabledState()
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		findPreference<SwitchPreferenceCompat>(AppSettings.KEY_WSRV_LOSSLESS)?.setOnPreferenceChangeListener { _, newValue ->
			updateQualityEnabledState()
			true
		}
	}

	private fun updateQualityEnabledState() {
		val isLossless = settings.wsrvLossless
		findPreference<EditTextPreference>(AppSettings.KEY_WSRV_QUALITY)?.isEnabled = !isLossless
	}

	override fun onPreferenceTreeClick(preference: Preference): Boolean = when (preference.key) {
		else -> super.onPreferenceTreeClick(preference)
	}
}
