package com.nodoomscroll.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.nodoomscroll.app.blocker.BlockAction
import com.nodoomscroll.app.blocker.BlockRule
import com.nodoomscroll.app.blocker.DoomScrollRuleEngine
import com.nodoomscroll.app.blocker.FacebookReelsRule
import com.nodoomscroll.app.blocker.InstagramReelsRule
import com.nodoomscroll.app.blocker.ScreenSnapshot
import com.nodoomscroll.app.blocker.XShortsRule
import com.nodoomscroll.app.data.SettingsRepository
import com.nodoomscroll.app.data.SharedPreferencesSettingsRepository

/**
 * Monitors supported short-video apps and triggers Back/Home actions based on [DoomScrollRuleEngine].
 */
class DoomScrollAccessibilityService : AccessibilityService() {

  private lateinit var settingsRepository: SettingsRepository
  private val ruleEngine = DoomScrollRuleEngine()
  private var lastDebugLogUptimeMs: Long = 0L
  private var lastWindowPackage: String = ""
  private var lastWindowClassName: String = ""

  override fun onServiceConnected() {
    super.onServiceConnected()
    settingsRepository = SharedPreferencesSettingsRepository.getInstance(applicationContext)
    isConnected = true
    Log.i(TAG, "DoomScrollAccessibilityService connected")
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    if (!::settingsRepository.isInitialized) {
      settingsRepository = SharedPreferencesSettingsRepository.getInstance(applicationContext)
    }

    val activeRoot = rootInActiveWindow
    val packageName =
      activeRoot?.packageName?.toString()
        ?: event?.packageName?.toString()
        ?: return

    if (packageName != lastWindowPackage) {
      lastWindowPackage = packageName
      lastWindowClassName = ""
    }
    if (
      event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
        (event.packageName == null || event.packageName.toString() == packageName)
    ) {
      lastWindowClassName = event.className?.toString().orEmpty()
    }

    if (packageName !in ruleEngine.watchedPackages) return

    val settings = settingsRepository.currentSettings()
    val matchingRules = ruleEngine.rulesForPackage(packageName)
    if (matchingRules.none { it.isEnabled(settings) } && !settings.debugLoggingEnabled) return

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
        Log.i(TAG, "Blocked ${decision.platform} (${decision.reason}) action=BACK ok=$ok")
      }
      BlockAction.NAVIGATE_HOME -> {
        val ok = performGlobalAction(GLOBAL_ACTION_HOME)
        Log.i(TAG, "Blocked ${decision.platform} (${decision.reason}) action=HOME ok=$ok")
      }
      BlockAction.NONE -> Unit
    }
  }

  override fun onInterrupt() {
    Log.d(TAG, "DoomScrollAccessibilityService interrupted")
  }

  override fun onUnbind(intent: Intent?): Boolean {
    isConnected = false
    Log.i(TAG, "DoomScrollAccessibilityService unbound")
    return super.onUnbind(intent)
  }

  override fun onDestroy() {
    isConnected = false
    super.onDestroy()
  }

  private fun buildSnapshot(
    packageName: String,
    matchingRules: List<BlockRule>,
    event: AccessibilityEvent?,
    activeRoot: AccessibilityNodeInfo?,
    debugLoggingEnabled: Boolean,
  ): ScreenSnapshot {
    val candidateIds = matchingRules.flatMapTo(linkedSetOf()) { it.candidateViewIds }
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
        scanVisibleWindow(root = root, packageName = packageName)
      } else {
        null
      }

    if (scanResult != null) {
      visibleViewIds.addAll(scanResult.visibleIds)
      selectedTabHints.addAll(scanResult.selectedTabHints)
    }

    if (debugLoggingEnabled) {
      logDebugInspection(
        packageName = packageName,
        event = event,
        visibleIds = scanResult?.visibleIds ?: visibleViewIds.toList(),
        selectedTabHints = selectedTabHints,
        selectedLabels = scanResult?.selectedLabels.orEmpty(),
      )
    }

    return ScreenSnapshot(
      packageName = packageName,
      visibleViewIds = visibleViewIds,
      selectedTabHints = selectedTabHints,
      windowClassName = lastWindowClassName,
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
  ) {
    val now = SystemClock.uptimeMillis()
    if (now - lastDebugLogUptimeMs < DEBUG_LOG_INTERVAL_MS) return
    lastDebugLogUptimeMs = now

    Log.d(
      TAG,
      "Inspect pkg=$packageName cls=$lastWindowClassName event=${event?.eventType} " +
        "visibleIds=$visibleIds selectedLabels=$selectedLabels selectedHints=$selectedTabHints",
    )
  }

  private fun scanVisibleWindow(
    root: AccessibilityNodeInfo,
    packageName: String,
    maxDepth: Int = 12,
    maxNodes: Int = 120,
    maxIds: Int = 40,
    maxSelectedLabels: Int = 10,
  ): WindowScanResult {
    val collectedIds = linkedSetOf<String>()
    val selectedTabHints = linkedSetOf<String>()
    val selectedLabels = linkedSetOf<String>()
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
      }

      if (depth >= maxDepth) continue
      for (i in 0 until node.childCount) {
        val child = node.getChild(i) ?: continue
        queue.addLast(child to (depth + 1))
      }
    }

    return WindowScanResult(
      visibleIds = collectedIds.toList(),
      selectedTabHints = selectedTabHints,
      selectedLabels = selectedLabels.toList(),
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

  private data class WindowScanResult(
    val visibleIds: List<String>,
    val selectedTabHints: Set<String>,
    val selectedLabels: List<String>,
  )

  companion object {
    const val TAG: String = "NoDoomScroll"
    private const val INSTAGRAM_PACKAGE: String = "com.instagram.android"
    private const val DEBUG_LOG_INTERVAL_MS: Long = 1500L
    private const val MAX_TAB_LABEL_LENGTH: Int = 30
    private val X_VIDEO_TAB_LABELS: Set<String> =
      setOf("video", "videos", "videolar", "video tab", "videolar sekmesi")

    @Volatile
    var isConnected: Boolean = false
      private set
  }
}
