package com.nodoomscroll.app.blocker

import com.nodoomscroll.app.data.BlockerSettings

/**
 * Contract for a single platform-specific short-video detection rule.
 */
interface BlockRule {
  val platform: Platform
  val supportedPackages: Set<String>
  val candidateViewIds: Set<String>

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
