package org.wastaken.kotatsu.api21.browser.cloudflare

import org.wastaken.kotatsu.api21.browser.BrowserCallback

interface CloudFlareCallback : BrowserCallback {

	override fun onTitleChanged(title: CharSequence, subtitle: CharSequence?) = Unit

	fun onPageLoaded()

	fun onCheckPassed()

	fun onLoopDetected()
}
