package org.wastaken.kotatsu.api21.booru.media

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.SurfaceTexture
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.view.GestureDetector
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.Toast
import org.wastaken.kotatsu.api21.R
import org.wastaken.kotatsu.api21.booru.media.ui.BooruPlayerActivity

/**
 * NewPipe-popup-style floating player: a small draggable video card added
 * straight to the WindowManager from [BooruMediaService] so it floats over
 * every screen (incl. other apps) while the foreground service lives.
 *
 * Window type: TYPE_PHONE on API 21-25, TYPE_APPLICATION_OVERLAY on 26+
 * (requires the SYSTEM_ALERT_WINDOW grant, checked on 23+ via canDrawOverlays;
 * 21/22 need no runtime prompt). Size/position come from settings presets.
 */
@SuppressLint("ClickableViewAccessibility")
class FloatingPlayerController(
	private val context: Context,
	private val service: BooruMediaService,
) {

	private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
	private var rootView: View? = null
	private var textureView: TextureView? = null
	private var gestureDetector: GestureDetector? = null
	private var dragging = false

	val isShowing: Boolean
		get() = rootView != null

	fun canShowOverlay(): Boolean =
		Build.VERSION.SDK_INT < Build.VERSION_CODES.M || AndroidSettings.canDrawOverlays(context)

	fun overlaySettingsIntent(): android.content.Intent = android.content.Intent(
		AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
		android.net.Uri.parse("package:${context.packageName}"),
	)

	fun show() {
		if (rootView != null) return
		if (!canShowOverlay()) return
		val layout = LayoutInflater.from(context).inflate(R.layout.layout_floating_player, null)
		val params = buildLayoutParams()
		val texture = layout.findViewById<TextureView>(R.id.floatTexture)
		val buttonPlay = layout.findViewById<ImageButton>(R.id.floatButtonPlay)
		val buttonClose = layout.findViewById<ImageButton>(R.id.floatButtonClose)
		val buttonExpand = layout.findViewById<ImageButton>(R.id.floatButtonExpand)

		texture.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
			override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
				service.bindSurface(Surface(surface))
			}

			override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit

			override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
				surface.release()
				service.bindSurface(null)
				return true
			}

			override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
		}
		val isLocked = service.settings.isMediaFloatingLock
		buttonPlay.visibility = if (isLocked) View.GONE else View.VISIBLE
		buttonClose.visibility = if (isLocked) View.GONE else View.VISIBLE
		buttonPlay.setOnClickListener { service.togglePlayPause(); syncPlayButton(buttonPlay) }
		buttonClose.setOnClickListener { service.stopPlaybackAndQueueClear() }
		buttonExpand.setOnClickListener { expandToFullScreen() }
		syncPlayButton(buttonPlay)
		installGesture(layout, texture)
		try {
			windowManager.addView(layout, params)
		} catch (e: WindowManager.BadTokenException) {
			// known Samsung API 21 edge: surface a clear message instead of crashing
			Toast.makeText(context, context.getString(R.string.media_floating_error, e.message), Toast.LENGTH_LONG).show()
			return
		} catch (e: Exception) {
			Toast.makeText(context, context.getString(R.string.media_floating_error, e.message), Toast.LENGTH_LONG).show()
			return
		}
		rootView = layout
		textureView = texture
	}

	fun hide() {
		val view = rootView ?: return
		service.bindSurface(null)
		runCatching { windowManager.removeView(view) }
		rootView = null
		textureView = null
	}

	fun syncPlayButton() {
		val view = rootView ?: return
		syncPlayButton(view.findViewById(R.id.floatButtonPlay))
	}

	private fun syncPlayButton(button: ImageButton) {
		button.setImageResource(if (service.isVideoPlaying()) R.drawable.ic_action_pause else R.drawable.ic_play)
	}

	private fun expandToFullScreen() {
		val item = service.currentItem
		val intent = if (item != null) {
			BooruPlayerActivity.newIntent(context, item).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
		} else {
			return
		}
		hide()
		context.startActivity(intent)
	}

	private fun buildLayoutParams(): WindowManager.LayoutParams {
		val (widthDp, heightDp) = when (service.settings.mediaFloatingSize) {
			FloatingWindowSize.SMALL -> 240 to 135
			FloatingWindowSize.MEDIUM -> 300 to 170
			FloatingWindowSize.LARGE -> 420 to 240
		}
		val density = context.resources.displayMetrics.density
		val gravity = when (service.settings.mediaFloatingPosition) {
			FloatingWindowPosition.TOP_RIGHT -> Gravity.TOP or Gravity.END
			FloatingWindowPosition.BOTTOM_RIGHT -> Gravity.BOTTOM or Gravity.END
			FloatingWindowPosition.BOTTOM_LEFT -> Gravity.BOTTOM or Gravity.START
		}
		val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
		} else {
			@Suppress("DEPRECATION")
			WindowManager.LayoutParams.TYPE_PHONE
		}
		val margin = (8 * density).toInt()
		return WindowManager.LayoutParams(
			(widthDp * density).toInt(),
			(heightDp * density).toInt(),
			type,
			WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
			PixelFormat.TRANSLUCENT,
		).apply {
			this.gravity = gravity
			x = margin
			y = margin
		}
	}

	/**
	 * Tap = expand, double-tap = play/pause, long-press = drag until release.
	 */
	private fun installGesture(layout: View, texture: View) {
		val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
			override fun onDown(e: MotionEvent): Boolean = true

			override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
				expandToFullScreen()
				return true
			}

			override fun onDoubleTap(e: MotionEvent): Boolean {
				service.togglePlayPause()
				syncPlayButton()
				return true
			}

			override fun onLongPress(e: MotionEvent) {
				dragging = true
			}
		})
		gestureDetector = detector
		var downX = 0f
		var downY = 0f
		val touchTarget: View = layout
		touchTarget.setOnTouchListener { _, event ->
			detector.onTouchEvent(event)
			val view = rootView ?: return@setOnTouchListener false
			val params = view.layoutParams as WindowManager.LayoutParams
			when (event.action) {
				MotionEvent.ACTION_DOWN -> {
					downX = event.rawX - params.x
					downY = event.rawY - params.y
				}
				MotionEvent.ACTION_MOVE -> if (dragging) {
					params.x = (event.rawX - downX).toInt()
					params.y = (event.rawY - downY).toInt()
					windowManager.updateViewLayout(view, params)
					true
				} else false
				MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
					dragging = false
					false
				}
				else -> false
			}
			true
		}
	}
}
