package com.nodoomscroll.app.blocker

import java.util.EnumMap

/**
 * Rate-limits blocking actions per [Platform] so consecutive accessibility events
 * do not trigger a loop of repeated Back/Home actions.
 */
class CooldownGate(
  private val cooldownMs: Long = 1200L,
  private val clockMs: () -> Long = System::currentTimeMillis,
) {
  private val lastTriggerByPlatform = EnumMap<Platform, Long>(Platform::class.java)

  @Synchronized
  fun tryAcquire(platform: Platform): Boolean {
    val now = clockMs()
    val previous = lastTriggerByPlatform[platform]
    if (previous != null && now - previous < cooldownMs) {
      return false
    }
    lastTriggerByPlatform[platform] = now
    return true
  }

  @Synchronized
  fun reset() {
    lastTriggerByPlatform.clear()
  }
}
