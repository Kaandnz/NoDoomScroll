package com.nodoomscroll.app.blocker

import com.nodoomscroll.app.data.BlockerSettings

/**
 * Contract for a single platform-specific short-video detection rule.
 */
interface BlockRule {
  val platform: Platform
  val supportedPackages: Set<String>
  val candidateViewIds: Set<String>
  val requiresTreeScan: Boolean
    get() = false

  fun isEnabled(settings: BlockerSettings): Boolean

  fun evaluate(snapshot: ScreenSnapshot): BlockDecision?
}

/**
 * Blocks YouTube Shorts player views while leaving normal videos and feeds untouched.
 */
object YouTubeShortsRule : BlockRule {
  override val platform: Platform = Platform.YOUTUBE_SHORTS
  override val supportedPackages: Set<String> = setOf("com.google.android.youtube")
  override val candidateViewIds: Set<String> =
    setOf(
      "com.google.android.youtube:id/reel_recycler",
      "com.google.android.youtube:id/reel_player_page_container",
      "com.google.android.youtube:id/reel_watch_fragment_root",
      "com.google.android.youtube:id/reel_watch_player",
      "com.google.android.youtube:id/shorts_player_view_pager",
      "com.google.android.youtube:id/reel_progress_bar",
    )

  override fun isEnabled(settings: BlockerSettings): Boolean = settings.blockYouTubeShorts

  override fun evaluate(snapshot: ScreenSnapshot): BlockDecision? {
    if (snapshot.packageName !in supportedPackages) return null
    val matchedId = candidateViewIds.firstOrNull { it in snapshot.visibleViewIds } ?: return null
    return BlockDecision(
      action = BlockAction.NAVIGATE_BACK,
      platform = platform,
      reason = "viewId:$matchedId",
    )
  }
}

/**
 * Blocks Instagram Reels player views and selected Reels tab without blocking Home feed or DMs.
 */
object InstagramReelsRule : BlockRule {
  const val CLIPS_TAB_ID: String = "com.instagram.android:id/clips_tab"
  const val CLIPS_TAB_SELECTED_HINT: String = "clips_tab_selected"

  override val platform: Platform = Platform.INSTAGRAM_REELS
  override val supportedPackages: Set<String> = setOf("com.instagram.android")
  override val candidateViewIds: Set<String> =
    setOf(
      "com.instagram.android:id/clips_viewer_view_pager",
      "com.instagram.android:id/clips_video_container",
      "com.instagram.android:id/clips_viewer_root",
      "com.instagram.android:id/clips_swipe_refresh_container",
      "com.instagram.android:id/clips_ufi_component",
    )

  override fun isEnabled(settings: BlockerSettings): Boolean = settings.blockInstagramReels

  override fun evaluate(snapshot: ScreenSnapshot): BlockDecision? {
    if (snapshot.packageName !in supportedPackages) return null
    val matchedId = candidateViewIds.firstOrNull { it in snapshot.visibleViewIds }
    if (matchedId != null) {
      return BlockDecision(
        action = BlockAction.NAVIGATE_BACK,
        platform = platform,
        reason = "viewId:$matchedId",
      )
    }
    if (CLIPS_TAB_SELECTED_HINT in snapshot.selectedTabHints) {
      return BlockDecision(
        action = BlockAction.NAVIGATE_BACK,
        platform = platform,
        reason = "tab:$CLIPS_TAB_SELECTED_HINT",
      )
    }
    return null
  }
}

/**
 * Blocks TikTok whenever its package enters the foreground by navigating back to Home.
 */
object TikTokRule : BlockRule {
  override val platform: Platform = Platform.TIKTOK
  override val supportedPackages: Set<String> =
    setOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")
  override val candidateViewIds: Set<String> = emptySet()

  override fun isEnabled(settings: BlockerSettings): Boolean = settings.blockTikTok

  override fun evaluate(snapshot: ScreenSnapshot): BlockDecision? {
    if (snapshot.packageName !in supportedPackages) return null
    return BlockDecision(
      action = BlockAction.NAVIGATE_HOME,
      platform = platform,
      reason = "package:${snapshot.packageName}",
    )
  }
}

/**
 * Blocks Facebook Reels/Shorts viewers without blocking News Feed posts or home Reels shelves.
 */
object FacebookReelsRule : BlockRule {
  const val REELS_TAB_SELECTED_HINT: String = "facebook_reels_tab_selected"

