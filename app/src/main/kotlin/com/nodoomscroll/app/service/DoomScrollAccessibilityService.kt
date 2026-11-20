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
import com.nodoomscroll.app.blocker.InstagramReelsRule
import com.nodoomscroll.app.blocker.ScreenSnapshot
import com.nodoomscroll.app.data.SettingsRepository
import com.nodoomscroll.app.data.SharedPreferencesSettingsRepository

/**
 * Monitors supported short-video apps and triggers Back/Home actions based on [DoomScrollRuleEngine].
 */
class DoomScrollAccessibilityService : AccessibilityService() {

  private lateinit var settingsRepository: SettingsRepository
  private val ruleEngine = DoomScrollRuleEngine()
  private var lastDebugLogUptimeMs: Long = 0L

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

    val packageName =
      event?.packageName?.toString()
        ?: rootInActiveWindow?.packageName?.toString()
        ?: return

    if (packageName !in ruleEngine.watchedPackages) return

    val settings = settingsRepository.currentSettings()
    val matchingRules = ruleEngine.rulesForPackage(packageName)
    if (matchingRules.none { it.isEnabled(settings) } && !settings.debugLoggingEnabled) return

    val snapshot = buildSnapshot(packageName, matchingRules, event, settings.debugLoggingEnabled)
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
    debugLoggingEnabled: Boolean,
  ): ScreenSnapshot {
    val candidateIds = matchingRules.flatMapTo(linkedSetOf()) { it.candidateViewIds }
    val needsRoot =
      candidateIds.isNotEmpty() ||
        packageName == INSTAGRAM_PACKAGE ||
        debugLoggingEnabled

    if (!needsRoot) {
      return ScreenSnapshot(packageName = packageName)
    }

    val root = rootInActiveWindow ?: event?.source
    val visibleViewIds =
      if (root == null) {
        emptySet()
      } else {
        candidateIds.filterTo(linkedSetOf()) { viewId ->
          hasVisibleNode(root, viewId)
        }
      }

    val selectedTabHints =
      if (root != null && packageName == INSTAGRAM_PACKAGE && isClipsTabSelected(root)) {
        setOf(InstagramReelsRule.CLIPS_TAB_SELECTED_HINT)
      } else {
        emptySet()
      }

    if (debugLoggingEnabled) {
      logDebugInspection(packageName, event, root, selectedTabHints)
    }

    return ScreenSnapshot(
      packageName = packageName,
      visibleViewIds = visibleViewIds,
      selectedTabHints = selectedTabHints,
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
    root: AccessibilityNodeInfo?,
    selectedTabHints: Set<String>,
  ) {
    val now = SystemClock.uptimeMillis()
    if (now - lastDebugLogUptimeMs < DEBUG_LOG_INTERVAL_MS) return
    lastDebugLogUptimeMs = now

    val ids = collectVisibleViewIds(root)
    Log.d(
      TAG,
      "Inspect pkg=$packageName event=${event?.eventType} visibleIds=$ids selectedHints=$selectedTabHints",
    )
  }

  private fun collectVisibleViewIds(
    root: AccessibilityNodeInfo?,
    maxDepth: Int = 12,
    maxNodes: Int = 120,
    maxIds: Int = 40,
  ): List<String> {
    if (root == null) return emptyList()

    val collectedIds = linkedSetOf<String>()
    val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
    queue.addLast(root to 0)
    var visitedNodes = 0

    while (queue.isNotEmpty() && visitedNodes < maxNodes && collectedIds.size < maxIds) {
      val (node, depth) = queue.removeFirst()
      visitedNodes++

      val resourceName = node.viewIdResourceName
      if (!resourceName.isNullOrBlank() && node.isVisibleToUser) {
        collectedIds.add(resourceName)
      }

      if (depth >= maxDepth) continue
      for (i in 0 until node.childCount) {
        val child = node.getChild(i) ?: continue
        queue.addLast(child to (depth + 1))
      }
    }

    return collectedIds.toList()
  }

  companion object {
    const val TAG: String = "NoDoomScroll"
    private const val INSTAGRAM_PACKAGE: String = "com.instagram.android"
    private const val DEBUG_LOG_INTERVAL_MS: Long = 1500L

    @Volatile
    var isConnected: Boolean = false
      private set
  }
}
