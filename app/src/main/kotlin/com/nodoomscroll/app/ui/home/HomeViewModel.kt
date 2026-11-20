package com.nodoomscroll.app.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nodoomscroll.app.data.BlockerSettings
import com.nodoomscroll.app.data.SettingsRepository
import com.nodoomscroll.app.data.SharedPreferencesSettingsRepository
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
)

/**
 * Coordinates blocker toggle updates and accessibility service status checks for [HomeScreen].
 */
class HomeViewModel(
  private val settingsRepository: SettingsRepository,
  private val serviceEnabledProvider: () -> Boolean,
) : ViewModel() {

  private val isServiceEnabled = MutableStateFlow(serviceEnabledProvider())

  val uiState: StateFlow<HomeUiState> =
    combine(isServiceEnabled, settingsRepository.settings) { enabled, settings ->
        HomeUiState(isServiceEnabled = enabled, settings = settings)
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue =
          HomeUiState(
            isServiceEnabled = isServiceEnabled.value,
            settings = settingsRepository.currentSettings(),
          ),
      )

  fun refreshServiceStatus() {
    isServiceEnabled.value = serviceEnabledProvider()
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

  companion object {
    fun factory(context: Context): ViewModelProvider.Factory =
      object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
          val appContext = context.applicationContext
          return HomeViewModel(
            settingsRepository = SharedPreferencesSettingsRepository.getInstance(appContext),
            serviceEnabledProvider = {
              AccessibilityStatusHelper.isAccessibilityServiceEnabled(appContext) ||
                DoomScrollAccessibilityService.isConnected
            },
          ) as T
        }
      }
  }
}
