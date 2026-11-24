package com.nodoomscroll.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.nodoomscroll.app.blocker.BlockAction
import com.nodoomscroll.app.blocker.BlockRule
import com.nodoomscroll.app.blocker.ContinuousScrollTracker
import com.nodoomscroll.app.blocker.DoomScrollRuleEngine
import com.nodoomscroll.app.blocker.FacebookAccessibilityAnalyzer
import com.nodoomscroll.app.blocker.FacebookReelsRule
import com.nodoomscroll.app.blocker.FacebookViewerSignals
import com.nodoomscroll.app.blocker.InstagramReelsRule
import com.nodoomscroll.app.blocker.NodeBounds
import com.nodoomscroll.app.blocker.ObservedNode
import com.nodoomscroll.app.blocker.Platform
import com.nodoomscroll.app.blocker.ScreenSnapshot
import com.nodoomscroll.app.blocker.ScrollEvent
import com.nodoomscroll.app.blocker.ScrollSessionSnapshot
import com.nodoomscroll.app.blocker.XShortsRule
import com.nodoomscroll.app.data.BlockerSettings
import com.nodoomscroll.app.data.ScrollGuardAction
import com.nodoomscroll.app.data.SettingsRepository
import com.nodoomscroll.app.data.SharedPreferencesSettingsRepository
import com.nodoomscroll.app.data.SharedPreferencesStatsRepository
import com.nodoomscroll.app.data.StatsRepository

/**
 * Monitors supported short-video apps and triggers Back/Home actions based on [DoomScrollRuleEngine]
 * as well as continuous downward scroll interventions via [ContinuousScrollTracker].
 */
class DoomScrollAccessibilityService : AccessibilityService() {

  private lateinit var settingsRepository: SettingsRepository
  private lateinit var statsRepository: StatsRepository
  private lateinit var scrollBreakOverlay: ScrollBreakOverlayController
  private val ruleEngine = DoomScrollRuleEngine()
  private val scrollTracker = ContinuousScrollTracker()
  private var lastDebugLogUptimeMs: Long = 0L
  private var lastWindowPackage: String = ""
  private var lastWindowClassName: String = ""
  private var lastFacebookReelClickUptimeMs: Long = 0L

  override fun onServiceConnected() {
    super.onServiceConnected()
    ensureDependencies()
    isConnected = true
    Log.i(TAG, "DoomScrollAccessibilityService connected")
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    ensureDependencies()

    val activeRoot = rootInActiveWindow
    val packageName =
      activeRoot?.packageName?.toString()
        ?: event?.packageName?.toString()
        ?: return

    if (packageName != lastWindowPackage) {
      lastWindowPackage = packageName
      lastWindowClassName = ""
      lastFacebookReelClickUptimeMs = 0L
      scrollTracker.onForegroundPackageChanged(packageName)
      if (scrollBreakOverlay.isShowing && scrollBreakOverlay.targetPackage != packageName) {
        scrollBreakOverlay.dismiss()
      }
    }
    if (
      event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
        (event.packageName == null || event.packageName.toString() == packageName)
    ) {
      lastWindowClassName = event.className?.toString().orEmpty()
    }

    if (packageName !in ruleEngine.watchedPackages) return

    val settings = settingsRepository.currentSettings()
    if (
      event?.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED &&
        packageName in FacebookReelsRule.supportedPackages &&
        settings.blockFacebookReels
    ) {
      recordFacebookFeedClickIfRelevant(event)
    }

    val matchingRules = ruleEngine.rulesForPackage(packageName)
    if (matchingRules.any { it.isEnabled(settings) } || settings.debugLoggingEnabled) {
      val snapshot =
        buildSnapshot(
          packageName = packageName,
          matchingRules = matchingRules,
          event = event,
          activeRoot = activeRoot,
          debugLoggingEnabled = settings.debugLoggingEnabled,
        )
      val decision = ruleEngine.evaluate(snapshot, settings)

      when (decision.action) {
        BlockAction.NAVIGATE_BACK -> {
          val ok = performGlobalAction(GLOBAL_ACTION_BACK)
          if (decision.platform == Platform.FACEBOOK_REELS) {
            lastFacebookReelClickUptimeMs = 0L
          }
          if (ok) {
            statsRepository.recordShortVideoBlock()
          }
          Log.i(TAG, "Blocked ${decision.platform} (${decision.reason}) action=BACK ok=$ok")
          return
        }
        BlockAction.NAVIGATE_HOME -> {
          val ok = performGlobalAction(GLOBAL_ACTION_HOME)
          if (ok) {
            statsRepository.recordShortVideoBlock()
          }
          scrollTracker.resetSession()
          scrollBreakOverlay.dismiss()
          Log.i(TAG, "Blocked ${decision.platform} (${decision.reason}) action=HOME ok=$ok")
          return
        }
        BlockAction.NONE -> {
          if (decision.reason == "cooldown") return
        }
      }
    }

    if (
      event?.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED &&
        settings.continuousScrollGuardEnabled &&
        !scrollBreakOverlay.isShowing
    ) {
      handleScrollEvent(packageName = packageName, event = event, settings = settings)
    }
  }

