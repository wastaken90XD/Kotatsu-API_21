package org.wastaken.kotatsu.api21.settings.sources.cookies.model

import okhttp3.Cookie
import org.wastaken.kotatsu.api21.list.ui.model.ListModel

data class CookieListModel(
	val name: String,
	val value: String,
	val domain: String,
	val path: String,
	val expiresAt: Long,
	val isPersistent: Boolean,
	val isSecure: Boolean,
	val isHttpOnly: Boolean,
) : ListModel {

	constructor(cookie: Cookie) : this(
		name = cookie.name,
		value = cookie.value,
		domain = cookie.domain,
		path = cookie.path,
		expiresAt = cookie.expiresAt,
		isPersistent = cookie.persistent,
		isSecure = cookie.secure,
		isHttpOnly = cookie.httpOnly,
	)

	fun matches(cookie: Cookie): Boolean = cookie.name == name && cookie.value == value

	override fun areItemsTheSame(other: ListModel): Boolean {
		return other is CookieListModel && other.name == name && other.value == value
	}
}
