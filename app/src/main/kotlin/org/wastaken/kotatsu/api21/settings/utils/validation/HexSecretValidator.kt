package org.wastaken.kotatsu.api21.settings.utils.validation

import org.wastaken.kotatsu.api21.R
import org.wastaken.kotatsu.api21.core.util.EditTextValidator

/**
 * Validates an MTProto proxy secret: a hexadecimal string.
 * Real-world MTProto secrets are 32 hex chars (16 bytes), or longer when a fake-TLS
 * domain suffix is appended (`ee`-prefixed secrets), so we accept 32..64 hex chars.
 */
class HexSecretValidator : EditTextValidator() {

	override fun validate(text: String): ValidationResult {
		val trimmed = text.trim()
		if (trimmed.isEmpty()) {
			return ValidationResult.Success
		}
		val isValid = trimmed.length in 32..64 && trimmed.all { it in HEX_CHARS }
		return if (isValid) {
			ValidationResult.Success
		} else {
			ValidationResult.Failed(context.getString(R.string.invalid_proxy_secret))
		}
	}

	private companion object {

		const val HEX_CHARS = "0123456789abcdefABCDEF"
	}
}
