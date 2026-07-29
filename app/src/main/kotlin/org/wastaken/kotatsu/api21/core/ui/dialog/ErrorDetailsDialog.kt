package org.wastaken.kotatsu.api21.core.ui.dialog

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import org.wastaken.kotatsu.api21.R
import org.wastaken.kotatsu.api21.core.github.AppUpdateRepository
import org.wastaken.kotatsu.api21.core.nav.AppRouter
import org.wastaken.kotatsu.api21.core.nav.router
import org.wastaken.kotatsu.api21.core.ui.AlertDialogFragment
import org.wastaken.kotatsu.api21.core.util.ext.copyToClipboard
import org.wastaken.kotatsu.api21.core.util.ext.getCauseUrl
import org.wastaken.kotatsu.api21.core.util.ext.isHttpUrl
import org.wastaken.kotatsu.api21.core.util.ext.requireSerializable
import org.wastaken.kotatsu.api21.core.util.ext.setTextAndVisible
import org.wastaken.kotatsu.api21.databinding.DialogErrorDetailsBinding
import org.koitharu.kotatsu.parsers.exception.toCrashReport
import javax.inject.Inject

@AndroidEntryPoint
class ErrorDetailsDialog : AlertDialogFragment<DialogErrorDetailsBinding>(), View.OnClickListener {

	private lateinit var exception: Throwable

	@Inject
	lateinit var appUpdateRepository: AppUpdateRepository

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		val args = requireArguments()
		exception = args.requireSerializable(AppRouter.KEY_ERROR)
	}

	override fun onCreateViewBinding(inflater: LayoutInflater, container: ViewGroup?): DialogErrorDetailsBinding {
		return DialogErrorDetailsBinding.inflate(inflater, container, false)
	}

	override fun onViewBindingCreated(binding: DialogErrorDetailsBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		binding.buttonBrowser.setOnClickListener(this)
		binding.textViewSummary.text = exception.message
		val isUrlAvailable = exception.getCauseUrl()?.isHttpUrl() == true
		binding.buttonBrowser.isVisible = isUrlAvailable
		binding.textViewBrowser.isVisible = isUrlAvailable
		binding.textViewDescription.setTextAndVisible(
			if (appUpdateRepository.isUpdateAvailable) {
				R.string.error_disclaimer_app_outdated
			} else {
				0
			},
		)
	}

	@Suppress("NAME_SHADOWING")
	override fun onBuildDialog(builder: MaterialAlertDialogBuilder): MaterialAlertDialogBuilder {
		val builder = super.onBuildDialog(builder)
			.setCancelable(true)
			.setNegativeButton(R.string.close, null)
			.setTitle(R.string.error_details)
			.setNeutralButton(R.string.copy) { _, _ ->
				context?.copyToClipboard(getString(R.string.error), exception.toCrashReport())
			}
		if (appUpdateRepository.isUpdateAvailable) {
			builder.setPositiveButton(R.string.update) { _, _ ->
				router.openAppUpdate()
				dismiss()
			}
		} else {
			builder.setPositiveButton(R.string.share) { _, _ ->
				val sendIntent = Intent(Intent.ACTION_SEND).apply {
					type = "text/plain"
					putExtra(Intent.EXTRA_TEXT, exception.toCrashReport())
					putExtra(Intent.EXTRA_SUBJECT, getString(R.string.error_details))
				}
				startActivity(Intent.createChooser(sendIntent, getString(R.string.crash_report_share)))
				dismiss()
			}
		}
		return builder
	}

	override fun onClick(v: View) {
		router.openBrowser(
			url = exception.getCauseUrl() ?: return,
			source = null,
			title = null,
		)
	}
}
