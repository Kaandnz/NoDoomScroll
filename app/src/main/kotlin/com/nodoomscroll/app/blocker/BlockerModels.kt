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
 * Screen-space rectangle bounds for an observed accessibility node.
 */
data class NodeBounds(
  val left: Int,
  val top: Int,
  val right: Int,
  val bottom: Int,
) {
  val width: Int
    get() = (right - left).coerceAtLeast(0)

  val height: Int
    get() = (bottom - top).coerceAtLeast(0)

  val centerX: Int
    get() = left + width / 2

  val centerY: Int
    get() = top + height / 2

  val isValid: Boolean
    get() = right > left && bottom > top
}

/**
 * Pure-Kotlin projection of a visible accessibility node for structural analysis.
 */
data class ObservedNode(
  val text: String = "",
  val contentDescription: String = "",
  val className: String = "",
  val viewId: String = "",
  val isSelected: Boolean = false,
  val isClickable: Boolean = false,
  val isScrollable: Boolean = false,
  val bounds: NodeBounds = NodeBounds(0, 0, 0, 0),
)

/**
 * Structural and semantic signals extracted from a Facebook window snapshot.
 */
data class FacebookViewerSignals(
  val hasMainFeedTabBar: Boolean = false,
  val hasTopLeftBackButton: Boolean = false,
  val hasProfileSubTabs: Boolean = false,
  val hasVerticalRightActionRail: Boolean = false,
  val explicitReelViewerLabels: Set<String> = emptySet(),
  val unconditionalExplicitReelLabels: Set<String> = emptySet(),
  val supportingReelLabels: Set<String> = emptySet(),
  val hasPlayerOrEngagementControl: Boolean = false,
  val recentFeedReelClick: Boolean = false,
)

/**
 * Pure-Kotlin snapshot of the active window used to evaluate rules without Android framework types.
 */
data class ScreenSnapshot(
  val packageName: String,
  val visibleViewIds: Set<String> = emptySet(),
  val selectedTabHints: Set<String> = emptySet(),
  val windowClassName: String = "",
  val facebookViewerSignals: FacebookViewerSignals = FacebookViewerSignals(),
)

/**
 * Result of evaluating [DoomScrollRuleEngine] against a [ScreenSnapshot].
 */
data class BlockDecision(
  val action: BlockAction,
  val platform: Platform? = null,
  val reason: String = "",
)
