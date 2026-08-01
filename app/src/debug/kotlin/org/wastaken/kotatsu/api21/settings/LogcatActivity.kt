package org.wastaken.kotatsu.api21.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.wastaken.kotatsu.api21.R
import org.wastaken.kotatsu.api21.core.ui.BaseActivity
import org.wastaken.kotatsu.api21.core.util.ext.consumeAllSystemBarsInsets
import org.wastaken.kotatsu.api21.core.util.ext.systemBarsInsets
import org.wastaken.kotatsu.api21.databinding.ActivityCrashReportBinding
import org.wastaken.kotatsu.api21.core.util.ext.lifecycleScope

class LogcatActivity : BaseActivity<ActivityCrashReportBinding>() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(ActivityCrashReportBinding.inflate(layoutInflater))
		setDisplayHomeAsUp(isEnabled = true, showUpAsClose = true)

		viewBinding.toolbar.title = "Logcat"

		viewBinding.buttonClose.setOnClickListener { finish() }
		viewBinding.buttonClose.visibility = View.GONE

		lifecycleScope.launchWhenCreated {
			val log = withContext(Dispatchers.IO) {
				try {
					val process = Runtime.getRuntime().exec("logcat -d -v threadtime --pid=${android.os.Process.myPid()}")
					process.inputStream.bufferedReader().readText()
				} catch (e: Exception) {
					"Failed to capture logcat: \${e.message}"
				}
			}
			viewBinding.textViewReport.text = log
			viewBinding.scrollView.post {
				viewBinding.scrollView.fullScroll(View.FOCUS_DOWN)
			}

			viewBinding.buttonCopy.setOnClickListener {
				val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
				clipboard.setPrimaryClip(ClipData.newPlainText("Logcat", log))
				Snackbar.make(viewBinding.root, R.string.copied_to_clipboard, Snackbar.LENGTH_SHORT).show()
			}

			viewBinding.buttonShare.setOnClickListener {
				val intent = Intent(Intent.ACTION_SEND).apply {
					type = "text/plain"
					putExtra(Intent.EXTRA_TEXT, log)
				}
				startActivity(Intent.createChooser(intent, getString(R.string.share_logs)))
			}
		}
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val barsInsets = insets.systemBarsInsets
		viewBinding.root.updatePadding(
			left = barsInsets.left,
			right = barsInsets.right,
			bottom = barsInsets.bottom,
		)
		return insets.consumeAllSystemBarsInsets()
	}
}