  override fun onInterrupt() {
    if (::scrollBreakOverlay.isInitialized) {
      scrollBreakOverlay.dismiss()
    }
    Log.d(TAG, "DoomScrollAccessibilityService interrupted")
  }

  override fun onUnbind(intent: Intent?): Boolean {
    isConnected = false
    if (::scrollBreakOverlay.isInitialized) {
      scrollBreakOverlay.dismiss()
    }
    Log.i(TAG, "DoomScrollAccessibilityService unbound")
    return super.onUnbind(intent)
  }

  override fun onDestroy() {
    isConnected = false
    if (::scrollBreakOverlay.isInitialized) {
      scrollBreakOverlay.dismiss()
    }
    super.onDestroy()
  }

  private fun ensureDependencies() {
    if (!::settingsRepository.isInitialized) {
      settingsRepository = SharedPreferencesSettingsRepository.getInstance(applicationContext)
    }
    if (!::statsRepository.isInitialized) {
      statsRepository = SharedPreferencesStatsRepository.getInstance(applicationContext)
    }
    if (!::scrollBreakOverlay.isInitialized) {
      scrollBreakOverlay = ScrollBreakOverlayController(this)
    }
  }

  private fun recordFacebookFeedClickIfRelevant(event: AccessibilityEvent) {
    val selfLabels = mutableListOf<String>()
    val contextLabels = mutableListOf<String>()

    event.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { selfLabels.add(it) }
    for (item in event.text) {
      item?.toString()?.takeIf { it.isNotBlank() }?.let { selfLabels.add(it) }
    }

    val source = event.source
    if (source != null) {
      source.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { selfLabels.add(it) }
      source.text?.toString()?.takeIf { it.isNotBlank() }?.let { selfLabels.add(it) }

      var ancestor = source.parent
      var ancestorSteps = 0
      while (ancestor != null && ancestorSteps < MAX_CLICK_ANCESTOR_STEPS) {
        ancestor.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { contextLabels.add(it) }
        ancestor.text?.toString()?.takeIf { it.isNotBlank() }?.let { contextLabels.add(it) }
        ancestor = ancestor.parent
        ancestorSteps++
      }

      collectShallowChildLabels(source, contextLabels)
    }

    if (FacebookAccessibilityAnalyzer.isFeedReelClick(selfLabels, contextLabels)) {
      lastFacebookReelClickUptimeMs = SystemClock.uptimeMillis()
    }
  }

  private fun collectShallowChildLabels(
    root: AccessibilityNodeInfo,
    outLabels: MutableList<String>,
  ) {
    val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
    queue.addLast(root to 0)
    var visited = 0
    while (queue.isNotEmpty() && visited < MAX_CLICK_CHILD_NODES) {
      val (node, depth) = queue.removeFirst()
      if (depth > 0 && node.isVisibleToUser) {
        visited++
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { outLabels.add(it) }
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let { outLabels.add(it) }
      }
      if (depth >= MAX_CLICK_CHILD_DEPTH) continue
      for (i in 0 until node.childCount) {
        val child = node.getChild(i) ?: continue
        queue.addLast(child to (depth + 1))
      }
    }
  }

