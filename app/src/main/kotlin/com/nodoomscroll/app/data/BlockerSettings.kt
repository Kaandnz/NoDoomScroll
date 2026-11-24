package com.nodoomscroll.app.data

/**
 * Sensitivity presets for continuous downward scroll detection across watched feeds.
 */
enum class ScrollGuardSensitivity(
  val swipeThreshold: Int,
  val durationThresholdMs: Long,
  val minSwipesForDuration: Int,
) {
  STRICT(swipeThreshold = 12, durationThresholdMs = 45_000L, minSwipesForDuration = 6),
  BALANCED(swipeThreshold = 20, durationThresholdMs = 90_000L, minSwipesForDuration = 8),
  RELAXED(swipeThreshold = 35, durationThresholdMs = 180_000L, minSwipesForDuration = 10),
}

/**
 * Action taken when a continuous scrolling streak exceeds the selected threshold.
 */
enum class ScrollGuardAction {
  BREAK_OVERLAY,
  NAVIGATE_HOME,
}

/**
 * Persisted toggle configuration for the blocker service and debug calibration.
 */
data class BlockerSettings(
  val blockYouTubeShorts: Boolean = true,
  val blockInstagramReels: Boolean = true,
  val blockTikTok: Boolean = true,
  val blockFacebookReels: Boolean = true,
  val blockXShorts: Boolean = true,
  val debugLoggingEnabled: Boolean = false,
  val continuousScrollGuardEnabled: Boolean = true,
  val scrollGuardSensitivity: ScrollGuardSensitivity = ScrollGuardSensitivity.BALANCED,
  val scrollGuardAction: ScrollGuardAction = ScrollGuardAction.BREAK_OVERLAY,
)
