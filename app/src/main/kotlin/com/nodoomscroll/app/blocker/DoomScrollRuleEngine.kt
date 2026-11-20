package com.nodoomscroll.app.blocker

import com.nodoomscroll.app.data.BlockerSettings

/**
 * Evaluates registered [BlockRule]s against a [ScreenSnapshot] and enforces [CooldownGate].
 */
class DoomScrollRuleEngine(
  val rules: List<BlockRule> = defaultRules(),
  private val cooldownGate: CooldownGate = CooldownGate(),
) {
  val watchedPackages: Set<String> = rules.flatMap { it.supportedPackages }.toSet()

  fun rulesForPackage(packageName: String): List<BlockRule> =
    rules.filter { packageName in it.supportedPackages }

  fun evaluate(snapshot: ScreenSnapshot, settings: BlockerSettings): BlockDecision {
    val activeRules = rulesForPackage(snapshot.packageName).filter { it.isEnabled(settings) }
    for (rule in activeRules) {
      val decision = rule.evaluate(snapshot) ?: continue
      val platform = decision.platform ?: return decision
      return if (cooldownGate.tryAcquire(platform)) {
        decision
      } else {
        BlockDecision(action = BlockAction.NONE, platform = platform, reason = "cooldown")
      }
    }
    return BlockDecision(action = BlockAction.NONE)
  }

  companion object {
    fun defaultRules(): List<BlockRule> =
      listOf(
        YouTubeShortsRule,
        InstagramReelsRule,
        TikTokRule,
        FacebookReelsRule,
        XShortsRule,
      )
  }
}
