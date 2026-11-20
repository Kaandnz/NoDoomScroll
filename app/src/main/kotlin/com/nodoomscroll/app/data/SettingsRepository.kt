package com.nodoomscroll.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Provides reactive and synchronous access to user blocker preferences.
 */
interface SettingsRepository {
  val settings: StateFlow<BlockerSettings>

  fun currentSettings(): BlockerSettings

  fun setBlockYouTubeShorts(enabled: Boolean)

  fun setBlockInstagramReels(enabled: Boolean)

  fun setBlockTikTok(enabled: Boolean)

  fun setBlockFacebookReels(enabled: Boolean)

  fun setBlockXShorts(enabled: Boolean)

  fun setDebugLoggingEnabled(enabled: Boolean)
}

/**
 * [SettingsRepository] backed by [SharedPreferences] so the UI and accessibility service
 * share consistent state within the app process.
 */
class SharedPreferencesSettingsRepository(
  context: Context,
) : SettingsRepository {

  private val prefs: SharedPreferences =
    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  private val _settings = MutableStateFlow(readSettings())
  override val settings: StateFlow<BlockerSettings> = _settings.asStateFlow()

  // Keep a strong reference because SharedPreferences stores listeners weakly.
  private val preferenceChangeListener =
    SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
      _settings.value = readSettings()
    }

  init {
    prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
  }

  override fun currentSettings(): BlockerSettings = readSettings()

  override fun setBlockYouTubeShorts(enabled: Boolean) {
    updateBoolean(KEY_BLOCK_YOUTUBE_SHORTS, enabled)
  }

  override fun setBlockInstagramReels(enabled: Boolean) {
    updateBoolean(KEY_BLOCK_INSTAGRAM_REELS, enabled)
  }

  override fun setBlockTikTok(enabled: Boolean) {
    updateBoolean(KEY_BLOCK_TIKTOK, enabled)
  }

  override fun setBlockFacebookReels(enabled: Boolean) {
    updateBoolean(KEY_BLOCK_FACEBOOK_REELS, enabled)
  }

  override fun setBlockXShorts(enabled: Boolean) {
    updateBoolean(KEY_BLOCK_X_SHORTS, enabled)
  }

  override fun setDebugLoggingEnabled(enabled: Boolean) {
    updateBoolean(KEY_DEBUG_LOGGING_ENABLED, enabled)
  }

  private fun updateBoolean(key: String, value: Boolean) {
    prefs.edit().putBoolean(key, value).apply()
    _settings.value = readSettings()
  }

  private fun readSettings(): BlockerSettings =
    BlockerSettings(
      blockYouTubeShorts = prefs.getBoolean(KEY_BLOCK_YOUTUBE_SHORTS, true),
      blockInstagramReels = prefs.getBoolean(KEY_BLOCK_INSTAGRAM_REELS, true),
      blockTikTok = prefs.getBoolean(KEY_BLOCK_TIKTOK, true),
      blockFacebookReels = prefs.getBoolean(KEY_BLOCK_FACEBOOK_REELS, true),
      blockXShorts = prefs.getBoolean(KEY_BLOCK_X_SHORTS, true),
      debugLoggingEnabled = prefs.getBoolean(KEY_DEBUG_LOGGING_ENABLED, false),
    )

  companion object {
    private const val PREFS_NAME = "nodoomscroll_prefs"
    private const val KEY_BLOCK_YOUTUBE_SHORTS = "block_youtube_shorts"
    private const val KEY_BLOCK_INSTAGRAM_REELS = "block_instagram_reels"
    private const val KEY_BLOCK_TIKTOK = "block_tiktok"
    private const val KEY_BLOCK_FACEBOOK_REELS = "block_facebook_reels"
    private const val KEY_BLOCK_X_SHORTS = "block_x_shorts"
    private const val KEY_DEBUG_LOGGING_ENABLED = "debug_logging_enabled"

    @Volatile
    private var instance: SharedPreferencesSettingsRepository? = null

    fun getInstance(context: Context): SharedPreferencesSettingsRepository =
      instance ?: synchronized(this) {
        instance ?: SharedPreferencesSettingsRepository(context.applicationContext).also {
          instance = it
        }
      }
  }
}
