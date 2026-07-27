package org.wastaken.kotatsu.api21.core.network.proxy

/**
 * Proxy types supported by the app.
 *
 * Implementation notes:
 *  - [HTTPS] is mapped to [java.net.Proxy.Type.HTTP] by OkHttp: the Java HTTP proxy
 *    implementation cannot establish a TLS (encrypted) channel to the proxy itself,
 *    it only tunnels via CONNECT. This is what an "https proxy" means in most setups
 *    (HTTPS traffic through an HTTP proxy), so it works for https:// targets.
 *    The WebView (Chromium), on the other hand, natively supports TLS proxies,
 *    so the `https://` scheme is used in WebView proxy rules — see [ProxyProvider].
 *  - [SOCKS4]/[SOCKS5] both map to [java.net.Proxy.Type.SOCKS]; the protocol version
 *    is selected via the `socksProxyVersion` system property, which is a JVM-wide hint
 *    honored by the JDK SOCKS implementation. On runtimes without SOCKSv4 support
 *    the negotiation silently falls back to the best available version.
 *  - [MTPROTO] proxies (Telegram) cannot be implemented within OkHttp or the platform
 *    network stack: MTProto requires its own obfuscated transport handshake on top of
 *    a raw TCP connection, which is neither an HTTP CONNECT proxy nor SOCKS.
 *    A full implementation would require a local SOCKS-to-MTProto bridge, which is out
 *    of scope for the network stack. The configuration (host, port, secret) is still
 *    accepted and stored, and connections are made directly in the meantime.
 *    This limitation is documented in [ProxyProvider] and in the app string
 *    `proxy_mtproto_not_supported`.
 */
enum class ProxyType {

	DIRECT,
	HTTP,
	HTTPS,
	SOCKS4,
	SOCKS5,
	MTPROTO,
	;

	val isSocks: Boolean
		get() = this == SOCKS4 || this == SOCKS5

	companion object {

		/**
		 * Parses the raw preference value, transparently migrating values stored
		 * by older app versions (enum names of [java.net.Proxy.Type]).
		 */
		fun of(raw: String?): ProxyType = when (raw) {
			null -> DIRECT
			"SOCKS" -> SOCKS5 // legacy value: single "SOCKS (v4/v5)" option, v5 was the default
			else -> entries.find { it.name == raw } ?: DIRECT
		}
	}
}
