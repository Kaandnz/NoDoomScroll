package com.nodoomscroll.app.blocker

import com.nodoomscroll.app.data.BlockerSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FacebookAccessibilityAnalyzerTest {

  private val phoneBounds = NodeBounds(left = 0, top = 0, right = 1080, bottom = 2400)
  private val engine = DoomScrollRuleEngine(cooldownGate = CooldownGate(cooldownMs = 1200L, clockMs = { 0L }))

  @Test
  fun normalizeLabel_handlesTurkishDottedAndDotlessI() {
    assertEquals("ilgilenmiyorum", FacebookAccessibilityAnalyzer.normalizeLabel("İLGİLENMİYORUM"))
    assertEquals("orijinal ses", FacebookAccessibilityAnalyzer.normalizeLabel("  ORİJİNAL   SES  "))
    assertEquals("isik", FacebookAccessibilityAnalyzer.normalizeLabel("Işık".replace('ş', 's').replace('k', 'k')))
    assertEquals("paylaş", FacebookAccessibilityAnalyzer.normalizeLabel("Paylaş"))
  }

  @Test
  fun turkishFullScreenReelsViewer_withVerticalRightActionRail_triggersNavigateBack() {
    val nodes =
      listOf(
        ObservedNode(text = "Reels", bounds = NodeBounds(120, 90, 340, 170)),
        ObservedNode(contentDescription = "Beğen", bounds = NodeBounds(910, 1280, 1050, 1400)),
        ObservedNode(contentDescription = "Yorum yap", bounds = NodeBounds(910, 1460, 1050, 1580)),
        ObservedNode(contentDescription = "Paylaş", bounds = NodeBounds(910, 1640, 1050, 1760)),
        ObservedNode(contentDescription = "Daha fazla seçenek", bounds = NodeBounds(910, 1820, 1050, 1940)),
        ObservedNode(text = "Orijinal ses", bounds = NodeBounds(40, 2100, 620, 2180)),
      )

    val signals = FacebookAccessibilityAnalyzer.analyze(nodes, phoneBounds)

    assertFalse(signals.hasMainFeedTabBar)
    assertTrue(signals.hasVerticalRightActionRail)
    assertTrue(signals.hasPlayerOrEngagementControl)

    val decision =
      engine.evaluate(
        ScreenSnapshot(packageName = "com.facebook.katana", facebookViewerSignals = signals),
        BlockerSettings(blockFacebookReels = true),
      )
    assertEquals(BlockAction.NAVIGATE_BACK, decision.action)
    assertEquals(Platform.FACEBOOK_REELS, decision.platform)
    assertEquals("facebook:verticalRightRail", decision.reason)
  }

  @Test
  fun englishFullScreenReelsViewer_withVerticalRightActionRail_triggersNavigateBack() {
    val nodes =
      listOf(
        ObservedNode(contentDescription = "Like, 12.4K", bounds = NodeBounds(900, 1300, 1040, 1410)),
        ObservedNode(contentDescription = "Comment, 320", bounds = NodeBounds(900, 1500, 1040, 1610)),
        ObservedNode(contentDescription = "Share", bounds = NodeBounds(900, 1700, 1040, 1810)),
        ObservedNode(text = "Original audio", bounds = NodeBounds(48, 2080, 560, 2150)),
      )

    val signals = FacebookAccessibilityAnalyzer.analyze(nodes, phoneBounds)

    assertTrue(signals.hasVerticalRightActionRail)
    val decision =
      engine.evaluate(
        ScreenSnapshot(packageName = "com.facebook.katana", facebookViewerSignals = signals),
        BlockerSettings(),
      )
    assertEquals(BlockAction.NAVIGATE_BACK, decision.action)
  }

  @Test
  fun homeFeedWithInlineReelsShelfAndOriginalAudio_isNeverBlocked() {
    val nodes =
      listOf(
        ObservedNode(contentDescription = "Ana Sayfa", isSelected = true, bounds = NodeBounds(0, 120, 180, 220)),
        ObservedNode(contentDescription = "Arkadaşlar", bounds = NodeBounds(180, 120, 360, 220)),
        ObservedNode(contentDescription = "Reels", bounds = NodeBounds(360, 120, 540, 220)),
        ObservedNode(contentDescription = "Bildirimler", bounds = NodeBounds(720, 120, 900, 220)),
        ObservedNode(text = "Reels ve kısa videolar", bounds = NodeBounds(40, 700, 520, 770)),
        ObservedNode(text = "Orijinal ses", bounds = NodeBounds(40, 1300, 420, 1360)),
        ObservedNode(contentDescription = "Beğen", bounds = NodeBounds(40, 1950, 300, 2040)),
        ObservedNode(contentDescription = "Yorum yap", bounds = NodeBounds(380, 1950, 680, 2040)),
        ObservedNode(contentDescription = "Paylaş", bounds = NodeBounds(760, 1950, 1020, 2040)),
      )

    val signals =
      FacebookAccessibilityAnalyzer.analyze(
        nodes = nodes,
        screenBounds = phoneBounds,
        recentFeedReelClick = true,
      )

    assertTrue(signals.hasMainFeedTabBar)
    assertFalse(signals.hasVerticalRightActionRail)

    val decision =
      engine.evaluate(
        ScreenSnapshot(packageName = "com.facebook.katana", facebookViewerSignals = signals),
        BlockerSettings(),
      )
    assertEquals(BlockAction.NONE, decision.action)
  }

  @Test
  fun homeFeedBottomNavigationWithoutSelectedFlag_detectsMainFeedTabBar() {
    val nodes =
      listOf(
        ObservedNode(contentDescription = "Feed", bounds = NodeBounds(20, 2200, 180, 2340)),
        ObservedNode(contentDescription = "Friends", bounds = NodeBounds(220, 2200, 380, 2340)),
        ObservedNode(contentDescription = "Marketplace", bounds = NodeBounds(420, 2200, 620, 2340)),
        ObservedNode(contentDescription = "Notifications", bounds = NodeBounds(640, 2200, 840, 2340)),
      )

    val signals = FacebookAccessibilityAnalyzer.analyze(nodes, phoneBounds)

    assertTrue(signals.hasMainFeedTabBar)
  }

  @Test
  fun profilePageWithSubTabsAndHorizontalPostButtons_isIgnored() {
    val nodes =
      listOf(
        ObservedNode(text = "Gönderiler", bounds = NodeBounds(30, 820, 250, 900)),
        ObservedNode(text = "Fotoğraflar", bounds = NodeBounds(270, 820, 520, 900)),
        ObservedNode(text = "Hakkında", bounds = NodeBounds(540, 820, 760, 900)),
        ObservedNode(text = "Reels", bounds = NodeBounds(780, 820, 960, 900)),
        ObservedNode(contentDescription = "Beğen", bounds = NodeBounds(40, 1700, 280, 1790)),
        ObservedNode(contentDescription = "Yorum", bounds = NodeBounds(360, 1700, 620, 1790)),
        ObservedNode(contentDescription = "Paylaş", bounds = NodeBounds(700, 1700, 980, 1790)),
      )

    val signals = FacebookAccessibilityAnalyzer.analyze(nodes, phoneBounds)

    assertTrue(signals.hasProfileSubTabs)
    assertFalse(signals.hasVerticalRightActionRail)

    val decision =
      engine.evaluate(
        ScreenSnapshot(packageName = "com.facebook.katana", facebookViewerSignals = signals),
        BlockerSettings(),
      )
    assertEquals(BlockAction.NONE, decision.action)
  }

  @Test
  fun facebookStoriesAndPhotoViewer_areNotBlocked() {
    val storiesNodes =
      listOf(
        ObservedNode(text = "Mesaj gönder", bounds = NodeBounds(40, 2160, 680, 2280)),
        ObservedNode(contentDescription = "Hikayeye yanıt ver", bounds = NodeBounds(40, 2160, 680, 2280)),
        ObservedNode(contentDescription = "Beğen", bounds = NodeBounds(740, 2160, 880, 2280)),
        ObservedNode(contentDescription = "Paylaş", bounds = NodeBounds(900, 2160, 1040, 2280)),
      )
    val storiesSignals = FacebookAccessibilityAnalyzer.analyze(storiesNodes, phoneBounds)
    assertFalse(storiesSignals.hasVerticalRightActionRail)
    assertEquals(
      BlockAction.NONE,
      engine.evaluate(
        ScreenSnapshot(packageName = "com.facebook.katana", facebookViewerSignals = storiesSignals),
        BlockerSettings(),
      ).action,
    )

    val photoViewerNodes =
      listOf(
        ObservedNode(contentDescription = "Beğen", bounds = NodeBounds(40, 2160, 320, 2260)),
        ObservedNode(contentDescription = "Yorum", bounds = NodeBounds(380, 2160, 660, 2260)),
        ObservedNode(contentDescription = "Paylaş", bounds = NodeBounds(720, 2160, 1020, 2260)),
      )
    val photoSignals = FacebookAccessibilityAnalyzer.analyze(photoViewerNodes, phoneBounds)
    assertFalse(photoSignals.hasVerticalRightActionRail)
    assertEquals(
      BlockAction.NONE,
      engine.evaluate(
        ScreenSnapshot(packageName = "com.facebook.katana", facebookViewerSignals = photoSignals),
        BlockerSettings(),
      ).action,
    )
  }

  @Test
  fun explicitReelViewerLabelsAndCorroboratingSignals_triggerNavigateBack() {
    val explicitNodes =
      listOf(
        ObservedNode(contentDescription = "Reels videosunu beğen", bounds = NodeBounds(900, 1400, 1040, 1500)),
      )
    val explicitSignals = FacebookAccessibilityAnalyzer.analyze(explicitNodes, phoneBounds)
    val explicitDecision =
      engine.evaluate(
        ScreenSnapshot(packageName = "com.facebook.katana", facebookViewerSignals = explicitSignals),
        BlockerSettings(),
      )
    assertEquals(BlockAction.NAVIGATE_BACK, explicitDecision.action)
    assertTrue(explicitDecision.reason.startsWith("facebook:explicitLabel:"))

    val supportingEngine =
      DoomScrollRuleEngine(cooldownGate = CooldownGate(cooldownMs = 1200L, clockMs = { 0L }))
    val supportingNodes =
      listOf(
        ObservedNode(text = "Reels", bounds = NodeBounds(120, 90, 340, 170)),
        ObservedNode(text = "Orijinal ses", bounds = NodeBounds(60, 2100, 500, 2160)),
        ObservedNode(contentDescription = "Duraklat", bounds = NodeBounds(450, 1100, 630, 1280)),
      )
    val supportingSignals = FacebookAccessibilityAnalyzer.analyze(supportingNodes, phoneBounds)
    val supportingDecision =
      supportingEngine.evaluate(
        ScreenSnapshot(packageName = "com.facebook.katana", facebookViewerSignals = supportingSignals),
        BlockerSettings(),
      )
    assertEquals(BlockAction.NAVIGATE_BACK, supportingDecision.action)
    assertTrue(supportingDecision.reason.startsWith("facebook:viewerSignals:"))
  }

  @Test
  fun feedReelClick_corroboratesPlayerWithoutFeedTabBar_andRejectsDismissClicks() {
    assertTrue(
      FacebookAccessibilityAnalyzer.isFeedReelClick(
        selfLabels = listOf("Reels videosunu oynat"),
        contextLabels = emptyList(),
      ),
    )
    assertTrue(
      FacebookAccessibilityAnalyzer.isFeedReelClick(
        selfLabels = listOf("Komediyi izle"),
        contextLabels = listOf("Reels ve kısa videolar"),
      ),
    )
    assertFalse(
      FacebookAccessibilityAnalyzer.isFeedReelClick(
        selfLabels = listOf("Gizle"),
        contextLabels = listOf("Reels ve kısa videolar"),
      ),
    )
    assertFalse(
      FacebookAccessibilityAnalyzer.isFeedReelClick(
        selfLabels = listOf("Hide"),
        contextLabels = listOf("Reels"),
      ),
    )
    assertFalse(
      FacebookAccessibilityAnalyzer.isFeedReelClick(
        selfLabels = listOf("Daha fazla seçenek"),
        contextLabels = listOf("Reels"),
      ),
    )
    assertFalse(
      FacebookAccessibilityAnalyzer.isFeedReelClick(
        selfLabels = listOf("Freelance iş ilanı"),
        contextLabels = emptyList(),
      ),
    )

    val playerNodes =
      listOf(
        ObservedNode(contentDescription = "Beğen", bounds = NodeBounds(40, 2120, 240, 2220)),
      )
    val signalsWithClick =
      FacebookAccessibilityAnalyzer.analyze(
        nodes = playerNodes,
        screenBounds = phoneBounds,
        recentFeedReelClick = true,
      )
    val decision =
      engine.evaluate(
        ScreenSnapshot(packageName = "com.facebook.katana", facebookViewerSignals = signalsWithClick),
        BlockerSettings(),
      )
    assertEquals(BlockAction.NAVIGATE_BACK, decision.action)
    assertEquals("facebook:feedReelClick", decision.reason)
  }
}
