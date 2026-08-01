package org.wastaken.kotatsu.api21.browser.cloudflare

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.view.isInvisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.yield
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.wastaken.kotatsu.api21.R
import org.wastaken.kotatsu.api21.browser.BaseBrowserActivity
import org.wastaken.kotatsu.api21.core.exceptions.CloudFlareProtectedException
import org.wastaken.kotatsu.api21.core.exceptions.resolve.CaptchaHandler
import org.wastaken.kotatsu.api21.core.model.MangaSource
import org.wastaken.kotatsu.api21.core.nav.AppRouter
import org.wastaken.kotatsu.api21.core.network.cookies.MutableCookieJar
import org.wastaken.kotatsu.api21.core.parser.ParserMangaRepository
import org.wastaken.kotatsu.api21.core.util.ext.getDisplayMessage
import org.wastaken.kotatsu.api21.core.util.ext.printStackTraceDebug
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.network.CloudFlareHelper
import org.koitharu.kotatsu.parsers.util.ifNullOrEmpty
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import javax.inject.Inject

@AndroidEntryPoint
class CloudFlareActivity : BaseBrowserActivity(), CloudFlareCallback {

	private var pendingResult = RESULT_CANCELED

	@Inject
	lateinit var cookieJar: MutableCookieJar

	@Inject
	lateinit var captchaHandler: CaptchaHandler

	@Inject
	lateinit var cloudFlareVerifier: CloudFlareVerifier

	private lateinit var cfClient: CloudFlareClient

	override fun onCreate2(savedInstanceState: Bundle?, source: MangaSource, repository: ParserMangaRepository?) {
		setDisplayHomeAsUp(isEnabled = true, showUpAsClose = true)
		val url = intent?.dataString
		if (url.isNullOrEmpty()) {
			finishAfterTransition()
			return
		}
		// Reuse the last User-Agent that successfully passed a challenge for
		// this host, so the cached cf_clearance token stays valid. This also
		// keeps the solving UA stable across attempts.
		url.toHttpUrlOrNull()?.host?.let { host ->
			cloudFlareVerifier.getSolvedUserAgent(host)?.let { solvedUa ->
				viewBinding.webView.settings.userAgentString = solvedUa
			}
		}
		// The challenge page must render unmodified: any ad-blocking that
		// alters the DOM or blocks scripts breaks Cloudflare's challenge JS
		// and causes the verification loop. So the solver runs without it.
		cfClient = CloudFlareClient(cookieJar, this, adBlock = null, targetUrl = url)
		viewBinding.webView.webViewClient = cfClient
		lifecycleScope.launch {
			try {
				proxyProvider.applyWebViewConfig()
			} catch (e: Exception) {
				Snackbar.make(viewBinding.webView, e.getDisplayMessage(resources), Snackbar.LENGTH_LONG).show()
			}
			if (savedInstanceState == null) {
				onTitleChanged(getString(R.string.loading_), url)
				viewBinding.webView.loadUrl(url)
			}
		}
	}

	override fun onCreateOptionsMenu(menu: Menu?): Boolean {
		menuInflater.inflate(R.menu.opt_captcha, menu)
		return super.onCreateOptionsMenu(menu)
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
		android.R.id.home -> {
			viewBinding.webView.stopLoading()
			finishAfterTransition()
			true
		}

		R.id.action_retry -> {
			// Manual retry clears the cached CF cookies to start fresh.
			reloadCheck(clearCookies = true)
			true
		}

		else -> super.onOptionsItemSelected(item)
	}

	override fun finish() {
		setResult(pendingResult)
		super.finish()
	}

	override fun onLoadingStateChanged(isLoading: Boolean) = Unit

	override fun onPageLoaded() {
		viewBinding.progressBar.isInvisible = true
	}

	override fun onLoopDetected() {
		// An intermediate failure: reload WITHOUT clearing the cached CF
		// cookies, since the token may still be valid once the challenge
		// actually completes.
		reloadCheck(clearCookies = false)
	}

	override fun onCheckFailed() {
		// The challenge could not be solved after several attempts (e.g. an
		// IP/ASN block or strict bot detection). Stop auto-reloading and
		// tell the user instead of looping forever.
		viewBinding.webView.stopLoading()
		Snackbar.make(
			viewBinding.webView,
			R.string.cloudflare_verification_failed,
			Snackbar.LENGTH_LONG,
		).show()
	}

	override fun onCheckPassed() {
		pendingResult = RESULT_OK
		// Persist the exact UA that passed the challenge for this host, so the
		// cf_clearance token in the cookie jar stays valid for the OkHttp
		// fetches and for future verification attempts.
		runCatching {
			intent?.dataString?.toHttpUrlOrNull()?.host?.let { host ->
				cloudFlareVerifier.rememberSolvedUserAgent(host, viewBinding.webView.settings.userAgentString)
			}
		}
		lifecycleScope.launch {
			val source = intent?.getStringExtra(AppRouter.KEY_SOURCE)
			if (source != null) {
				runCatchingCancellable {
					captchaHandler.discard(MangaSource(source))
				}.onFailure {
					it.printStackTraceDebug()
				}
			}
			finishAfterTransition()
		}
	}

	override fun onTitleChanged(title: CharSequence, subtitle: CharSequence?) {
		setTitle(title)
		supportActionBar?.subtitle = subtitle?.toString()?.toHttpUrlOrNull()?.host.ifNullOrEmpty { subtitle }
	}

	private fun reloadCheck(clearCookies: Boolean) {
		lifecycleScope.launch {
			viewBinding.webView.stopLoading()
			yield()
			cfClient.reset()
			val targetUrl = intent?.dataString?.toHttpUrlOrNull()
			if (targetUrl != null) {
				if (clearCookies) {
					clearCfCookies(targetUrl)
				}
				viewBinding.webView.loadUrl(targetUrl.toString())
			}
		}
	}

	private suspend fun clearCfCookies(url: HttpUrl) = runInterruptible(Dispatchers.Default) {
		cookieJar.removeCookies(url) { cookie ->
			CloudFlareHelper.isCloudFlareCookie(cookie.name)
		}
	}

	class Contract : ActivityResultContract<CloudFlareProtectedException, Boolean>() {
		override fun createIntent(context: Context, input: CloudFlareProtectedException): Intent {
			return AppRouter.cloudFlareResolveIntent(context, input)
		}

		override fun parseResult(resultCode: Int, intent: Intent?): Boolean {
			return resultCode == RESULT_OK
		}
	}

	companion object {

		const val TAG = "CloudFlareActivity"
	}
}
