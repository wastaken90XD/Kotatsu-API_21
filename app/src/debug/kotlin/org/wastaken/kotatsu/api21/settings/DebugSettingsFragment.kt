package org.wastaken.kotatsu.api21.settings

import android.os.Bundle
import androidx.preference.Preference
import org.wastaken.kotatsu.api21.R
import org.wastaken.kotatsu.api21.core.ui.BasePreferenceFragment
import org.koitharu.workinspector.WorkInspector

class DebugSettingsFragment : BasePreferenceFragment(R.string.debug) {

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		addPreferencesFromResource(R.xml.pref_debug)
	}

	override fun onPreferenceTreeClick(preference: Preference): Boolean = when (preference.key) {
		KEY_WORK_INSPECTOR -> {
			startActivity(WorkInspector.getIntent(preference.context))
			true
		}

		else -> super.onPreferenceTreeClick(preference)
	}

	private companion object {

		const val KEY_WORK_INSPECTOR = "work_inspector"
	}
}
