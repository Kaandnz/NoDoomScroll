package com.nodoomscroll.app.data

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Local-only daily and cumulative protection metrics.
 */
data class ProtectionStats(
  val dateKey: String = "",
  val todayShortVideoBlocks: Int = 0,
  val todayScrollBreaks: Int = 0,
  val totalBlocksAllTime: Int = 0,
) {
  val todayTotal: Int
    get() = todayShortVideoBlocks + todayScrollBreaks
}

/**
 * Provides reactive and synchronous access to local protection counters.
 */
interface StatsRepository {
  val stats: StateFlow<ProtectionStats>

  fun currentStats(): ProtectionStats

  fun recordShortVideoBlock()

  fun recordScrollBreak()

  fun refreshIfDateChanged()
}

/**
 * [StatsRepository] backed by [SharedPreferences] with automatic midnight rollover.
 */
class SharedPreferencesStatsRepository(
  context: Context,
  private val dateKeyProvider: () -> String = { LocalDate.now().toString() },
) : StatsRepository {

  private val prefs: SharedPreferences =
    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  private val _stats = MutableStateFlow(readNormalizedStats())
  override val stats: StateFlow<ProtectionStats> = _stats.asStateFlow()

  // Keep a strong reference because SharedPreferences stores listeners weakly.
  private val preferenceChangeListener =
    SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
      _stats.value = readNormalizedStats()
    }

  init {
    prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
  }

  @Synchronized
  override fun currentStats(): ProtectionStats = readNormalizedStats()

  @Synchronized
  override fun recordShortVideoBlock() {
    val current = ensureTodayStats()
    val updated =
      current.copy(
        todayShortVideoBlocks = current.todayShortVideoBlocks + 1,
        totalBlocksAllTime = current.totalBlocksAllTime + 1,
      )
    persistStats(updated)
  }

  @Synchronized
  override fun recordScrollBreak() {
    val current = ensureTodayStats()
    val updated =
      current.copy(
        todayScrollBreaks = current.todayScrollBreaks + 1,
        totalBlocksAllTime = current.totalBlocksAllTime + 1,
      )
    persistStats(updated)
  }

  @Synchronized
  override fun refreshIfDateChanged() {
    val normalized = ensureTodayStats()
    _stats.value = normalized
  }

  private fun ensureTodayStats(): ProtectionStats {
    val today = dateKeyProvider()
    val storedDate = prefs.getString(KEY_DATE_KEY, null)
    val totalAllTime = prefs.getInt(KEY_TOTAL_BLOCKS_ALL_TIME, 0)
    if (storedDate != today) {
      val rolledOver =
        ProtectionStats(
          dateKey = today,
          todayShortVideoBlocks = 0,
          todayScrollBreaks = 0,
          totalBlocksAllTime = totalAllTime,
        )
      prefs
        .edit()
        .putString(KEY_DATE_KEY, today)
        .putInt(KEY_TODAY_SHORT_VIDEO_BLOCKS, 0)
        .putInt(KEY_TODAY_SCROLL_BREAKS, 0)
        .apply()
      return rolledOver
    }
    return ProtectionStats(
      dateKey = today,
      todayShortVideoBlocks = prefs.getInt(KEY_TODAY_SHORT_VIDEO_BLOCKS, 0),
      todayScrollBreaks = prefs.getInt(KEY_TODAY_SCROLL_BREAKS, 0),
      totalBlocksAllTime = totalAllTime,
    )
  }

  private fun readNormalizedStats(): ProtectionStats {
    val today = dateKeyProvider()
    val storedDate = prefs.getString(KEY_DATE_KEY, null)
    val totalAllTime = prefs.getInt(KEY_TOTAL_BLOCKS_ALL_TIME, 0)
    return if (storedDate == today) {
      ProtectionStats(
        dateKey = today,
        todayShortVideoBlocks = prefs.getInt(KEY_TODAY_SHORT_VIDEO_BLOCKS, 0),
        todayScrollBreaks = prefs.getInt(KEY_TODAY_SCROLL_BREAKS, 0),
        totalBlocksAllTime = totalAllTime,
      )
    } else {
      ProtectionStats(
        dateKey = today,
        todayShortVideoBlocks = 0,
        todayScrollBreaks = 0,
        totalBlocksAllTime = totalAllTime,
      )
    }
  }

  private fun persistStats(updated: ProtectionStats) {
    prefs
      .edit()
      .putString(KEY_DATE_KEY, updated.dateKey)
      .putInt(KEY_TODAY_SHORT_VIDEO_BLOCKS, updated.todayShortVideoBlocks)
      .putInt(KEY_TODAY_SCROLL_BREAKS, updated.todayScrollBreaks)
      .putInt(KEY_TOTAL_BLOCKS_ALL_TIME, updated.totalBlocksAllTime)
      .apply()
    _stats.value = updated
  }

  companion object {
    private const val PREFS_NAME = "nodoomscroll_stats"
    private const val KEY_DATE_KEY = "date_key"
    private const val KEY_TODAY_SHORT_VIDEO_BLOCKS = "today_short_video_blocks"
    private const val KEY_TODAY_SCROLL_BREAKS = "today_scroll_breaks"
    private const val KEY_TOTAL_BLOCKS_ALL_TIME = "total_blocks_all_time"

    @Volatile
    private var instance: SharedPreferencesStatsRepository? = null

    fun getInstance(context: Context): SharedPreferencesStatsRepository =
      instance ?: synchronized(this) {
        instance ?: SharedPreferencesStatsRepository(context.applicationContext).also {
          instance = it
        }
      }
  }
}
