package com.nodoomscroll.app.blocker

import com.nodoomscroll.app.data.BlockerSettings
import kotlin.math.abs

/**
 * Pure-Kotlin representation of an accessibility scroll event.
 */
data class ScrollEvent(
  val packageName: String,
  val deltaX: Int = 0,
  val deltaY: Int = 0,
  val fromIndex: Int = -1,
  val toIndex: Int = -1,
)

/**
 * Trigger emitted when continuous downward scrolling exceeds the active sensitivity preset.
 */
data class ScrollIntervention(
  val packageName: String,
  val swipeCount: Int,
  val sessionDurationMs: Long,
  val triggerReason: String,
)

/**
 * Snapshot of the currently tracked scroll streak for diagnostics.
 */
data class ScrollSessionSnapshot(
  val packageName: String,
  val swipeCount: Int,
  val sessionDurationMs: Long,
)

/**
 * Tracks continuous downward feed scrolling per foreground app with gesture debouncing,
 * idle resets, and per-package snooze windows.
 */
class ContinuousScrollTracker(
  private val gestureDebounceMs: Long = 650L,
  private val idleResetMs: Long = 20_000L,
  private val postTriggerCooldownMs: Long = 15_000L,
  private val defaultSnoozeMs: Long = 120_000L,
  private val clockMs: () -> Long = System::currentTimeMillis,
) {
  private var activePackage: String = ""
  private var sessionStartMs: Long = -1L
  private var lastGestureMs: Long = -1L
  private var swipeCount: Int = 0
  private val snoozedUntilByPackage = HashMap<String, Long>()

  @Synchronized
  fun onScroll(event: ScrollEvent, settings: BlockerSettings): ScrollIntervention? {
    if (!settings.continuousScrollGuardEnabled) return null
    if (settings.blockTikTok && event.packageName in TikTokRule.supportedPackages) return null

    if (event.deltaX != 0 && abs(event.deltaX) > abs(event.deltaY)) return null
    if (event.deltaY < 0) return null

    val now = clockMs()
    val snoozedUntil = snoozedUntilByPackage[event.packageName]
    if (snoozedUntil != null && now < snoozedUntil) return null

    if (
      event.packageName != activePackage ||
        (lastGestureMs >= 0L && now - lastGestureMs >= idleResetMs)
    ) {
      activePackage = event.packageName
      resetSession()
    }

    if (lastGestureMs >= 0L && now - lastGestureMs < gestureDebounceMs) return null

    if (swipeCount == 0 || sessionStartMs < 0L) {
      sessionStartMs = now
    }
    swipeCount++
    lastGestureMs = now
    val durationMs = (now - sessionStartMs).coerceAtLeast(0L)

    val sensitivity = settings.scrollGuardSensitivity
    val swipeHit = swipeCount >= sensitivity.swipeThreshold
    val durationHit =
      swipeCount >= sensitivity.minSwipesForDuration &&
        durationMs >= sensitivity.durationThresholdMs

    if (swipeHit || durationHit) {
      val intervention =
        ScrollIntervention(
          packageName = event.packageName,
          swipeCount = swipeCount,
          sessionDurationMs = durationMs,
          triggerReason = if (swipeHit) "swipe_count" else "sustained_duration",
        )
      resetSession()
      snoozedUntilByPackage[event.packageName] = now + postTriggerCooldownMs
      return intervention
    }

    return null
  }

  @Synchronized
  fun onForegroundPackageChanged(packageName: String) {
    if (packageName != activePackage) {
      activePackage = packageName
      resetSession()
    }
  }

  @Synchronized
  fun snooze(packageName: String, durationMs: Long = defaultSnoozeMs) {
    snoozedUntilByPackage[packageName] = clockMs() + durationMs
    if (activePackage == packageName) {
      resetSession()
    }
  }

  @Synchronized
  fun resetSession() {
    swipeCount = 0
    sessionStartMs = -1L
    lastGestureMs = -1L
  }

  @Synchronized
  fun currentSession(): ScrollSessionSnapshot {
    if (swipeCount == 0 || sessionStartMs < 0L) {
      return ScrollSessionSnapshot(packageName = activePackage, swipeCount = 0, sessionDurationMs = 0L)
    }
    val now = clockMs()
    if (lastGestureMs >= 0L && now - lastGestureMs >= idleResetMs) {
      return ScrollSessionSnapshot(packageName = activePackage, swipeCount = 0, sessionDurationMs = 0L)
    }
    return ScrollSessionSnapshot(
      packageName = activePackage,
      swipeCount = swipeCount,
      sessionDurationMs = (now - sessionStartMs).coerceAtLeast(0L),
    )
  }
}