  override val platform: Platform = Platform.FACEBOOK_REELS
  override val supportedPackages: Set<String> =
    setOf("com.facebook.katana", "com.facebook.lite")
  override val requiresTreeScan: Boolean = true
  override val candidateViewIds: Set<String> =
    setOf(
      "com.facebook.katana:id/fb_shorts_viewer_container",
      "com.facebook.katana:id/fb_shorts_video_view",
      "com.facebook.katana:id/fb_shorts_pager",
      "com.facebook.katana:id/reels_viewer_container",
      "com.facebook.katana:id/reels_player_container",
    )

  private val viewIdKeywords: List<String> =
    listOf("fb_shorts_viewer", "fb_shorts_pager", "fb_shorts_player", "reels_viewer", "reels_player")
  private val excludedFeedKeywords: List<String> = listOf("tofu", "shelf", "tray", "unit")
  private val windowClassKeywords: List<String> = listOf("fbshortsviewer", "reelsviewer")

  override fun isEnabled(settings: BlockerSettings): Boolean = settings.blockFacebookReels

  override fun evaluate(snapshot: ScreenSnapshot): BlockDecision? =
    evaluateViewerMatch(
      snapshot = snapshot,
      platform = platform,
      supportedPackages = supportedPackages,
      candidateViewIds = candidateViewIds,
      viewIdKeywords = viewIdKeywords,
      excludedViewIdKeywords = excludedFeedKeywords,
      windowClassKeywords = windowClassKeywords,
      selectedTabHint = REELS_TAB_SELECTED_HINT,
    )
}

/**
 * Blocks X (Twitter) immersive/vertical video viewers without blocking normal timeline posts.
 */
object XShortsRule : BlockRule {
  const val VIDEO_TAB_SELECTED_HINT: String = "x_video_tab_selected"

  override val platform: Platform = Platform.X_SHORTS
  override val supportedPackages: Set<String> = setOf("com.twitter.android")
  override val requiresTreeScan: Boolean = true
  override val candidateViewIds: Set<String> =
    setOf(
      "ImmersiveVideo",
      "VideoTab",
      "com.twitter.android:id/immersive_video_container",
      "com.twitter.android:id/explore_immersive_pager",
      "com.twitter.android:id/vertical_video_pager",
      "com.twitter.android:id/immersive_media_viewer",
      "com.twitter.android:id/immersive_player_root",
    )

  private val viewIdKeywords: List<String> =
    listOf(
      "immersivevideo",
      "immersive_video",
      "explore_immersive",
      "vertical_video",
      "immersive_media",
    )
  private val windowClassKeywords: List<String> =
    listOf("immersivevideo", "exploreimmersive", "verticalvideo")

  override fun isEnabled(settings: BlockerSettings): Boolean = settings.blockXShorts

  override fun evaluate(snapshot: ScreenSnapshot): BlockDecision? =
    evaluateViewerMatch(
      snapshot = snapshot,
      platform = platform,
      supportedPackages = supportedPackages,
      candidateViewIds = candidateViewIds,
      viewIdKeywords = viewIdKeywords,
      windowClassKeywords = windowClassKeywords,
      selectedTabHint = VIDEO_TAB_SELECTED_HINT,
    )
}

private fun evaluateViewerMatch(
  snapshot: ScreenSnapshot,
  platform: Platform,
  supportedPackages: Set<String>,
  candidateViewIds: Set<String>,
  viewIdKeywords: List<String>,
  excludedViewIdKeywords: List<String> = emptyList(),
  windowClassKeywords: List<String>,
  selectedTabHint: String,
): BlockDecision? {
  if (snapshot.packageName !in supportedPackages) return null

  candidateViewIds.firstOrNull { it in snapshot.visibleViewIds }?.let { matchedId ->
    return BlockDecision(
      action = BlockAction.NAVIGATE_BACK,
      platform = platform,
      reason = "viewId:$matchedId",
    )
  }

  val matchedKeywordId =
    snapshot.visibleViewIds.firstOrNull { viewId ->
      val lower = viewId.lowercase()
      viewIdKeywords.any { lower.contains(it) } &&
        excludedViewIdKeywords.none { lower.contains(it) }
    }
  if (matchedKeywordId != null) {
    return BlockDecision(
      action = BlockAction.NAVIGATE_BACK,
      platform = platform,
      reason = "viewIdKeyword:$matchedKeywordId",
    )
  }

  val lowerClass = snapshot.windowClassName.lowercase()
  if (lowerClass.isNotEmpty() && windowClassKeywords.any { lowerClass.contains(it) }) {
    return BlockDecision(
      action = BlockAction.NAVIGATE_BACK,
      platform = platform,
      reason = "windowClass:${snapshot.windowClassName}",
    )
  }

  if (selectedTabHint in snapshot.selectedTabHints) {
    return BlockDecision(
      action = BlockAction.NAVIGATE_BACK,
      platform = platform,
      reason = "tab:$selectedTabHint",
    )
  }

  return null
}
