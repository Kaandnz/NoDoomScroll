package com.nodoomscroll.app.data

/**
 * Persisted toggle configuration for the blocker service and debug calibration.
 */
data class BlockerSettings(
  val blockYouTubeShorts: Boolean = true,
  val blockInstagramReels: Boolean = true,
  val blockTikTok: Boolean = true,
  val debugLoggingEnabled: Boolean = false,
)
