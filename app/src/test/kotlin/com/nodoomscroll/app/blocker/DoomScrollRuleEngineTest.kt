package com.nodoomscroll.app.blocker

import com.nodoomscroll.app.data.BlockerSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DoomScrollRuleEngineTest {

  private var fakeNowMs: Long = 0L
  private val cooldownGate = CooldownGate(cooldownMs = 1200L, clockMs = { fakeNowMs })
  private val engine = DoomScrollRuleEngine(cooldownGate = cooldownGate)

  @Test
  fun youtubeShorts_visibleViewId_triggersNavigateBackWhenEnabled() {
    val snapshot =
      ScreenSnapshot(
        packageName = "com.google.android.youtube",
        visibleViewIds = setOf("com.google.android.youtube:id/reel_recycler"),
      )

    val decision = engine.evaluate(snapshot, BlockerSettings(blockYouTubeShorts = true))

    assertEquals(BlockAction.NAVIGATE_BACK, decision.action)
    assertEquals(Platform.YOUTUBE_SHORTS, decision.platform)
    assertTrue(decision.reason.contains("reel_recycler"))
  }

  @Test
  fun youtubeShorts_disabled_returnsNone() {
    val snapshot =
      ScreenSnapshot(
        packageName = "com.google.android.youtube",
        visibleViewIds = setOf("com.google.android.youtube:id/reel_recycler"),
      )

    val decision = engine.evaluate(snapshot, BlockerSettings(blockYouTubeShorts = false))

    assertEquals(BlockAction.NONE, decision.action)
  }

  @Test
  fun youtubeNormalVideoAndFeed_returnsNone() {
    val normalVideoSnapshot =
      ScreenSnapshot(
        packageName = "com.google.android.youtube",
        visibleViewIds = setOf("com.google.android.youtube:id/watch_player"),
      )
    val homeFeedSnapshot =
      ScreenSnapshot(
        packageName = "com.google.android.youtube",
        visibleViewIds = emptySet(),
      )

    assertEquals(BlockAction.NONE, engine.evaluate(normalVideoSnapshot, BlockerSettings()).action)
    assertEquals(BlockAction.NONE, engine.evaluate(homeFeedSnapshot, BlockerSettings()).action)
  }

  @Test
  fun youtubeMusic_returnsNone() {
    val snapshot =
      ScreenSnapshot(
        packageName = "com.google.android.apps.youtube.music",
        visibleViewIds = setOf("com.google.android.youtube:id/reel_recycler"),
      )

    val decision = engine.evaluate(snapshot, BlockerSettings())

    assertEquals(BlockAction.NONE, decision.action)
  }

  @Test
  fun instagramReels_visibleViewId_triggersNavigateBack() {
    val snapshot =
      ScreenSnapshot(
        packageName = "com.instagram.android",
        visibleViewIds = setOf("com.instagram.android:id/clips_viewer_view_pager"),
      )

    val decision = engine.evaluate(snapshot, BlockerSettings(blockInstagramReels = true))

    assertEquals(BlockAction.NAVIGATE_BACK, decision.action)
    assertEquals(Platform.INSTAGRAM_REELS, decision.platform)
    assertTrue(decision.reason.contains("clips_viewer_view_pager"))
  }

  @Test
  fun instagramReels_selectedTabHint_triggersNavigateBack() {
    val snapshot =
      ScreenSnapshot(
        packageName = "com.instagram.android",
        selectedTabHints = setOf(InstagramReelsRule.CLIPS_TAB_SELECTED_HINT),
      )

    val decision = engine.evaluate(snapshot, BlockerSettings(blockInstagramReels = true))

    assertEquals(BlockAction.NAVIGATE_BACK, decision.action)
    assertEquals(Platform.INSTAGRAM_REELS, decision.platform)
    assertEquals("tab:${InstagramReelsRule.CLIPS_TAB_SELECTED_HINT}", decision.reason)
  }

  @Test
  fun instagramHomeDmAndThreads_returnsNone() {
    val homeOrDmSnapshot =
      ScreenSnapshot(
        packageName = "com.instagram.android",
        visibleViewIds = setOf("com.instagram.android:id/direct_inbox"),
      )
    val threadsSnapshot =
      ScreenSnapshot(
        packageName = "com.instagram.barcelona",
        visibleViewIds = setOf("com.instagram.android:id/clips_viewer_view_pager"),
      )

    assertEquals(BlockAction.NONE, engine.evaluate(homeOrDmSnapshot, BlockerSettings()).action)
    assertEquals(BlockAction.NONE, engine.evaluate(threadsSnapshot, BlockerSettings()).action)
  }

  @Test
  fun instagramReels_disabled_returnsNone() {
    val snapshot =
      ScreenSnapshot(
        packageName = "com.instagram.android",
        visibleViewIds = setOf("com.instagram.android:id/clips_viewer_view_pager"),
      )

    val decision = engine.evaluate(snapshot, BlockerSettings(blockInstagramReels = false))

    assertEquals(BlockAction.NONE, decision.action)
  }

  @Test
  fun tiktokPackages_triggerNavigateHomeWhenEnabled_andNoneWhenDisabled() {
    val musically = ScreenSnapshot(packageName = "com.zhiliaoapp.musically")
    val trill = ScreenSnapshot(packageName = "com.ss.android.ugc.trill")

    val musicallyDecision = engine.evaluate(musically, BlockerSettings(blockTikTok = true))
    assertEquals(BlockAction.NAVIGATE_HOME, musicallyDecision.action)
    assertEquals(Platform.TIKTOK, musicallyDecision.platform)

    fakeNowMs += 1200L
    val trillDecision = engine.evaluate(trill, BlockerSettings(blockTikTok = true))
    assertEquals(BlockAction.NAVIGATE_HOME, trillDecision.action)
    assertEquals(Platform.TIKTOK, trillDecision.platform)

    fakeNowMs += 1200L
    val disabledDecision = engine.evaluate(musically, BlockerSettings(blockTikTok = false))
    assertEquals(BlockAction.NONE, disabledDecision.action)
  }

  @Test
  fun cooldownGate_blocksDuplicateWithinWindow_allowsOtherPlatform_andResetsAfterWindow() {
    val ytSnapshot =
      ScreenSnapshot(
        packageName = "com.google.android.youtube",
        visibleViewIds = setOf("com.google.android.youtube:id/reel_recycler"),
      )
    val igSnapshot =
      ScreenSnapshot(
        packageName = "com.instagram.android",
        visibleViewIds = setOf("com.instagram.android:id/clips_viewer_view_pager"),
      )

    // Starts at 0ms: first trigger succeeds
    assertEquals(BlockAction.NAVIGATE_BACK, engine.evaluate(ytSnapshot, BlockerSettings()).action)

    // Within 1200ms: duplicate trigger for same platform is suppressed
    fakeNowMs = 1199L
    val suppressed = engine.evaluate(ytSnapshot, BlockerSettings())
    assertEquals(BlockAction.NONE, suppressed.action)
    assertEquals(Platform.YOUTUBE_SHORTS, suppressed.platform)
    assertEquals("cooldown", suppressed.reason)

    // Different platform is NOT blocked by YouTube cooldown
    assertEquals(BlockAction.NAVIGATE_BACK, engine.evaluate(igSnapshot, BlockerSettings()).action)

    // At 1200ms: YouTube cooldown elapsed and triggers again
    fakeNowMs = 1200L
    assertEquals(BlockAction.NAVIGATE_BACK, engine.evaluate(ytSnapshot, BlockerSettings()).action)
  }

  @Test
  fun facebookReels_exactViewerId_triggersNavigateBack() {
    val snapshot =
      ScreenSnapshot(
        packageName = "com.facebook.katana",
        visibleViewIds = setOf("com.facebook.katana:id/fb_shorts_viewer_container"),
      )

    val decision = engine.evaluate(snapshot, BlockerSettings(blockFacebookReels = true))

    assertEquals(BlockAction.NAVIGATE_BACK, decision.action)
    assertEquals(Platform.FACEBOOK_REELS, decision.platform)
    assertTrue(decision.reason.contains("fb_shorts_viewer_container"))
  }

  @Test
  fun facebookReels_dynamicKeywordTriggers_whileHomeFeedShelfIsIgnored() {
    val homeFeedShelf =
      ScreenSnapshot(
        packageName = "com.facebook.katana",
        visibleViewIds = setOf("com.facebook.katana:id/fb_shorts_tofu_unit"),
      )
    assertEquals(BlockAction.NONE, engine.evaluate(homeFeedShelf, BlockerSettings()).action)

    val dynamicViewer =
      ScreenSnapshot(
        packageName = "com.facebook.katana",
        visibleViewIds = setOf("com.facebook.katana:id/custom_fb_shorts_player_v2"),
      )
    val decision = engine.evaluate(dynamicViewer, BlockerSettings(blockFacebookReels = true))
    assertEquals(BlockAction.NAVIGATE_BACK, decision.action)
    assertEquals(Platform.FACEBOOK_REELS, decision.platform)
    assertTrue(decision.reason.startsWith("viewIdKeyword:"))
  }

  @Test
  fun facebookReels_windowClassTabHintLiteAndDisabled() {
    val windowClassMatch =
      ScreenSnapshot(
        packageName = "com.facebook.katana",
        windowClassName = "com.facebook.fbshorts.viewer.FbShortsViewerActivity",
      )
    assertEquals(BlockAction.NAVIGATE_BACK, engine.evaluate(windowClassMatch, BlockerSettings()).action)

    fakeNowMs += 1200L
    val liteTabMatch =
      ScreenSnapshot(
        packageName = "com.facebook.lite",
        selectedTabHints = setOf(FacebookReelsRule.REELS_TAB_SELECTED_HINT),
      )
    val liteDecision = engine.evaluate(liteTabMatch, BlockerSettings(blockFacebookReels = true))
    assertEquals(BlockAction.NAVIGATE_BACK, liteDecision.action)
    assertEquals(Platform.FACEBOOK_REELS, liteDecision.platform)

    fakeNowMs += 1200L
    assertEquals(
      BlockAction.NONE,
      engine.evaluate(windowClassMatch, BlockerSettings(blockFacebookReels = false)).action,
    )
  }

  @Test
  fun xShorts_immersiveIdsWindowClassTabHint_triggerNavigateBack_whileTimelineVideoIsIgnored() {
    val timelineInlineVideo =
      ScreenSnapshot(
        packageName = "com.twitter.android",
        visibleViewIds = setOf("com.twitter.android:id/video_player"),
      )
    assertEquals(BlockAction.NONE, engine.evaluate(timelineInlineVideo, BlockerSettings()).action)

    val immersiveId =
      ScreenSnapshot(
        packageName = "com.twitter.android",
        visibleViewIds = setOf("com.twitter.android:id/immersive_video_container"),
      )
    val idDecision = engine.evaluate(immersiveId, BlockerSettings(blockXShorts = true))
    assertEquals(BlockAction.NAVIGATE_BACK, idDecision.action)
    assertEquals(Platform.X_SHORTS, idDecision.platform)

    fakeNowMs += 1200L
    val keywordMatch =
      ScreenSnapshot(
        packageName = "com.twitter.android",
        visibleViewIds = setOf("com.twitter.android:id/explore_immersive_feed"),
      )
    assertEquals(BlockAction.NAVIGATE_BACK, engine.evaluate(keywordMatch, BlockerSettings()).action)

    fakeNowMs += 1200L
    val windowClassMatch =
      ScreenSnapshot(
        packageName = "com.twitter.android",
        windowClassName = "com.twitter.explore.immersive.ImmersiveVideoActivity",
      )
    assertEquals(BlockAction.NAVIGATE_BACK, engine.evaluate(windowClassMatch, BlockerSettings()).action)

    fakeNowMs += 1200L
    val tabMatch =
      ScreenSnapshot(
        packageName = "com.twitter.android",
        selectedTabHints = setOf(XShortsRule.VIDEO_TAB_SELECTED_HINT),
      )
    assertEquals(BlockAction.NAVIGATE_BACK, engine.evaluate(tabMatch, BlockerSettings()).action)

    fakeNowMs += 1200L
    assertEquals(
      BlockAction.NONE,
      engine.evaluate(immersiveId, BlockerSettings(blockXShorts = false)).action,
    )
  }

  @Test
  fun xShorts_liveComposeTags_triggerNavigateBack_whileMainPostDetailAndGalleryStayOpen() {
    val homeTimeline =
      ScreenSnapshot(
        packageName = "com.twitter.android",
        visibleViewIds = setOf("MainLanding", "scaffold_home_tabbed", "timeline_post"),
      )
    val postDetail =
      ScreenSnapshot(
        packageName = "com.twitter.android",
        visibleViewIds = setOf("PostDetail", "timeline_post"),
      )
    val mediaGallery =
      ScreenSnapshot(
        packageName = "com.twitter.android",
        visibleViewIds = setOf("MediaGallery", "GalleryActionBar"),
      )

    assertEquals(BlockAction.NONE, engine.evaluate(homeTimeline, BlockerSettings()).action)
    assertEquals(BlockAction.NONE, engine.evaluate(postDetail, BlockerSettings()).action)
    assertEquals(BlockAction.NONE, engine.evaluate(mediaGallery, BlockerSettings()).action)

    val videoTabAndImmersive =
      ScreenSnapshot(
        packageName = "com.twitter.android",
        visibleViewIds = setOf("VideoTab", "ImmersiveVideo"),
      )
    val tabDecision = engine.evaluate(videoTabAndImmersive, BlockerSettings(blockXShorts = true))
    assertEquals(BlockAction.NAVIGATE_BACK, tabDecision.action)
    assertEquals(Platform.X_SHORTS, tabDecision.platform)

    fakeNowMs += 1200L
    val immersiveOnly =
      ScreenSnapshot(
        packageName = "com.twitter.android",
        visibleViewIds = setOf("ImmersiveVideo"),
      )
    val immersiveDecision = engine.evaluate(immersiveOnly, BlockerSettings(blockXShorts = true))
    assertEquals(BlockAction.NAVIGATE_BACK, immersiveDecision.action)
    assertEquals(Platform.X_SHORTS, immersiveDecision.platform)
  }

  @Test
  fun cooldownGate_directResetAllowsImmediateAcquire() {
    assertTrue(cooldownGate.tryAcquire(Platform.TIKTOK))
    assertFalse(cooldownGate.tryAcquire(Platform.TIKTOK))

    cooldownGate.reset()

    assertTrue(cooldownGate.tryAcquire(Platform.TIKTOK))
  }
}