  private fun handleScrollEvent(
    packageName: String,
    event: AccessibilityEvent,
    settings: BlockerSettings,
  ) {
    val deltaX = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) event.scrollDeltaX else 0
    val deltaY = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) event.scrollDeltaY else 0
    val intervention =
      scrollTracker.onScroll(
        event =
          ScrollEvent(
            packageName = packageName,
            deltaX = deltaX,
            deltaY = deltaY,
            fromIndex = event.fromIndex,
            toIndex = event.toIndex,
          ),
        settings = settings,
      ) ?: return

    statsRepository.recordScrollBreak()
    Log.i(
      TAG,
      "Continuous scroll break pkg=$packageName swipes=${intervention.swipeCount} " +
        "durationMs=${intervention.sessionDurationMs} reason=${intervention.triggerReason} " +
        "action=${settings.scrollGuardAction}",
    )

    when (settings.scrollGuardAction) {
      ScrollGuardAction.NAVIGATE_HOME -> {
        performGlobalAction(GLOBAL_ACTION_HOME)
      }
      ScrollGuardAction.BREAK_OVERLAY -> {
        val shown =
          scrollBreakOverlay.show(
            packageName = packageName,
            appName = displayNameForPackage(packageName),
            swipeCount = intervention.swipeCount,
            sessionDurationMs = intervention.sessionDurationMs,
            onGoHome = {
              scrollTracker.resetSession()
              performGlobalAction(GLOBAL_ACTION_HOME)
            },
            onSnooze = {
              scrollTracker.snooze(packageName)
            },
          )
        if (!shown) {
          scrollTracker.resetSession()
          performGlobalAction(GLOBAL_ACTION_HOME)
        }
      }
    }
  }

  private fun buildSnapshot(
    packageName: String,
    matchingRules: List<BlockRule>,
    event: AccessibilityEvent?,
    activeRoot: AccessibilityNodeInfo?,
    debugLoggingEnabled: Boolean,
  ): ScreenSnapshot {
    val candidateIds = matchingRules.flatMapTo(linkedSetOf()) { it.candidateViewIds }
    val isFacebook = packageName in FacebookReelsRule.supportedPackages
    val needsTreeScan = matchingRules.any { it.requiresTreeScan } || debugLoggingEnabled
    val needsRoot =
      candidateIds.isNotEmpty() ||
        packageName == INSTAGRAM_PACKAGE ||
        needsTreeScan

    if (!needsRoot) {
      return ScreenSnapshot(
        packageName = packageName,
        windowClassName = lastWindowClassName,
      )
    }

    val root = activeRoot ?: event?.source
    val visibleViewIds =
      if (root == null) {
        linkedSetOf()
      } else {
        candidateIds.filterTo(linkedSetOf()) { viewId ->
          hasVisibleNode(root, viewId)
        }
      }

    val selectedTabHints = linkedSetOf<String>()
    if (root != null && packageName == INSTAGRAM_PACKAGE && isClipsTabSelected(root)) {
      selectedTabHints.add(InstagramReelsRule.CLIPS_TAB_SELECTED_HINT)
    }

    val scanResult =
      if (root != null && needsTreeScan) {
        scanVisibleWindow(
          root = root,
          packageName = packageName,
          collectObservedNodes = isFacebook,
          maxDepth = if (isFacebook) FACEBOOK_MAX_SCAN_DEPTH else DEFAULT_MAX_SCAN_DEPTH,
          maxNodes = if (isFacebook) FACEBOOK_MAX_SCAN_NODES else DEFAULT_MAX_SCAN_NODES,
        )
      } else {
        null
      }

    if (scanResult != null) {
      visibleViewIds.addAll(scanResult.visibleIds)
      selectedTabHints.addAll(scanResult.selectedTabHints)
    }

    val facebookViewerSignals =
      if (isFacebook && scanResult != null) {
        val recentClick =
          lastFacebookReelClickUptimeMs > 0L &&
            (SystemClock.uptimeMillis() - lastFacebookReelClickUptimeMs) <= FEED_REEL_CLICK_WINDOW_MS
        FacebookAccessibilityAnalyzer.analyze(
          nodes = scanResult.observedNodes,
          screenBounds = scanResult.screenBounds,
          recentFeedReelClick = recentClick,
        )
      } else {
        FacebookViewerSignals()
      }

    if (debugLoggingEnabled) {
      logDebugInspection(
        packageName = packageName,
        event = event,
        visibleIds = scanResult?.visibleIds ?: visibleViewIds.toList(),
        selectedTabHints = selectedTabHints,
        selectedLabels = scanResult?.selectedLabels.orEmpty(),
        facebookViewerSignals = if (isFacebook) facebookViewerSignals else null,
        scrollSession = scrollTracker.currentSession(),
      )
    }

    return ScreenSnapshot(
      packageName = packageName,
      visibleViewIds = visibleViewIds,
      selectedTabHints = selectedTabHints,
      windowClassName = lastWindowClassName,
      facebookViewerSignals = facebookViewerSignals,
    )
  }

  private fun hasVisibleNode(root: AccessibilityNodeInfo, viewId: String): Boolean {
    val nodes = root.findAccessibilityNodeInfosByViewId(viewId).orEmpty()
    return nodes.any { node -> node.isVisibleToUser }
  }

  private fun isClipsTabSelected(root: AccessibilityNodeInfo): Boolean {
    val nodes = root.findAccessibilityNodeInfosByViewId(InstagramReelsRule.CLIPS_TAB_ID).orEmpty()
    return nodes.any { node -> node.isVisibleToUser && node.isSelected }
  }

  private fun logDebugInspection(
    packageName: String,
    event: AccessibilityEvent?,
    visibleIds: List<String>,
    selectedTabHints: Set<String>,
    selectedLabels: List<String>,
    facebookViewerSignals: FacebookViewerSignals?,
    scrollSession: ScrollSessionSnapshot,
  ) {
    val now = SystemClock.uptimeMillis()
    if (now - lastDebugLogUptimeMs < DEBUG_LOG_INTERVAL_MS) return
    lastDebugLogUptimeMs = now

    val fbPart =
      if (facebookViewerSignals != null) {
        " fbSignals=(feedTab=${facebookViewerSignals.hasMainFeedTabBar}," +
          "profileTabs=${facebookViewerSignals.hasProfileSubTabs}," +
          "rail=${facebookViewerSignals.hasVerticalRightActionRail}," +
          "explicit=${facebookViewerSignals.explicitReelViewerLabels}," +
          "support=${facebookViewerSignals.supportingReelLabels}," +
          "ctrl=${facebookViewerSignals.hasPlayerOrEngagementControl}," +
          "click=${facebookViewerSignals.recentFeedReelClick})"
      } else {
        ""
      }

    val scrollPart =
      if (scrollSession.swipeCount > 0) {
        " scroll=(pkg=${scrollSession.packageName},swipes=${scrollSession.swipeCount}," +
          "sec=${scrollSession.sessionDurationMs / 1000L})"
      } else {
        ""
      }

    Log.d(
      TAG,
      "Inspect pkg=$packageName cls=$lastWindowClassName event=${event?.eventType} " +
        "visibleIds=$visibleIds selectedLabels=$selectedLabels selectedHints=$selectedTabHints" +
        fbPart +
        scrollPart,
    )
  }

  private fun scanVisibleWindow(
    root: AccessibilityNodeInfo,
    packageName: String,
    collectObservedNodes: Boolean,
    maxDepth: Int = DEFAULT_MAX_SCAN_DEPTH,
    maxNodes: Int = DEFAULT_MAX_SCAN_NODES,
    maxIds: Int = 40,
    maxSelectedLabels: Int = 10,
  ): WindowScanResult {
    val collectedIds = linkedSetOf<String>()
    val selectedTabHints = linkedSetOf<String>()
    val selectedLabels = linkedSetOf<String>()
    val observedNodes = if (collectObservedNodes) ArrayList<ObservedNode>(64) else mutableListOf()
    val reusableRect = Rect()
    root.getBoundsInScreen(reusableRect)
    val screenBounds =
      NodeBounds(
        left = reusableRect.left,
        top = reusableRect.top,
        right = reusableRect.right,
        bottom = reusableRect.bottom,
      )

    val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
    queue.addLast(root to 0)
    var visitedNodes = 0

    while (queue.isNotEmpty() && visitedNodes < maxNodes) {
      val (node, depth) = queue.removeFirst()
      visitedNodes++

      val isVisible = node.isVisibleToUser
      if (isVisible) {
        val resourceName = node.viewIdResourceName
        if (!resourceName.isNullOrBlank() && collectedIds.size < maxIds) {
          collectedIds.add(resourceName)
        }
        if (node.isSelected) {
          collectSelectedTabSignals(
            node = node,
            packageName = packageName,
            selectedLabels = selectedLabels,
            selectedTabHints = selectedTabHints,
            maxSelectedLabels = maxSelectedLabels,
          )
        }
        if (collectObservedNodes) {
          node.getBoundsInScreen(reusableRect)
          observedNodes.add(
            ObservedNode(
              text = node.text?.toString().orEmpty(),
              contentDescription = node.contentDescription?.toString().orEmpty(),
              className = node.className?.toString().orEmpty(),
              viewId = resourceName.orEmpty(),
              isSelected = node.isSelected,
              isClickable = node.isClickable,
              isScrollable = node.isScrollable,
              bounds =
                NodeBounds(
                  left = reusableRect.left,
                  top = reusableRect.top,
                  right = reusableRect.right,
                  bottom = reusableRect.bottom,
                ),
            ),
          )
        }
      }

      if (depth >= maxDepth) continue
      if (depth > 1 && !isVisible) continue
      for (i in 0 until node.childCount) {
        val child = node.getChild(i) ?: continue
        queue.addLast(child to (depth + 1))
      }
    }

    return WindowScanResult(
      visibleIds = collectedIds.toList(),
      selectedTabHints = selectedTabHints,
      selectedLabels = selectedLabels.toList(),
      observedNodes = observedNodes,
      screenBounds = screenBounds,
    )
  }

  private fun collectSelectedTabSignals(
    node: AccessibilityNodeInfo,
    packageName: String,
    selectedLabels: MutableSet<String>,
    selectedTabHints: MutableSet<String>,
    maxSelectedLabels: Int,
  ) {
    val rawCandidates = listOfNotNull(node.contentDescription?.toString(), node.text?.toString())
    for (raw in rawCandidates) {
      val trimmed = raw.trim()
      if (trimmed.isEmpty() || trimmed.length > MAX_TAB_LABEL_LENGTH) continue

      if (selectedLabels.size < maxSelectedLabels) {
        selectedLabels.add(trimmed)
      }

      val normalized = trimmed.lowercase()
      when (packageName) {
        in FacebookReelsRule.supportedPackages -> {
          if (
            normalized == "reels" ||
              normalized.startsWith("reels,") ||
              normalized.startsWith("reels ")
          ) {
            selectedTabHints.add(FacebookReelsRule.REELS_TAB_SELECTED_HINT)
          }
        }
        in XShortsRule.supportedPackages -> {
          if (
            normalized in X_VIDEO_TAB_LABELS ||
              normalized.startsWith("video,") ||
              normalized.startsWith("videos,") ||
              normalized.startsWith("videolar,")
          ) {
            selectedTabHints.add(XShortsRule.VIDEO_TAB_SELECTED_HINT)
          }
        }
      }
    }
  }

  private fun displayNameForPackage(packageName: String): String =
    when (packageName) {
      "com.google.android.youtube" -> "YouTube"
      "com.instagram.android" -> "Instagram"
      "com.facebook.katana", "com.facebook.lite" -> "Facebook"
      "com.twitter.android" -> "X"
      "com.zhiliaoapp.musically", "com.ss.android.ugc.trill" -> "TikTok"
      else -> "Sosyal Medya"
    }

  private data class WindowScanResult(
    val visibleIds: List<String>,
    val selectedTabHints: Set<String>,
    val selectedLabels: List<String>,
    val observedNodes: List<ObservedNode>,
    val screenBounds: NodeBounds,
  )

  companion object {
    const val TAG: String = "NoDoomScroll"
    private const val INSTAGRAM_PACKAGE: String = "com.instagram.android"
    private const val DEBUG_LOG_INTERVAL_MS: Long = 1500L
    private const val MAX_TAB_LABEL_LENGTH: Int = 30
    private const val DEFAULT_MAX_SCAN_DEPTH: Int = 16
    private const val DEFAULT_MAX_SCAN_NODES: Int = 180
    private const val FACEBOOK_MAX_SCAN_DEPTH: Int = 24
    private const val FACEBOOK_MAX_SCAN_NODES: Int = 320
    private const val FEED_REEL_CLICK_WINDOW_MS: Long = 3500L
    private const val MAX_CLICK_ANCESTOR_STEPS: Int = 2
    private const val MAX_CLICK_CHILD_DEPTH: Int = 2
    private const val MAX_CLICK_CHILD_NODES: Int = 8
    private val X_VIDEO_TAB_LABELS: Set<String> =
      setOf("video", "videos", "videolar", "video tab", "videolar sekmesi")

    @Volatile
    var isConnected: Boolean = false
      private set
  }
}
