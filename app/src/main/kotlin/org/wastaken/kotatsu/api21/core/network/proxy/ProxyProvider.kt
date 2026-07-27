package org.wastaken.kotatsu.api21.core.network.proxy

import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import okio.IOException
import org.wastaken.kotatsu.api21.core.exceptions.ProxyConfigException
import org.wastaken.kotatsu.api21.core.network.CommonHeaders
import org.wastaken.kotatsu.api21.core.prefs.AppSettings
import org.wastaken.kotatsu.api21.core.util.ext.printStackTraceDebug
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import java.net.Authenticator as JavaAuthenticator

@Singleton
class ProxyProvider @Inject constructor(
	private val settings: AppSettings,
) {

	private var cachedProxy: Proxy? = null

	val selector = object : ProxySelector() {
		override fun select(uri: URI?): List<Proxy> {
			return listOf(getProxy())
		}

		override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
			ioe?.printStackTraceDebug()
		}
	}

	val authenticator = ProxyAuthenticator()

	init {
		ProxySelector.setDefault(selector)
		JavaAuthenticator.setDefault(authenticator)
	}

	suspend fun applyWebViewConfig() {
		val type = settings.proxyType
		if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
			if (type != ProxyType.DIRECT) {
				throw IllegalArgumentException("Proxy for WebView is not supported") // TODO localize
			}
			return
		}
		val controller = ProxyController.getInstance()
		// MTPROTO is not supported by the app network stack (see ProxyType docs);
		// behave as DIRECT for WebView as well.
		if (type == ProxyType.DIRECT || type == ProxyType.MTPROTO) {
			suspendCoroutine { cont ->
				controller.clearProxyOverride(
					(cont.context[CoroutineDispatcher] ?: Dispatchers.Main).asExecutor(),
				) {
					cont.resume(Unit)
				}
			}
			return
		}
		val scheme = when (type) {
			ProxyType.HTTP -> "http"
			ProxyType.HTTPS -> "https" // WebView (Chromium) does support TLS proxies
			ProxyType.SOCKS4 -> "socks4"
			ProxyType.SOCKS5 -> "socks5"
			// DIRECT and MTPROTO are handled above
			else -> throw ProxyConfigException()
		}
		val url = buildString {
			append(scheme)
			append("://")
			append(settings.proxyAddress)
			append(':')
			append(settings.proxyPort)
		}
		if (type.isSocks) {
			settings.proxyLogin?.let { System.setProperty("java.net.socks.username", it) }
			settings.proxyPassword?.let { System.setProperty("java.net.socks.password", it) }
		}
		val proxyConfig = ProxyConfig.Builder()
			.addProxyRule(url)
			.build()
		suspendCoroutine { cont ->
			controller.setProxyOverride(
				proxyConfig,
				(cont.context[CoroutineDispatcher] ?: Dispatchers.Main).asExecutor(),
			) {
				cont.resume(Unit)
			}
		}
	}

	private fun isProxyEnabled(): Boolean {
		val type = settings.proxyType
		// MTPROTO is stored but not actually applied (see ProxyType docs)
		return type != ProxyType.DIRECT && type != ProxyType.MTPROTO
	}

	private fun getProxy(): Proxy {
		val type = settings.proxyType
		if (type == ProxyType.DIRECT) {
			return Proxy.NO_PROXY
		}
		// MTProto proxies require the Telegram obfuscated transport, which cannot be
		// implemented within OkHttp/java.net (it is neither an HTTP CONNECT proxy nor
		// a SOCKS proxy). Configuration is accepted and stored for future use,
		// but connections are made directly for now.
		if (type == ProxyType.MTPROTO) {
			return Proxy.NO_PROXY
		}
		val address = settings.proxyAddress
		val port = settings.proxyPort
		if (address.isNullOrEmpty() || port < 0 || port > 0xFFFF) {
			throw ProxyConfigException()
		}
		val javaType = when (type) {
			// Both HTTP and HTTPS are served by the HTTP proxy implementation:
			// OkHttp cannot open a TLS channel to the proxy itself (see ProxyType docs).
			ProxyType.HTTP, ProxyType.HTTPS -> Proxy.Type.HTTP
			ProxyType.SOCKS4 -> {
				// JVM-wide hint honored by the JDK SOCKS implementation.
				// Runtimes without SOCKSv4 support simply negotiate SOCKS5.
				System.setProperty("socksProxyVersion", "4")
				Proxy.Type.SOCKS
			}

			ProxyType.SOCKS5 -> {
				System.setProperty("socksProxyVersion", "5")
				Proxy.Type.SOCKS
			}

			else -> throw ProxyConfigException() // unreachable: DIRECT/MTPROTO handled above
		}
		cachedProxy?.let {
			val addr = it.address() as? InetSocketAddress
			if (addr != null && it.type() == javaType && addr.port == port && addr.hostString == address) {
				return it
			}
		}
		val proxy = Proxy(javaType, InetSocketAddress(address, port))
		cachedProxy = proxy
		return proxy
	}

	inner class ProxyAuthenticator : Authenticator, JavaAuthenticator() {

		override fun authenticate(route: Route?, response: Response): Request? {
			if (!isProxyEnabled()) {
				return null
			}
			if (response.request.header(CommonHeaders.PROXY_AUTHORIZATION) != null) {
				return null
			}
			val login = settings.proxyLogin ?: return null
			val password = settings.proxyPassword ?: return null
			val credential = Credentials.basic(login, password)
			return response.request.newBuilder()
				.header(CommonHeaders.PROXY_AUTHORIZATION, credential)
				.build()
		}

		public override fun getPasswordAuthentication(): PasswordAuthentication? {
			if (!isProxyEnabled()) {
				return null
			}
			val login = settings.proxyLogin ?: return null
			val password = settings.proxyPassword ?: return null
			return PasswordAuthentication(login, password.toCharArray())
		}
	}
}
