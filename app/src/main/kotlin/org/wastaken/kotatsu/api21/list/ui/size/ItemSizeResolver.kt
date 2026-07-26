package org.wastaken.kotatsu.api21.list.ui.size

import android.view.View
import android.widget.TextView
import org.wastaken.kotatsu.api21.history.ui.util.ReadingProgressView

interface ItemSizeResolver {

	val cellWidth: Int

	fun attachToView(
		view: View,
		textView: TextView?,
		progressView: ReadingProgressView?,
	)
}
