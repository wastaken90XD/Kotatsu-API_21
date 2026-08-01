package org.wastaken.kotatsu.api21.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.preference.ListPreference
import androidx.preference.Preference
import com.google.android.material.snackbar.Snackbar
import org.wastaken.kotatsu.api21.R
import org.wastaken.kotatsu.api21.core.network.DoHProvider
import org.wastaken.kotatsu.api21.core.network.proxy.ProxyType
import org.wastaken.kotatsu.api21.core.prefs.AppSettings
import org.wastaken.kotatsu.api21.core.ui.BasePreferenceFragment
import org.wastaken.kotatsu.api21.core.util.ext.setDefaultValueCompat
import org.koitharu.kotatsu.parsers.util.names

class NetworkSettingsFragment :
	BasePreferenceFragment(R.string.network),
	SharedPreferences.OnSharedPreferenceChangeListener {

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		addPreferencesFromResource(R.xml.pref_network)
		findPreference<ListPreference>(AppSettings.KEY_DOH)?.run {
			entryValues = DoHProvider.entries.names()
			setDefaultValueCompat(DoHProvider.NONE.name)
		}
		bindProxySummary()
		updateWsrvVisibility()
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		settings.subscribe(this)
	}

	override fun onDestroyView() {
		settings.unsubscribe(this)
		super.onDestroyView()
	}

	override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
		when (key) {
			AppSettings.KEY_SSL_BYPASS -> {
				Snackbar.make(listView, R.string.settings_apply_restart_required, Snackbar.LENGTH_INDEFINITE).show()
			}

			AppSettings.KEY_PROXY_TYPE,
			AppSettings.KEY_PROXY_ADDRESS,
			AppSettings.KEY_PROXY_PORT -> {
				bindProxySummary()
			}

			AppSettings.KEY_IMAGES_PROXY -> {
				updateWsrvVisibility()
			}
		}
	}

	private fun updateWsrvVisibility() {
		findPreference<Preference>("wsrv_quality")?.isVisible = settings.imagesProxy == 0
	}

	private fun bindProxySummary() {
		findPreference<Preference>(AppSettings.KEY_PROXY)?.run {
			val type = settings.proxyType
			val address = settings.proxyAddress
			val port = settings.proxyPort
			summary = when {
				type == ProxyType.DIRECT -> context.getString(R.string.disabled)
				address.isNullOrEmpty() || port == 0 -> context.getString(R.string.invalid_proxy_configuration)
				else -> "$address:$port"
			}
		}
	}
}
