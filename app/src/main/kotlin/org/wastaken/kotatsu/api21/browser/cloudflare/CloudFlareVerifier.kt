package org.wastaken.kotatsu.api21.browser.cloudflare

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lightweight verification framework for the Cloudflare solving flow.
 *
 * Cloudflare binds its `cf_clearance` token to the IP address *and* the
 * User-Agent (plus TLS fingerprint) that were used when the challenge was
 * solved. If the WebView solves the challenge under one User-Agent but the
 * OkHttp fetches that follow use a different one, Cloudflare rejects the
 * cached token and re-issues the challenge — producing the infinite
 * verification loop.
 *
 * This manager keeps the exact User-Agent that successfully passed the
 * challenge for a given host, so:
 *  1. the solving WebView reuses the same User-Agent across attempts/sessions,
 *  2. the token cached in the cookie jar remains valid for the fetches.
 */
@Singleton
class CloudFlareVerifier @Inject constructor(
	@ApplicationContext private val context: Context,
) {

	private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

	/**
	 * Returns the last User-Agent that successfully passed a Cloudflare
	 * challenge for [host], or null if none was recorded yet.
	 */
	fun getSolvedUserAgent(host: String): String? = prefs.getString(uaKey(host), null)

	/**
	 * Records the User-Agent used to successfully pass a challenge for [host].
	 * Blank values are ignored.
	 */
	fun rememberSolvedUserAgent(host: String, userAgent: String) {
		if (host.isBlank() || userAgent.isBlank()) {
			return
		}
		prefs.edit(commit = true) {
			putString(uaKey(host), userAgent)
		}
	}

	private fun uaKey(host: String): String = KEY_PREFIX_UA + host.lowercase()

	private companion object {

		const val PREFS_NAME = "cloudflare_verification"
		const val KEY_PREFIX_UA = "solved_ua:"
	}
}
