package com.nodoomscroll.app.blocker

/**
 * Supported short-video feeds or apps targeted by the blocker engine.
 */
enum class Platform {
  YOUTUBE_SHORTS,
  INSTAGRAM_REELS,
  TIKTOK,
  FACEBOOK_REELS,
  X_SHORTS,
}

/**
 * Global navigation action to execute when a rule matches.
 */
enum class BlockAction {
  NONE,
  NAVIGATE_BACK,
  NAVIGATE_HOME,
}

/**
 * Pure-Kotlin snapshot of the active window used to evaluate rules without Android framework types.
 */
data class ScreenSnapshot(
  val packageName: String,
  val visibleViewIds: Set<String> = emptySet(),
  val selectedTabHints: Set<String> = emptySet(),
  val windowClassName: String = "",
)

/**
 * Result of evaluating [DoomScrollRuleEngine] against a [ScreenSnapshot].
 */
data class BlockDecision(
  val action: BlockAction,
  val platform: Platform? = null,
  val reason: String = "",
)
