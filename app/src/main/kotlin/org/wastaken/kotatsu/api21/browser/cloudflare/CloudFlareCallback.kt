package org.wastaken.kotatsu.api21.browser.cloudflare

import org.wastaken.kotatsu.api21.browser.BrowserCallback

interface CloudFlareCallback : BrowserCallback {

	override fun onTitleChanged(title: CharSequence, subtitle: CharSequence?) = Unit

	fun onPageLoaded()

	fun onCheckPassed()

	fun onLoopDetected()

	/**
	 * Called when the challenge could not be solved after several full
	 * attempts. Implementers should surface this to the user and stop
	 * auto-reloading instead of looping indefinitely.
	 */
	fun onCheckFailed() = Unit
}
