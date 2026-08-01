package org.wastaken.kotatsu.api21.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.ListPreference
import androidx.preference.SwitchPreferenceCompat
import dagger.hilt.android.AndroidEntryPoint
import org.wastaken.kotatsu.api21.R
import org.wastaken.kotatsu.api21.core.network.imageproxy.WsrvNlProxyInterceptor
import org.wastaken.kotatsu.api21.core.prefs.AppSettings
import org.wastaken.kotatsu.api21.core.ui.BasePreferenceFragment

@AndroidEntryPoint
class WsrvQualitySettingsFragment : BasePreferenceFragment(R.string.wsrv_quality_settings),
	SharedPreferences.OnSharedPreferenceChangeListener {

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		addPreferencesFromResource(R.xml.pref_wsrv_quality)
		updateVisibility()
		updateLivePreview()
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		settings.subscribe(this)
	}

	override fun onDestroyView() {
		settings.unsubscribe(this)
		super.onDestroyView()
	}

	override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
		if (key?.startsWith("wsrv_") == true) {
			updateVisibility()
			updateLivePreview()
		}
	}

	private fun updateVisibility() {
		val isLossless = settings.wsrvLossless
		findPreference<EditTextPreference>(AppSettings.KEY_WSRV_QUALITY)?.isEnabled = !isLossless

		val format = settings.wsrvFormat
		val isPng = format == "png"
		findPreference<EditTextPreference>(AppSettings.KEY_WSRV_PNG_LEVEL)?.isEnabled = isPng
		findPreference<SwitchPreferenceCompat>(AppSettings.KEY_WSRV_PNG_FILTER)?.isEnabled = isPng

		val hasWidth = settings.wsrvMaxWidth > 0
		val hasHeight = settings.wsrvMaxHeight > 0
		val dimensionsSet = hasWidth && hasHeight
		val fitPref = findPreference<ListPreference>(AppSettings.KEY_WSRV_FIT)
		val alignPref = findPreference<ListPreference>(AppSettings.KEY_WSRV_ALIGNMENT)
		
		val dimensionsNote = if (!dimensionsSet) "\nNote: Fit and Alignment only apply when both Max width and Max height are set." else ""
		
		fitPref?.summary = getString(R.string.wsrv_fit_summary) + dimensionsNote
		alignPref?.summary = getString(R.string.wsrv_alignment_summary) + dimensionsNote
	}

	private fun updateLivePreview() {
		val interceptor = WsrvNlProxyInterceptor(settings)
		val previewUrl = interceptor.buildUrl("https://example.com/image.jpg", includeCoilSize = false).toString()
		findPreference<Preference>("wsrv_url_preview")?.summary = previewUrl
	}

	override fun onPreferenceTreeClick(preference: Preference): Boolean = when (preference.key) {
		else -> super.onPreferenceTreeClick(preference)
	}
}
