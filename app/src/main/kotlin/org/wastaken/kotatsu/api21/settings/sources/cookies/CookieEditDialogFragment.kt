package org.wastaken.kotatsu.api21.settings.sources.cookies

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.wastaken.kotatsu.api21.R
import org.wastaken.kotatsu.api21.core.util.ext.withArgs
import org.wastaken.kotatsu.api21.databinding.DialogCookieEditBinding

/**
 * A dialog for adding a new cookie (manual injection) or editing an existing
 * cookie value. The result is delivered via the Fragment Result API: see [REQUEST_KEY].
 */
class CookieEditDialogFragment : DialogFragment() {

	private val isEditMode: Boolean
		get() = arguments?.getBoolean(ARG_EDIT) == true

	override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
		val binding = DialogCookieEditBinding.inflate(layoutInflater)
		binding.layoutName.isEnabled = !isEditMode
		if (!isEditMode) {
			binding.editDomain.setText(arguments?.getString(ARG_DEFAULT_DOMAIN).orEmpty())
			binding.editDomain.setOnFocusChangeListener { v, hasFocus ->
				if (hasFocus) {
					(v as? android.widget.EditText)?.selectAll()
				}
			}
		} else {
			binding.editName.setText(arguments?.getString(ARG_NAME))
			binding.editValue.setText(arguments?.getString(ARG_VALUE))
			binding.layoutDomain.isVisible = false
			binding.textViewHint.isVisible = false
		}
		return MaterialAlertDialogBuilder(requireContext())
			.setTitle(if (isEditMode) R.string.edit_cookie else R.string.add_cookie)
			.setView(binding.root)
			.setNegativeButton(android.R.string.cancel, null)
			// The actual save action is set in onStart to prevent automatic dismissal
			// when the input is invalid.
			.setPositiveButton(R.string.save, null)
			.create()
			.also { dialog ->
				dialog.setOnShowListener { onDialogShown(dialog, binding) }
			}
	}

	private fun onDialogShown(dialog: DialogInterface, binding: DialogCookieEditBinding) {
		(dialog as? AlertDialog)?.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
			val name = binding.editName.text?.toString()?.trim().orEmpty()
			if (name.isEmpty()) {
				binding.layoutName.error = getString(R.string.invalid_value_message)
				return@setOnClickListener
			}
			val domain = if (isEditMode) null else binding.editDomain.text?.toString()?.trim()
			if (!isEditMode && !isValidCookieDomain(domain)) {
				binding.layoutDomain.error = getString(R.string.invalid_domain_message)
				return@setOnClickListener
			}
			parentFragmentManager.setFragmentResult(
				REQUEST_KEY,
				bundleOf(
					ARG_NAME to name,
					ARG_VALUE to binding.editValue.text?.toString().orEmpty(),
					ARG_DEFAULT_DOMAIN to domain,
					ARG_EDIT to isEditMode,
					ARG_OLD_NAME to arguments?.getString(ARG_NAME).orEmpty(),
					ARG_OLD_VALUE to arguments?.getString(ARG_VALUE).orEmpty(),
				),
			)
			dialog.dismiss()
		}
	}

	private fun isValidCookieDomain(domain: String?): Boolean {
		if (domain.isNullOrEmpty()) {
			return true // host-only cookie is fine
		}
		val d = domain.removePrefix(".")
		return d.isNotEmpty() && d.none { it.isWhitespace() } &&
			d.count { it == '.' } >= 1 && d.all { it.isLetterOrDigit() || it == '.' || it == '-' }
	}

	companion object {

		const val REQUEST_KEY = "CookieEditDialogFragment"

		private const val ARG_EDIT = "edit"
		private const val ARG_NAME = "name"
		private const val ARG_VALUE = "value"
		private const val ARG_OLD_NAME = "old_name"
		private const val ARG_OLD_VALUE = "old_value"
		private const val ARG_DEFAULT_DOMAIN = "domain"

		fun showAdd(fm: FragmentManager, defaultDomain: String?) =
			CookieEditDialogFragment().withArgs(2) {
				putBoolean(ARG_EDIT, false)
				putString(ARG_DEFAULT_DOMAIN, defaultDomain)
			}.show(fm, REQUEST_KEY)

		fun showEdit(fm: FragmentManager, name: String, value: String) =
			CookieEditDialogFragment().withArgs(3) {
				putBoolean(ARG_EDIT, true)
				putString(ARG_NAME, name)
				putString(ARG_VALUE, value)
			}.show(fm, REQUEST_KEY)

		/** Result bundle helpers */
		fun getName(bundle: Bundle): String = bundle.getString(ARG_NAME).orEmpty()
		fun getValue(bundle: Bundle): String = bundle.getString(ARG_VALUE).orEmpty()
		fun getDomain(bundle: Bundle): String? = bundle.getString(ARG_DEFAULT_DOMAIN)
		fun isEdit(bundle: Bundle): Boolean = bundle.getBoolean(ARG_EDIT)
		fun getOldName(bundle: Bundle): String = bundle.getString(ARG_OLD_NAME).orEmpty()
		fun getOldValue(bundle: Bundle): String = bundle.getString(ARG_OLD_VALUE).orEmpty()
	}
}
