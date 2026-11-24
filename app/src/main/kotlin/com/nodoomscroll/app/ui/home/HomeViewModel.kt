package com.nodoomscroll.app.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nodoomscroll.app.data.BlockerSettings
import com.nodoomscroll.app.data.ProtectionStats
import com.nodoomscroll.app.data.ScrollGuardAction
import com.nodoomscroll.app.data.ScrollGuardSensitivity
import com.nodoomscroll.app.data.SettingsRepository
import com.nodoomscroll.app.data.SharedPreferencesSettingsRepository
import com.nodoomscroll.app.data.SharedPreferencesStatsRepository
import com.nodoomscroll.app.data.StatsRepository
import com.nodoomscroll.app.service.AccessibilityStatusHelper
import com.nodoomscroll.app.service.DoomScrollAccessibilityService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Immutable UI state for [HomeScreen].
 */
data class HomeUiState(
  val isServiceEnabled: Boolean = false,
  val settings: BlockerSettings = BlockerSettings(),
  val stats: ProtectionStats = ProtectionStats(),
)

/**
 * Coordinates blocker toggle updates, continuous scroll guard settings, and stats for [HomeScreen].
 */
class HomeViewModel(
  private val settingsRepository: SettingsRepository,
  private val statsRepository: StatsRepository,
  private val serviceEnabledProvider: () -> Boolean,
) : ViewModel() {

  private val isServiceEnabled = MutableStateFlow(serviceEnabledProvider())

  val uiState: StateFlow<HomeUiState> =
    combine(isServiceEnabled, settingsRepository.settings, statsRepository.stats) {
        enabled,
        settings,
        stats ->
        HomeUiState(isServiceEnabled = enabled, settings = settings, stats = stats)
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue =
          HomeUiState(
            isServiceEnabled = isServiceEnabled.value,
            settings = settingsRepository.currentSettings(),
            stats = statsRepository.currentStats(),
          ),
      )

  fun refreshServiceStatus() {
    isServiceEnabled.value = serviceEnabledProvider()
    statsRepository.refreshIfDateChanged()
  }

  fun onToggleYouTubeShorts(enabled: Boolean) {
    settingsRepository.setBlockYouTubeShorts(enabled)
  }

  fun onToggleInstagramReels(enabled: Boolean) {
    settingsRepository.setBlockInstagramReels(enabled)
  }

  fun onToggleTikTok(enabled: Boolean) {
    settingsRepository.setBlockTikTok(enabled)
  }

  fun onToggleFacebookReels(enabled: Boolean) {
    settingsRepository.setBlockFacebookReels(enabled)
  }

  fun onToggleXShorts(enabled: Boolean) {
    settingsRepository.setBlockXShorts(enabled)
  }

  fun onToggleDebugLogging(enabled: Boolean) {
    settingsRepository.setDebugLoggingEnabled(enabled)
  }

  fun onToggleContinuousScrollGuard(enabled: Boolean) {
    settingsRepository.setContinuousScrollGuardEnabled(enabled)
  }

  fun onSelectScrollGuardSensitivity(sensitivity: ScrollGuardSensitivity) {
    settingsRepository.setScrollGuardSensitivity(sensitivity)
  }

  fun onSelectScrollGuardAction(action: ScrollGuardAction) {
    settingsRepository.setScrollGuardAction(action)
  }

  companion object {
    fun factory(context: Context): ViewModelProvider.Factory =
      object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
          val appContext = context.applicationContext
          return HomeViewModel(
            settingsRepository = SharedPreferencesSettingsRepository.getInstance(appContext),
            statsRepository = SharedPreferencesStatsRepository.getInstance(appContext),
            serviceEnabledProvider = {
              AccessibilityStatusHelper.isAccessibilityServiceEnabled(appContext) ||
                DoomScrollAccessibilityService.isConnected
            },
          ) as T
        }
      }
  }
}
