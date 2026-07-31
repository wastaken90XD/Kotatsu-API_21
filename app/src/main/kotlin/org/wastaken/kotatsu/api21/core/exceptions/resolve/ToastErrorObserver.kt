package org.wastaken.kotatsu.api21.core.exceptions.resolve

import android.view.View
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import org.wastaken.kotatsu.api21.R
import org.wastaken.kotatsu.api21.core.util.ext.getDisplayMessage
import org.wastaken.kotatsu.api21.core.util.ext.isSerializable

class ToastErrorObserver(
	host: View,
	fragment: Fragment?,
) : ErrorObserver(host, fragment, null, null) {

	override suspend fun emit(value: Throwable) {
		val snackbar = Snackbar.make(host, value.getDisplayMessage(host.context.resources), Snackbar.LENGTH_LONG)
		val router = router()
		if (router != null && value.isSerializable()) {
			snackbar.setAction(R.string.details) {
				router.showErrorDialog(value)
			}
		}
		snackbar.show()
	}
}
