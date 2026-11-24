package com.nodoomscroll.app.blocker

import com.nodoomscroll.app.data.BlockerSettings
import com.nodoomscroll.app.data.ScrollGuardSensitivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ContinuousScrollTrackerTest {

  private var fakeNowMs: Long = 0L
  private val tracker =
    ContinuousScrollTracker(
      gestureDebounceMs = 650L,
      idleResetMs = 20_000L,
      postTriggerCooldownMs = 15_000L,
      defaultSnoozeMs = 120_000L,
      clockMs = { fakeNowMs },
    )

  private fun settings(
    enabled: Boolean = true,
    sensitivity: ScrollGuardSensitivity = ScrollGuardSensitivity.BALANCED,
    blockTikTok: Boolean = true,
  ) =
    BlockerSettings(
      continuousScrollGuardEnabled = enabled,
      scrollGuardSensitivity = sensitivity,
      blockTikTok = blockTikTok,
    )

  @Test
  fun debouncesRapidScrollBurstIntoSingleGesture_evenStartingAtZeroMs() {
    val cfg = settings(sensitivity = ScrollGuardSensitivity.STRICT)
    val event = ScrollEvent(packageName = "com.instagram.android", deltaY = 400)

    assertNull(tracker.onScroll(event, cfg))
    assertEquals(1, tracker.currentSession().swipeCount)

    fakeNowMs = 200L
    assertNull(tracker.onScroll(event, cfg))
    fakeNowMs = 649L
    assertNull(tracker.onScroll(event, cfg))
    assertEquals(1, tracker.currentSession().swipeCount)

    fakeNowMs = 650L
    assertNull(tracker.onScroll(event, cfg))
    assertEquals(2, tracker.currentSession().swipeCount)
  }

  @Test
  fun filtersHorizontalAndUpwardScrolls() {
    val cfg = settings()
    assertNull(tracker.onScroll(ScrollEvent("com.facebook.katana", deltaX = 500, deltaY = 50), cfg))
    assertNull(tracker.onScroll(ScrollEvent("com.facebook.katana", deltaX = 0, deltaY = -300), cfg))
    assertEquals(0, tracker.currentSession().swipeCount)
  }

  @Test
  fun strictBalancedAndRelaxedSwipeThresholds_triggerAtExpectedCounts() {
    val pkg = "com.facebook.katana"

    val strictCfg = settings(sensitivity = ScrollGuardSensitivity.STRICT)
    for (i in 1 until ScrollGuardSensitivity.STRICT.swipeThreshold) {
      assertNull(tracker.onScroll(ScrollEvent(pkg, deltaY = 300), strictCfg))
      fakeNowMs += 1000L
    }
    val strictHit = tracker.onScroll(ScrollEvent(pkg, deltaY = 300), strictCfg)
    assertNotNull(strictHit)
    assertEquals("swipe_count", strictHit?.triggerReason)
    assertEquals(12, strictHit?.swipeCount)

    fakeNowMs += 20_000L
    val balancedCfg = settings(sensitivity = ScrollGuardSensitivity.BALANCED)
    for (i in 1 until ScrollGuardSensitivity.BALANCED.swipeThreshold) {
      assertNull(tracker.onScroll(ScrollEvent(pkg, deltaY = 300), balancedCfg))
      fakeNowMs += 1000L
    }
    val balancedHit = tracker.onScroll(ScrollEvent(pkg, deltaY = 300), balancedCfg)
    assertNotNull(balancedHit)
    assertEquals(20, balancedHit?.swipeCount)

    fakeNowMs += 20_000L
    val relaxedCfg = settings(sensitivity = ScrollGuardSensitivity.RELAXED)
    for (i in 1 until ScrollGuardSensitivity.RELAXED.swipeThreshold) {
      assertNull(tracker.onScroll(ScrollEvent(pkg, deltaY = 300), relaxedCfg))
      fakeNowMs += 1000L
    }
    val relaxedHit = tracker.onScroll(ScrollEvent(pkg, deltaY = 300), relaxedCfg)
    assertNotNull(relaxedHit)
    assertEquals(35, relaxedHit?.swipeCount)
  }

  @Test
  fun sustainedDuration_triggersWhenMinSwipesAndDurationMet() {
    val pkg = "com.google.android.youtube"
    val cfg = settings(sensitivity = ScrollGuardSensitivity.BALANCED)

    for (i in 1 until ScrollGuardSensitivity.BALANCED.minSwipesForDuration) {
      assertNull(tracker.onScroll(ScrollEvent(pkg, deltaY = 250), cfg))
      fakeNowMs += 13_000L
    }
    // At 7 * 13s = 91s, 8th swipe satisfies minSwipesForDuration (8) and durationThresholdMs (90s)
    val intervention = tracker.onScroll(ScrollEvent(pkg, deltaY = 250), cfg)
    assertNotNull(intervention)
    assertEquals("sustained_duration", intervention?.triggerReason)
    assertEquals(8, intervention?.swipeCount)
    assertEquals(91_000L, intervention?.sessionDurationMs)
  }

  @Test
  fun idleTimeoutAndPackageSwitch_resetSession() {
    val cfg = settings(sensitivity = ScrollGuardSensitivity.STRICT)
    repeat(5) {
      tracker.onScroll(ScrollEvent("com.instagram.android", deltaY = 200), cfg)
      fakeNowMs += 1000L
    }
    assertEquals(5, tracker.currentSession().swipeCount)

    // 20s idle resets session
    fakeNowMs += 20_000L
    assertEquals(0, tracker.currentSession().swipeCount)
    tracker.onScroll(ScrollEvent("com.instagram.android", deltaY = 200), cfg)
    assertEquals(1, tracker.currentSession().swipeCount)

    // Switching foreground package resets session
    tracker.onForegroundPackageChanged("com.facebook.katana")
    assertEquals(0, tracker.currentSession().swipeCount)
  }

  @Test
  fun snoozeAndPostTriggerCooldown_arePerPackage() {
    val cfg = settings(sensitivity = ScrollGuardSensitivity.STRICT)
    tracker.snooze("com.instagram.android")

    repeat(15) {
      assertNull(tracker.onScroll(ScrollEvent("com.instagram.android", deltaY = 300), cfg))
      fakeNowMs += 1000L
    }

    // Another app is NOT snoozed
    for (i in 1 until 12) {
      assertNull(tracker.onScroll(ScrollEvent("com.facebook.katana", deltaY = 300), cfg))
      fakeNowMs += 1000L
    }
    val fbHit = tracker.onScroll(ScrollEvent("com.facebook.katana", deltaY = 300), cfg)
    assertNotNull(fbHit)

    // Within 15s post-trigger cooldown, Facebook stays quiet
    fakeNowMs += 10_000L
    assertNull(tracker.onScroll(ScrollEvent("com.facebook.katana", deltaY = 300), cfg))

    // After 120s snooze expires, Instagram resumes tracking
    fakeNowMs = 120_000L
    tracker.onScroll(ScrollEvent("com.instagram.android", deltaY = 300), cfg)
    assertEquals(1, tracker.currentSession().swipeCount)
  }

  @Test
  fun disabledGuardOrBlockedTikTok_ignoresScrollEvents() {
    assertNull(
      tracker.onScroll(
        ScrollEvent("com.instagram.android", deltaY = 300),
        settings(enabled = false),
      ),
    )

    assertNull(
      tracker.onScroll(
        ScrollEvent("com.zhiliaoapp.musically", deltaY = 300),
        settings(enabled = true, blockTikTok = true),
      ),
    )

    tracker.onScroll(
      ScrollEvent("com.zhiliaoapp.musically", deltaY = 300),
      settings(enabled = true, blockTikTok = false),
    )
    assertEquals(1, tracker.currentSession().swipeCount)
  }
}
