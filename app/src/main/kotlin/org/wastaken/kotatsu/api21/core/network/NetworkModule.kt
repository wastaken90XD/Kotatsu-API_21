package org.wastaken.kotatsu.api21.core.network

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Cache
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import org.wastaken.kotatsu.api21.BuildConfig
import org.wastaken.kotatsu.api21.core.network.cookies.AndroidCookieJar
import org.wastaken.kotatsu.api21.core.network.cookies.MutableCookieJar
import org.wastaken.kotatsu.api21.core.network.cookies.PreferencesCookieJar
import org.wastaken.kotatsu.api21.core.network.imageproxy.ImageProxyInterceptor
import org.wastaken.kotatsu.api21.core.network.imageproxy.RealImageProxyInterceptor
import org.wastaken.kotatsu.api21.core.network.proxy.ProxyProvider
import org.wastaken.kotatsu.api21.core.prefs.AppSettings
import org.wastaken.kotatsu.api21.core.util.ext.assertNotInMainThread
import org.wastaken.kotatsu.api21.core.util.ext.printStackTraceDebug
import org.wastaken.kotatsu.api21.local.data.LocalStorageManager
import java.util.concurrent.TimeUnit
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface NetworkModule {

	@Binds
	fun bindCookieJar(androidCookieJar: MutableCookieJar): CookieJar

	@Binds
	fun bindImageProxyInterceptor(impl: RealImageProxyInterceptor): ImageProxyInterceptor

	companion object {

		@Provides
		@Singleton
		fun provideCookieJar(
			@ApplicationContext context: Context
		): MutableCookieJar = runCatching {
			AndroidCookieJar()
		}.getOrElse { e ->
			e.printStackTraceDebug()
			// WebView is not available
			PreferencesCookieJar(context)
		}

		@Provides
		@Singleton
		fun provideHttpCache(
			localStorageManager: LocalStorageManager,
		): Cache = localStorageManager.createHttpCache()

		@Provides
		@Singleton
		@BaseHttpClient
		fun provideBaseHttpClient(
			@ApplicationContext contextProvider: Provider<Context>,
			cache: Cache,
			cookieJar: CookieJar,
			settings: AppSettings,
			proxyProvider: ProxyProvider,
		): OkHttpClient = OkHttpClient.Builder().apply {
			assertNotInMainThread()
			connectTimeout(20, TimeUnit.SECONDS)
			readTimeout(60, TimeUnit.SECONDS)
			writeTimeout(20, TimeUnit.SECONDS)
			cookieJar(cookieJar)
			proxySelector(proxyProvider.selector)
			proxyAuthenticator(proxyProvider.authenticator)
			dns(DoHManager(cache, settings))
			if (settings.isSSLBypassEnabled) {
				disableCertificateVerification()
			} else {
				installExtraCertificates(contextProvider.get())
			}
			cache(cache)
			addInterceptor(GZipInterceptor())
			addInterceptor(CloudFlareInterceptor())
			addInterceptor(RateLimitInterceptor())
			if (BuildConfig.DEBUG) {
				addInterceptor(CurlLoggingInterceptor())
			}
		}.build()

		@Provides
		@Singleton
		@MangaHttpClient
		fun provideMangaHttpClient(
			@BaseHttpClient baseClient: OkHttpClient,
			commonHeadersInterceptor: CommonHeadersInterceptor,
		): OkHttpClient = baseClient.newBuilder().apply {
			addNetworkInterceptor(CacheLimitInterceptor())
			addInterceptor(commonHeadersInterceptor)
		}.build()

	}
}
