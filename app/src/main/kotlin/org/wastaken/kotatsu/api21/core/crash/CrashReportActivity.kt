package org.wastaken.kotatsu.api21.core.crash

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import dagger.hilt.android.AndroidEntryPoint
import org.wastaken.kotatsu.api21.R
import org.wastaken.kotatsu.api21.core.ui.BaseActivity
import org.wastaken.kotatsu.api21.databinding.ActivityCrashReportBinding

@AndroidEntryPoint
class CrashReportActivity : BaseActivity<ActivityCrashReportBinding>() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(ActivityCrashReportBinding.inflate(layoutInflater))

		val report = intent.getStringExtra(EXTRA_REPORT)
		if (report.isNullOrBlank()) {
			finish()
			return
		}

		viewBinding.toolbar.title = getString(R.string.crash_report_title)
		setDisplayHomeAsUp(true, true)
		viewBinding.toolbar.setNavigationOnClickListener { finish() }
		viewBinding.textViewReport.text = report

		viewBinding.buttonCopy.setOnClickListener {
			copyToClipboard(report)
		}
		viewBinding.buttonShare.setOnClickListener {
			shareReport(report)
		}
		viewBinding.buttonClose.setOnClickListener {
			finish()
		}
	}

	private fun copyToClipboard(report: String) {
		val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
		clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.crash_report_title), report))
		Toast.makeText(this, R.string.crash_report_copied, Toast.LENGTH_SHORT).show()
	}

	private fun shareReport(report: String) {
		val sendIntent = Intent(Intent.ACTION_SEND).apply {
			type = "text/plain"
			putExtra(Intent.EXTRA_TEXT, report)
			putExtra(Intent.EXTRA_SUBJECT, getString(R.string.crash_report_title))
		}
		startActivity(Intent.createChooser(sendIntent, getString(R.string.crash_report_share)))
	}

	override fun onApplyWindowInsets(
		v: android.view.View,
		insets: androidx.core.view.WindowInsetsCompat,
	): androidx.core.view.WindowInsetsCompat = insets

	companion object {
		const val EXTRA_REPORT = "report"
	}
}
