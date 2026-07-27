package org.wastaken.kotatsu.api21.settings.sources.cookies

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.wastaken.kotatsu.api21.R
import org.wastaken.kotatsu.api21.core.model.MangaSource
import org.wastaken.kotatsu.api21.core.nav.AppRouter
import org.wastaken.kotatsu.api21.core.network.cookies.MutableCookieJar
import org.wastaken.kotatsu.api21.core.parser.MangaRepository
import org.wastaken.kotatsu.api21.core.parser.ParserMangaRepository
import org.wastaken.kotatsu.api21.core.ui.BaseViewModel
import org.wastaken.kotatsu.api21.core.ui.util.ReversibleAction
import org.wastaken.kotatsu.api21.core.util.ext.MutableEventFlow
import org.wastaken.kotatsu.api21.core.util.ext.call
import org.wastaken.kotatsu.api21.list.ui.model.EmptyState
import org.wastaken.kotatsu.api21.list.ui.model.ListModel
import org.wastaken.kotatsu.api21.settings.sources.cookies.model.CookieListModel
import org.koitharu.kotatsu.parsers.util.nullIfEmpty
import javax.inject.Inject

@HiltViewModel
class CookiesViewModel @Inject constructor(
	savedStateHandle: SavedStateHandle,
	mangaRepositoryFactory: MangaRepository.Factory,
	private val cookieJar: MutableCookieJar,
) : BaseViewModel() {

	val source = MangaSource(savedStateHandle.get<String>(AppRouter.KEY_SOURCE))
	private val repository = mangaRepositoryFactory.create(source)

	val content = MutableStateFlow<List<ListModel>?>(null)
	val onActionDone = MutableEventFlow<ReversibleAction>()

	val sourceDomain: String?
		get() = (repository as? ParserMangaRepository)?.domain

	/**
	 * Cookies are managed for the current domain of the source, same scope
	 * as the "Clear cookies" action in source settings.
	 */
	private val sourceUrl: HttpUrl?
		get() = sourceDomain?.let {
			runCatching { "https://$it/".toHttpUrl() }.getOrNull()
		}

	fun onResume() {
		loadCookies()
	}

	fun deleteCookie(item: CookieListModel) {
		val url = sourceUrl ?: return
		launchLoadingJob(Dispatchers.Default) {
			cookieJar.removeCookies(url) { item.matches(it) }
			loadCookiesInternal()
		}
	}

	fun clearAllCookies() {
		val url = sourceUrl ?: return
		launchLoadingJob(Dispatchers.Default) {
			cookieJar.removeCookies(url, null)
			onActionDone.call(ReversibleAction(R.string.cookies_cleared_source, null))
			loadCookiesInternal()
		}
	}

	/**
	 * Saves (inserts or replaces) a cookie. When [replaced] is not null the original
	 * cookie is removed first, so this effectively works as "edit value".
	 *
	 * Injected cookies get a long lifetime so the source can stay authorized
	 * across app restarts, which is the main use-case (manual login token injection).
	 */
	fun setCookie(name: String, value: String, domain: String?, replaced: CookieListModel?) {
		val url = sourceUrl ?: return
		val cleanName = sanitizeName(name)
		if (cleanName.isEmpty()) {
			return
		}
		launchLoadingJob(Dispatchers.Default) {
			if (replaced != null) {
				cookieJar.removeCookies(url) { replaced.matches(it) }
			}
			val cookieDomain = domain?.trim()?.removePrefix(".")?.nullIfEmpty()
			val raw = buildString {
				append(cleanName)
				append('=')
				append(sanitizeValue(value))
				if (cookieDomain != null) {
					append("; Domain=")
					append(cookieDomain)
				}
				append("; Path=/")
				append("; Max-Age=")
				append(PERSISTENT_MAX_AGE)
			}
			// A Set-Cookie value is only accepted when its Domain attribute
			// matches the request url, so insert against the requested domain
			// (e.g. to support sub-domains) when one is specified.
			val insertUrl = cookieDomain?.let {
				runCatching { "https://$it/".toHttpUrl() }.getOrNull()
			} ?: url
			cookieJar.insertCookie(insertUrl, raw)
			loadCookiesInternal()
		}
	}

	private fun loadCookies() {
		launchLoadingJob(Dispatchers.Default) {
			loadCookiesInternal()
		}
	}

	private suspend fun loadCookiesInternal() {
		val url = sourceUrl
		val cookies = if (url == null) {
			emptyList()
		} else {
			runCatching { cookieJar.getCookies(url) }.getOrDefault(emptyList())
		}
		content.value = if (cookies.isEmpty()) {
			listOf(
				EmptyState(
					icon = 0,
					textPrimary = R.string.no_cookies_stored,
					textSecondary = 0,
					actionStringRes = 0,
				),
			)
		} else {
			cookies.map { CookieListModel(it) }
		}
	}

	private companion object {

		// ~10 years: effectively "keep me signed in" for manually injected cookies
		const val PERSISTENT_MAX_AGE = 10 * 365 * 24 * 60 * 60

		// ';', '=' and CR/LF are never legal inside a cookie name
		fun sanitizeName(value: String) = value.trim().filterNot { it == ';' || it == '=' || it == '\n' || it == '\r' }

		// ';' and CR/LF would break the Set-Cookie header; '=' is legal in values (e.g. base64 padding)
		fun sanitizeValue(value: String) = value.trim().filterNot { it == ';' || it == '\n' || it == '\r' }
	}
}
