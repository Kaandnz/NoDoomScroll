package com.nodoomscroll.app.blocker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayEventGuardTest {

  private val ownPackage = "com.nodoomscroll.app"
  private val watched =
    setOf(
      "com.google.android.youtube",
      "com.instagram.android",
      "com.facebook.katana",
      "com.twitter.android",
    )

  @Test
  fun neverDismissesOnOwnOverlayWindowOrMomentumScrollOrContentEvents() {
    assertFalse(
      OverlayEventGuard.shouldDismissOverlay(
        isWindowStateChanged = true,
        eventPackage = ownPackage,
        ownPackage = ownPackage,
        targetPackage = "com.instagram.android",
        watchedPackages = watched,
        elapsedShownMs = 2_000L,
      ),
    )

    assertFalse(
      OverlayEventGuard.shouldDismissOverlay(
        isWindowStateChanged = false,
        eventPackage = "com.facebook.katana",
        ownPackage = ownPackage,
        targetPackage = "com.instagram.android",
        watchedPackages = watched,
        elapsedShownMs = 2_000L,
      ),
    )

    assertFalse(
      OverlayEventGuard.shouldDismissOverlay(
        isWindowStateChanged = true,
        eventPackage = "com.instagram.android",
        ownPackage = ownPackage,
        targetPackage = "com.instagram.android",
        watchedPackages = watched,
        elapsedShownMs = 2_000L,
      ),
    )
  }

  @Test
  fun respectsGraceWindowAndIgnoresNonWatchedSystemWindows() {
    assertFalse(
      OverlayEventGuard.shouldDismissOverlay(
        isWindowStateChanged = true,
        eventPackage = "com.facebook.katana",
        ownPackage = ownPackage,
        targetPackage = "com.instagram.android",
        watchedPackages = watched,
        elapsedShownMs = 599L,
      ),
    )

    assertFalse(
      OverlayEventGuard.shouldDismissOverlay(
        isWindowStateChanged = true,
        eventPackage = "com.android.systemui",
        ownPackage = ownPackage,
        targetPackage = "com.instagram.android",
        watchedPackages = watched,
        elapsedShownMs = 1_500L,
      ),
    )

    assertTrue(
      OverlayEventGuard.shouldDismissOverlay(
        isWindowStateChanged = true,
        eventPackage = "com.facebook.katana",
        ownPackage = ownPackage,
        targetPackage = "com.instagram.android",
        watchedPackages = watched,
        elapsedShownMs = 600L,
      ),
    )
  }
}
