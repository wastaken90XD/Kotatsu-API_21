package org.wastaken.kotatsu.api21.core.prefs

import android.os.Build
import androidx.annotation.Keep
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import com.google.android.material.color.DynamicColors
import org.wastaken.kotatsu.api21.R
import org.koitharu.kotatsu.parsers.util.find

@Keep
enum class ColorScheme(
	@StyleRes val styleResId: Int,
	@StringRes val titleResId: Int,
) {

	DEFAULT(R.style.ThemeOverlay_Kotatsu_Primavera, R.string.theme_name_primavera),
	TOTORO(R.style.ThemeOverlay_Kotatsu_Totoro, R.string.theme_name_totoro),
	MONET(R.style.ThemeOverlay_Kotatsu_Monet, R.string.theme_name_dynamic),
	EXPRESSIVE(R.style.ThemeOverlay_Kotatsu_Expressive, R.string.theme_name_expressive),
	MIKU(R.style.ThemeOverlay_Kotatsu_Miku, R.string.theme_name_miku),
	RENA(R.style.ThemeOverlay_Kotatsu_Asuka, R.string.theme_name_asuka),
	FROG(R.style.ThemeOverlay_Kotatsu_Mion, R.string.theme_name_mion),
	BLUEBERRY(R.style.ThemeOverlay_Kotatsu_Rikka, R.string.theme_name_rikka),
	SAKURA(R.style.ThemeOverlay_Kotatsu_Sakura, R.string.theme_name_sakura),
	MAMIMI(R.style.ThemeOverlay_Kotatsu_Mamimi, R.string.theme_name_mamimi),
	KANADE(R.style.ThemeOverlay_Kotatsu_Kanade, R.string.theme_name_kanade),
	ITSUKA(R.style.ThemeOverlay_Kotatsu_Itsuka, R.string.theme_name_itsuka),
	;

	/**
	 * Schemes based on dynamic (wallpaper-derived) colors. They only render
	 * correctly when the platform supports dynamic color.
	 */
	val isDynamic: Boolean
		get() = this == MONET || this == EXPRESSIVE

	/**
	 * Returns this scheme if it can be rendered on this device, or [default] otherwise.
	 * Protects against a stored scheme that the device cannot render (e.g. after
	 * restoring a settings backup from a newer device): applying a dynamic
	 * scheme there would silently use the Material dynamic fallback palette.
	 */
	fun availableOrDefault(): ColorScheme {
		return when {
			// This fork targets API 21+: on Lollipop the scheme picker is hidden
			// and only the default Primavera palette is guaranteed to render
			// correctly, so ignore any stored (e.g. backup-restored) scheme there.
			Build.VERSION.SDK_INT < Build.VERSION_CODES.M -> default
			isDynamic && !DynamicColors.isDynamicColorAvailable() -> default
			else -> this
		}
	}

	companion object {

		/**
		 * The out-of-the-box color scheme of the app.
		 * Primavera (the DEFAULT entry) is used regardless of dynamic color support,
		 * so the app has a consistent brand palette on every device.
		 * Dynamic schemes (MONET/EXPRESSIVE) remain selectable in settings.
		 */
		val default: ColorScheme
			get() = DEFAULT

		fun getAvailableList(): List<ColorScheme> {
			val list = ColorScheme.entries.toMutableList()
			if (!DynamicColors.isDynamicColorAvailable()) {
				list.remove(MONET)
				list.remove(EXPRESSIVE)
			}
			return list
		}

		fun safeValueOf(name: String): ColorScheme? {
			return ColorScheme.entries.find(name)
		}
	}
}
