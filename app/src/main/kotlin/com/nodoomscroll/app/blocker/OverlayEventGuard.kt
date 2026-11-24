package com.nodoomscroll.app.blocker

/**
 * Pure-Kotlin guard deciding whether an incoming accessibility event represents a genuine switch
 * away from the app covered by the continuous-scroll break overlay.
 */
object OverlayEventGuard {
  const val SWITCH_DISMISS_GRACE_MS: Long = 600L

  fun shouldDismissOverlay(
    isWindowStateChanged: Boolean,
    eventPackage: String?,
    ownPackage: String,
    targetPackage: String?,
    watchedPackages: Set<String>,
    elapsedShownMs: Long,
  ): Boolean =
    isWindowStateChanged &&
      eventPackage != null &&
      eventPackage != ownPackage &&
      eventPackage != targetPackage &&
      eventPackage in watchedPackages &&
      elapsedShownMs >= SWITCH_DISMISS_GRACE_MS
}
