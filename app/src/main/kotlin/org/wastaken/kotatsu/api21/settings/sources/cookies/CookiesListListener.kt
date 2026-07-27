package org.wastaken.kotatsu.api21.settings.sources.cookies

import org.wastaken.kotatsu.api21.settings.sources.cookies.model.CookieListModel

interface CookiesListListener {

	fun onCookieClick(item: CookieListModel)

	fun onCookieDeleteClick(item: CookieListModel)
}
