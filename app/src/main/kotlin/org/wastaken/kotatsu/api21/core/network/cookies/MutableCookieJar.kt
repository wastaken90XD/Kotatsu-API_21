package org.wastaken.kotatsu.api21.core.network.cookies

import androidx.annotation.WorkerThread
import androidx.core.util.Predicate
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

interface MutableCookieJar : CookieJar {

	@WorkerThread
	override fun loadForRequest(url: HttpUrl): List<Cookie>

	@WorkerThread
	override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>)

	@WorkerThread
	fun removeCookies(url: HttpUrl, predicate: Predicate<Cookie>?)

	/**
	 * Returns all cookies that would be sent during a request to [url].
	 * Used by the cookies management UI. Defaults to [loadForRequest], i.e.
	 * only non-expired cookies visible from the given url are returned.
	 *
	 * Note: the implementation may not know the original attributes (domain, path,
	 * expiration) of each cookie — e.g. [android.webkit.CookieManager] does not
	 * expose them — so the returned [Cookie] objects may contain default attribute
	 * values in such cases.
	 */
	@WorkerThread
	fun getCookies(url: HttpUrl): List<Cookie> = loadForRequest(url)

	/**
	 * Inserts a single cookie described by a Set-Cookie header value
	 * (e.g. `name=value; Domain=example.com; Path=/; Max-Age=86400`).
	 * Used for manual cookie injection and for editing cookie values.
	 * Unknown/invalid cookies are silently ignored.
	 */
	@WorkerThread
	fun insertCookie(url: HttpUrl, rawSetCookie: String)

	suspend fun clear(): Boolean
}
