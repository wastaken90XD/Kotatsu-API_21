package org.wastaken.kotatsu.api21.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.wastaken.kotatsu.api21.R
import org.wastaken.kotatsu.api21.core.network.BaseHttpClient
import org.wastaken.kotatsu.api21.core.network.proxy.ProxyType
import org.wastaken.kotatsu.api21.core.prefs.AppSettings
import org.wastaken.kotatsu.api21.core.ui.BasePreferenceFragment
import org.wastaken.kotatsu.api21.core.util.ext.getDisplayMessage
import org.wastaken.kotatsu.api21.core.util.ext.printStackTraceDebug
import org.wastaken.kotatsu.api21.core.util.ext.viewLifecycleScope
import org.koitharu.kotatsu.parsers.util.await
import org.wastaken.kotatsu.api21.settings.utils.EditTextBindListener
import org.wastaken.kotatsu.api21.settings.utils.PasswordSummaryProvider
import org.wastaken.kotatsu.api21.settings.utils.validation.DomainValidator
import org.wastaken.kotatsu.api21.settings.utils.validation.HexSecretValidator
import org.wastaken.kotatsu.api21.settings.utils.validation.PortNumberValidator
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

@AndroidEntryPoint
class ProxySettingsFragment : BasePreferenceFragment(R.string.proxy),
	SharedPreferences.OnSharedPreferenceChangeListener {

	private var testJob: Job? = null

	@Inject
	@BaseHttpClient
	lateinit var okHttpClient: OkHttpClient

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		addPreferencesFromResource(R.xml.pref_proxy)
		@Suppress("UsePropertyAccessSyntax")
		findPreference<EditTextPreference>(AppSettings.KEY_PROXY_ADDRESS)?.setOnBindEditTextListener(
			EditTextBindListener(
				inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_VARIATION_URI,
				hint = null,
				validator = DomainValidator(),
			),
		)
		@Suppress("UsePropertyAccessSyntax")
		findPreference<EditTextPreference>(AppSettings.KEY_PROXY_PORT)?.setOnBindEditTextListener(
			EditTextBindListener(
				inputType = EditorInfo.TYPE_CLASS_NUMBER,
				hint = null,
				validator = PortNumberValidator(),
			),
		)
		findPreference<EditTextPreference>(AppSettings.KEY_PROXY_SECRET)?.let { pref ->
			@Suppress("UsePropertyAccessSyntax")
			pref.setOnBindEditTextListener(
				EditTextBindListener(
					inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_VARIATION_PASSWORD,
					hint = null,
					validator = HexSecretValidator(),
				),
			)
			pref.summaryProvider = PasswordSummaryProvider()
		}
		findPreference<EditTextPreference>(AppSettings.KEY_PROXY_PASSWORD)?.let { pref ->
			@Suppress("UsePropertyAccessSyntax")
			pref.setOnBindEditTextListener(
				EditTextBindListener(
					inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_VARIATION_PASSWORD,
					hint = null,
					validator = null,
				),
			)
			pref.summaryProvider = PasswordSummaryProvider()
		}
		updateDependencies()
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		settings.subscribe(this)
	}

	override fun onDestroyView() {
		settings.unsubscribe(this)
		super.onDestroyView()
	}

	override fun onPreferenceTreeClick(preference: Preference): Boolean = when (preference.key) {
		AppSettings.KEY_PROXY_TEST -> {
			testConnection()
			true
		}

		else -> super.onPreferenceTreeClick(preference)
	}

	override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
		when (key) {
			AppSettings.KEY_PROXY_TYPE -> {
				updateDependencies()
				testConnectionIfConfigured()
			}

			AppSettings.KEY_PROXY_ADDRESS,
			AppSettings.KEY_PROXY_PORT,
			-> testConnectionIfConfigured()
		}
	}

	private fun updateDependencies() {
		val type = settings.proxyType
		val isProxyEnabled = type != ProxyType.DIRECT
		findPreference<Preference>(AppSettings.KEY_PROXY_ADDRESS)?.isEnabled = isProxyEnabled
		findPreference<Preference>(AppSettings.KEY_PROXY_PORT)?.isEnabled = isProxyEnabled
		// MTProto uses a secret instead of the login/password pair (see ProxyType docs),
		// so the auth category is simply hidden for it.
		findPreference<Preference>(AppSettings.KEY_PROXY_SECRET)?.isVisible = type == ProxyType.MTPROTO
		findPreference<PreferenceCategory>(AppSettings.KEY_PROXY_AUTH)?.run {
			isVisible = type != ProxyType.MTPROTO
			isEnabled = isProxyEnabled
		}
		findPreference<Preference>(AppSettings.KEY_PROXY_LOGIN)?.isEnabled = isProxyEnabled
		findPreference<Preference>(AppSettings.KEY_PROXY_PASSWORD)?.isEnabled = isProxyEnabled
		findPreference<Preference>(AppSettings.KEY_PROXY_TEST)?.isEnabled = isProxyEnabled && testJob?.isActive != true
	}

	/**
	 * Test the connection through the just-saved proxy configuration, so an invalid
	 * configuration is reported to the user immediately (before leaving the screen).
	 */
	private fun testConnectionIfConfigured() {
		val type = settings.proxyType
		if (type == ProxyType.DIRECT) {
			return
		}
		// MTProto cannot be tested: it is not supported by the network stack (see ProxyType)
		if (type == ProxyType.MTPROTO) {
			showMessage(getString(R.string.proxy_mtproto_not_supported))
			return
		}
		if (settings.proxyAddress.isNullOrEmpty() || settings.proxyPort == 0) {
			return
		}
		testConnection()
	}

	private fun testConnection() {
		if (settings.proxyType == ProxyType.MTPROTO) {
			// Not supported by the network stack, a direct connection would give
			// a false positive. Just inform the user.
			showMessage(getString(R.string.proxy_mtproto_not_supported))
			return
		}
		testJob?.cancel()
		testJob = viewLifecycleScope.launch {
			val pref = findPreference<Preference>(AppSettings.KEY_PROXY_TEST)
			pref?.run {
				setSummary(R.string.loading_)
				isEnabled = false
			}
			try {
				withContext(Dispatchers.Default) {
					val request = Request.Builder()
						.get()
						.url("http://neverssl.com")
						.build()
					okHttpClient.newCall(request).await().use { response ->
						check(response.isSuccessful) { response.message }
					}
				}
				showTestResult(null)
			} catch (e: CancellationException) {
				throw e
			} catch (e: Throwable) {
				e.printStackTraceDebug()
				showTestResult(e)
			} finally {
				pref?.run {
					isEnabled = true
					summary = null
				}
			}
		}
	}

	private fun showTestResult(error: Throwable?) {
		MaterialAlertDialogBuilder(requireContext())
			.setTitle(R.string.proxy)
			.setMessage(error?.getDisplayMessage(resources) ?: getString(R.string.connection_ok))
			.setPositiveButton(android.R.string.ok, null)
			.setCancelable(true)
			.show()
	}

	private fun showMessage(message: CharSequence) {
		MaterialAlertDialogBuilder(requireContext())
			.setTitle(R.string.proxy)
			.setMessage(message)
			.setPositiveButton(android.R.string.ok, null)
			.setCancelable(true)
			.show()
	}
}
