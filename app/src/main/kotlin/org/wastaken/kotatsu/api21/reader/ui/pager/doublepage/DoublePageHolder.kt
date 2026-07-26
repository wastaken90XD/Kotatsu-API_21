package org.wastaken.kotatsu.api21.reader.ui.pager.doublepage

import android.graphics.PointF
import android.view.Gravity
import android.widget.FrameLayout
import androidx.lifecycle.LifecycleOwner
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import org.wastaken.kotatsu.api21.core.exceptions.resolve.ExceptionResolver
import org.wastaken.kotatsu.api21.core.os.NetworkState
import org.wastaken.kotatsu.api21.databinding.ItemPageBinding
import org.wastaken.kotatsu.api21.reader.domain.PageLoader
import org.wastaken.kotatsu.api21.reader.ui.config.ReaderSettings
import org.wastaken.kotatsu.api21.reader.ui.pager.ReaderPage
import org.wastaken.kotatsu.api21.reader.ui.pager.standard.PageHolder

class DoublePageHolder(
	owner: LifecycleOwner,
	binding: ItemPageBinding,
	loader: PageLoader,
	readerSettingsProducer: ReaderSettings.Producer,
	networkState: NetworkState,
	exceptionResolver: ExceptionResolver,
) : PageHolder(
	owner = owner,
	binding = binding,
	loader = loader,
	readerSettingsProducer = readerSettingsProducer,
	networkState = networkState,
	exceptionResolver = exceptionResolver,
) {

	private val isEven: Boolean
		get() = bindingAdapterPosition and 1 == 0

	init {
		binding.ssiv.panLimit = SubsamplingScaleImageView.PAN_LIMIT_INSIDE
	}

	override fun onBind(data: ReaderPage) {
		super.onBind(data)
		(binding.textViewNumber.layoutParams as FrameLayout.LayoutParams)
			.gravity = (if (isEven) Gravity.START else Gravity.END) or Gravity.BOTTOM
	}

	override fun onReady() {
		with(binding.ssiv) {
			maxScale = 2f * maxOf(
				width / sWidth.toFloat(),
				height / sHeight.toFloat(),
			)
			binding.ssiv.colorFilter = settings.colorFilter?.toColorFilter()
			minimumScaleType = SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE
			setScaleAndCenter(
				minScale,
				PointF(if (isEven) 0f else sWidth.toFloat(), sHeight / 2f),
			)
		}
	}
}
