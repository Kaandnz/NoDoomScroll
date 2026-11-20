package com.nodoomscroll.app.ui.home

import com.nodoomscroll.app.data.BlockerSettings
import com.nodoomscroll.app.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

  @get:Rule
  val mainDispatcherRule = MainDispatcherRule()

  @Test
  fun initialState_reflectsRepositoryAndServiceStatus() {
    val repo =
      FakeSettingsRepository(
        BlockerSettings(
          blockYouTubeShorts = true,
          blockInstagramReels = false,
          blockTikTok = true,
          debugLoggingEnabled = true,
        ),
      )

    val viewModel = HomeViewModel(settingsRepository = repo, serviceEnabledProvider = { true })

    assertTrue(viewModel.uiState.value.isServiceEnabled)
    assertEquals(repo.currentSettings(), viewModel.uiState.value.settings)
  }

  @Test
  fun toggleCallbacks_updateRepositoryAndUiState() {
    val repo = FakeSettingsRepository()
    val viewModel = HomeViewModel(settingsRepository = repo, serviceEnabledProvider = { false })

    viewModel.onToggleYouTubeShorts(false)
    viewModel.onToggleInstagramReels(false)
    viewModel.onToggleTikTok(false)
    viewModel.onToggleFacebookReels(false)
    viewModel.onToggleXShorts(false)
    viewModel.onToggleDebugLogging(true)

    val expected =
      BlockerSettings(
        blockYouTubeShorts = false,
        blockInstagramReels = false,
        blockTikTok = false,
        blockFacebookReels = false,
        blockXShorts = false,
        debugLoggingEnabled = true,
      )
    assertEquals(expected, repo.currentSettings())
    assertEquals(expected, viewModel.uiState.value.settings)
  }

  @Test
  fun refreshServiceStatus_updatesIsServiceEnabled() {
    var serviceEnabled = false
    val repo = FakeSettingsRepository()
    val viewModel = HomeViewModel(settingsRepository = repo, serviceEnabledProvider = { serviceEnabled })

    assertFalse(viewModel.uiState.value.isServiceEnabled)

    serviceEnabled = true
    viewModel.refreshServiceStatus()

    assertTrue(viewModel.uiState.value.isServiceEnabled)
  }
}

private class FakeSettingsRepository(
  initial: BlockerSettings = BlockerSettings(),
) : SettingsRepository {

  private val _settings = MutableStateFlow(initial)
  override val settings: StateFlow<BlockerSettings> = _settings.asStateFlow()

  override fun currentSettings(): BlockerSettings = _settings.value

  override fun setBlockYouTubeShorts(enabled: Boolean) {
    _settings.update { it.copy(blockYouTubeShorts = enabled) }
  }

  override fun setBlockInstagramReels(enabled: Boolean) {
    _settings.update { it.copy(blockInstagramReels = enabled) }
  }

  override fun setBlockTikTok(enabled: Boolean) {
    _settings.update { it.copy(blockTikTok = enabled) }
  }

  override fun setBlockFacebookReels(enabled: Boolean) {
    _settings.update { it.copy(blockFacebookReels = enabled) }
  }

  override fun setBlockXShorts(enabled: Boolean) {
    _settings.update { it.copy(blockXShorts = enabled) }
  }

  override fun setDebugLoggingEnabled(enabled: Boolean) {
    _settings.update { it.copy(debugLoggingEnabled = enabled) }
  }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
  private val testDispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
  override fun starting(description: Description) {
    Dispatchers.setMain(testDispatcher)
  }

  override fun finished(description: Description) {
    Dispatchers.resetMain()
  }
}
