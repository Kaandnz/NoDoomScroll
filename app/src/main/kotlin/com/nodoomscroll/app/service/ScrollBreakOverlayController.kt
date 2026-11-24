package com.nodoomscroll.app.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Displays a mindful break overlay using [WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY]
 * without requiring the `SYSTEM_ALERT_WINDOW` permission.
 */
class ScrollBreakOverlayController(
  private val context: Context,
) {
  private val windowManager: WindowManager? =
    context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
  private val mainHandler = Handler(Looper.getMainLooper())

  private var overlayView: View? = null
  private var countdownRunnable: Runnable? = null

  @Volatile
  var targetPackage: String? = null
    private set

  val isShowing: Boolean
    get() = overlayView != null

  fun show(
    packageName: String,
    appName: String,
    swipeCount: Int,
    sessionDurationMs: Long,
    onGoHome: () -> Unit,
    onSnooze: () -> Unit,
  ): Boolean {
    val wm = windowManager ?: return false
    dismiss()

    val root =
      object : FrameLayout(context) {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
          if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            dismiss()
            onGoHome()
            return true
          }
          return super.dispatchKeyEvent(event)
        }
      }.apply {
        setBackgroundColor(COLOR_SCRIM)
        isClickable = true
        isFocusable = true
        isFocusableInTouchMode = true
        setPadding(dp(24), dp(24), dp(24), dp(24))
      }

    val card =
      LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background =
          GradientDrawable().apply {
            setColor(COLOR_CARD_BG)
            setStroke(dp(1), COLOR_CARD_BORDER)
            cornerRadius = dpF(24)
          }
        setPadding(dp(24), dp(24), dp(24), dp(24))
        isClickable = true
      }

    val badge =
      TextView(context).apply {
        text = "SÜREKLİ KAYDIRMA FRENİ"
        setTextColor(COLOR_ACCENT)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        setTypeface(typeface, Typeface.BOLD)
        letterSpacing = 0.08f
        background =
          GradientDrawable().apply {
            setColor(COLOR_BADGE_BG)
            cornerRadius = dpF(100)
          }
        setPadding(dp(12), dp(6), dp(12), dp(6))
      }

    val badgeParams =
      LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
      )
    card.addView(badge, badgeParams)

    val titleView =
      TextView(context).apply {
        text = "Kısa bir nefes molası"
        setTextColor(COLOR_TEXT_PRIMARY)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        setTypeface(typeface, Typeface.BOLD)
      }
    card.addView(
      titleView,
      LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
      ).apply { topMargin = dp(16) },
    )

    val durationText = formatDurationTurkish(sessionDurationMs)
    val bodyView =
      TextView(context).apply {
        text =
          "$appName akışında arka arkaya $swipeCount kez kaydırdın ($durationText). " +
          "Gerçekten aradığın bir şeye mi bakıyorsun?"
        setTextColor(COLOR_TEXT_SECONDARY)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setLineSpacing(dpF(4), 1f)
      }
    card.addView(
      bodyView,
      LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
      ).apply { topMargin = dp(10) },
    )

    val goHomeButton =
      Button(context).apply {
        text = "Ana Ekrana Dön"
        isAllCaps = false
        stateListAnimator = null
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setTypeface(typeface, Typeface.BOLD)
        minHeight = dp(48)
        background =
          GradientDrawable().apply {
            setColor(COLOR_PRIMARY_BUTTON)
            cornerRadius = dpF(14)
          }
        setOnClickListener {
          dismiss()
          onGoHome()
        }
      }
    card.addView(
      goHomeButton,
      LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
      ).apply { topMargin = dp(22) },
    )

    val snoozeDrawable =
      GradientDrawable().apply {
        setColor(COLOR_SECONDARY_BUTTON_BG)
        setStroke(dp(1), COLOR_CARD_BORDER)
        cornerRadius = dpF(14)
      }
    val snoozeButton =
      Button(context).apply {
        isAllCaps = false
        stateListAnimator = null
        isEnabled = false
        alpha = 0.65f
        setTextColor(COLOR_TEXT_MUTED)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setTypeface(typeface, Typeface.BOLD)
        minHeight = dp(46)
        background = snoozeDrawable
        text = "2 dk daha izin ver ($SNOOZE_COUNTDOWN_SECONDS sn)"
        setOnClickListener {
          dismiss()
          onSnooze()
        }
      }
    card.addView(
      snoozeButton,
      LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
      ).apply { topMargin = dp(10) },
    )

    root.addView(
      card,
      FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.WRAP_CONTENT,
        Gravity.CENTER,
      ),
    )

    val layoutParams =
      WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
      ).apply {
        gravity = Gravity.CENTER
      }

    return try {
      wm.addView(root, layoutParams)
      overlayView = root
      targetPackage = packageName
      root.requestFocus()
      startSnoozeCountdown(snoozeButton)
      true
    } catch (ex: RuntimeException) {
      Log.w(DoomScrollAccessibilityService.TAG, "Failed to display break overlay: ${ex.message}")
      overlayView = null
      targetPackage = null
      false
    }
  }

  fun dismiss() {
    countdownRunnable?.let { mainHandler.removeCallbacks(it) }
    countdownRunnable = null
    val view = overlayView
    overlayView = null
    targetPackage = null
    if (view != null) {
      try {
        windowManager?.removeView(view)
      } catch (_: RuntimeException) {
        // View was already detached from WindowManager.
      }
    }
  }

  private fun startSnoozeCountdown(snoozeButton: Button) {
    var remainingSeconds = SNOOZE_COUNTDOWN_SECONDS
    val runnable =
      object : Runnable {
        override fun run() {
          if (overlayView == null) return
          remainingSeconds--
          if (remainingSeconds <= 0) {
            snoozeButton.isEnabled = true
            snoozeButton.alpha = 1f
            snoozeButton.setTextColor(COLOR_TEXT_PRIMARY)
            snoozeButton.text = "2 dk daha izin ver"
            countdownRunnable = null
          } else {
            snoozeButton.text = "2 dk daha izin ver ($remainingSeconds sn)"
            mainHandler.postDelayed(this, 1000L)
          }
        }
      }
    countdownRunnable = runnable
    mainHandler.postDelayed(runnable, 1000L)
  }

  private fun dp(value: Int): Int =
    TypedValue.applyDimension(
      TypedValue.COMPLEX_UNIT_DIP,
      value.toFloat(),
      context.resources.displayMetrics,
    ).toInt()

  private fun dpF(value: Int): Float =
    TypedValue.applyDimension(
      TypedValue.COMPLEX_UNIT_DIP,
      value.toFloat(),
      context.resources.displayMetrics,
    )

  companion object {
    private const val SNOOZE_COUNTDOWN_SECONDS = 5
    private const val COLOR_SCRIM = 0xD9080E14.toInt()
    private const val COLOR_CARD_BG = 0xFF141D24.toInt()
    private const val COLOR_CARD_BORDER = 0xFF2B3E4D.toInt()
    private const val COLOR_BADGE_BG = 0xFF0E2938.toInt()
    private const val COLOR_ACCENT = 0xFF87CFF9.toInt()
    private const val COLOR_TEXT_PRIMARY = 0xFFF2F6FA.toInt()
    private const val COLOR_TEXT_SECONDARY = 0xFFC5D2DC.toInt()
    private const val COLOR_TEXT_MUTED = 0xFF9FB0BE.toInt()
    private const val COLOR_PRIMARY_BUTTON = 0xFF005C7D.toInt()
    private const val COLOR_SECONDARY_BUTTON_BG = 0xFF1E2933.toInt()

    fun formatDurationTurkish(durationMs: Long): String {
      val totalSeconds = (durationMs / 1000L).coerceAtLeast(1L)
      if (totalSeconds < 60L) {
        return "$totalSeconds sn"
      }
      val minutes = totalSeconds / 60L
      val seconds = totalSeconds % 60L
      return if (seconds == 0L) {
        "$minutes dk"
      } else {
        "$minutes dk $seconds sn"
      }
    }
  }
}
