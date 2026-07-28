package org.wastaken.kotatsu.api21.settings.about

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.annotation.StringRes
import androidx.fragment.app.viewModels
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import org.wastaken.kotatsu.api21.BuildConfig
import org.wastaken.kotatsu.api21.R
import org.wastaken.kotatsu.api21.core.github.AppVersion
import org.wastaken.kotatsu.api21.core.github.VersionId
import org.wastaken.kotatsu.api21.core.github.isStable
import org.wastaken.kotatsu.api21.core.nav.router
import org.wastaken.kotatsu.api21.core.prefs.AppSettings
import org.wastaken.kotatsu.api21.core.ui.BasePreferenceFragment
import org.wastaken.kotatsu.api21.core.util.ext.getThemeColor
import org.wastaken.kotatsu.api21.core.util.ext.observe
import org.wastaken.kotatsu.api21.core.util.ext.observeEvent
import com.google.android.material.R as materialR

@AndroidEntryPoint
class AboutSettingsFragment : BasePreferenceFragment(R.string.about) {

	private val viewModel by viewModels<AboutSettingsViewModel>()

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		addPreferencesFromResource(R.xml.pref_about)
		findPreference<Preference>(AppSettings.KEY_APP_VERSION)?.run {
			title = getString(R.string.app_version, BuildConfig.VERSION_NAME)
		}
		findPreference<SwitchPreferenceCompat>(AppSettings.KEY_UPDATES_UNSTABLE)?.run {
			isEnabled = VersionId(BuildConfig.VERSION_NAME).isStable
			if (!isEnabled) isChecked = true
		}
		if (BuildConfig.DEBUG) {
			addThemeDiagnostics()
		}
	}

	/**
	 * Temporary theme diagnostics (debug builds only): shows the raw stored
	 * color scheme, the effectively applied one, and the colors actually
	 * resolved by the activity theme, to debug device-specific theming issues.
	 */
	private fun addThemeDiagnostics() {
		val activity = activity ?: return
		val rawName = PreferenceManager.getDefaultSharedPreferences(activity)
			.getString(AppSettings.KEY_COLOR_THEME, null) ?: "<unset>"
		val effective = settings.colorScheme
		val primary = activity.getThemeColor(materialR.attr.colorPrimary, 0)
		val surface = activity.getThemeColor(materialR.attr.colorSurface, 0)
		val background = activity.getThemeColor(android.R.attr.colorBackground, 0)
		val pref = Preference(preferenceScreen.context)
		pref.title = "Theme diagnostics"
		pref.summary = buildString {
			append("sdk=").append(Build.VERSION.SDK_INT)
			append(", raw=").append(rawName)
			append(", effective=").append(effective.name)
			append("\nprimary=").append(hex(primary))
			append(", surface=").append(hex(surface))
			append(", background=").append(hex(background))
		}
		pref.isSelectable = false
		pref.isPersistent = false
		preferenceScreen.addPreference(pref)
	}

	private fun hex(color: Int): String = String.format("#%06X", color and 0xFFFFFF)

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		combine(viewModel.isUpdateSupported, viewModel.isLoading, ::Pair)
			.observe(viewLifecycleOwner) { (isUpdateSupported, isLoading) ->
				findPreference<Preference>(AppSettings.KEY_UPDATES_UNSTABLE)?.isVisible = isUpdateSupported
				findPreference<Preference>(AppSettings.KEY_APP_VERSION)?.isEnabled = isUpdateSupported && !isLoading

			}
		viewModel.onUpdateAvailable.observeEvent(viewLifecycleOwner, ::onUpdateAvailable)
	}

	override fun onPreferenceTreeClick(preference: Preference): Boolean {
		return when (preference.key) {
			AppSettings.KEY_APP_VERSION -> {
				viewModel.checkForUpdates()
				true
			}

			AppSettings.KEY_LINK_WEBLATE -> {
				openLink(R.string.url_weblate, preference.title)
				true
			}

			AppSettings.KEY_LINK_GITHUB -> {
				openLink(R.string.url_github, preference.title)
				true
			}

			AppSettings.KEY_LINK_MANUAL -> {
				openLink(R.string.url_user_manual, preference.title)
				true
			}

			AppSettings.KEY_LINK_TELEGRAM -> {
				if (!openLink(R.string.url_telegram, null)) {
					openLink(R.string.url_telegram_web, preference.title)
				}
				true
			}

			else -> super.onPreferenceTreeClick(preference)
		}
	}

	private fun onUpdateAvailable(version: AppVersion?) {
		if (version == null) {
			Snackbar.make(listView, R.string.no_update_available, Snackbar.LENGTH_SHORT).show()
		} else {
			startActivity(Intent(requireContext(), AppUpdateActivity::class.java))
		}
	}

	private fun openLink(
		@StringRes url: Int,
		title: CharSequence?
	): Boolean = if (router.openExternalBrowser(getString(url), title)) {
		true
	} else {
		Snackbar.make(listView, R.string.operation_not_supported, Snackbar.LENGTH_SHORT).show()
		false
	}
}
