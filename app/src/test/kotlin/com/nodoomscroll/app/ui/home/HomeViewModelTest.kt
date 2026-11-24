package com.nodoomscroll.app.ui.home

import com.nodoomscroll.app.data.BlockerSettings
import com.nodoomscroll.app.data.ProtectionStats
import com.nodoomscroll.app.data.ScrollGuardAction
import com.nodoomscroll.app.data.ScrollGuardSensitivity
import com.nodoomscroll.app.data.SettingsRepository
import com.nodoomscroll.app.data.StatsRepository
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
  fun initialState_reflectsRepositoryServiceStatusAndStats() {
    val repo =
      FakeSettingsRepository(
        BlockerSettings(
          blockYouTubeShorts = true,
          blockInstagramReels = false,
          blockTikTok = true,
          debugLoggingEnabled = true,
        ),
      )
    val statsRepo =
      FakeStatsRepository(
        ProtectionStats(
          dateKey = "2026-11-24",
          todayShortVideoBlocks = 3,
          todayScrollBreaks = 1,
          totalBlocksAllTime = 12,
        ),
      )

    val viewModel =
      HomeViewModel(
        settingsRepository = repo,
        statsRepository = statsRepo,
        serviceEnabledProvider = { true },
      )

    assertTrue(viewModel.uiState.value.isServiceEnabled)
    assertEquals(repo.currentSettings(), viewModel.uiState.value.settings)
    assertEquals(statsRepo.currentStats(), viewModel.uiState.value.stats)
  }

  @Test
  fun toggleCallbacks_updateRepositoryAndUiState() {
    val repo = FakeSettingsRepository()
    val statsRepo = FakeStatsRepository()
    val viewModel =
      HomeViewModel(
        settingsRepository = repo,
        statsRepository = statsRepo,
        serviceEnabledProvider = { false },
      )

    viewModel.onToggleYouTubeShorts(false)
    viewModel.onToggleInstagramReels(false)
    viewModel.onToggleTikTok(false)
    viewModel.onToggleFacebookReels(false)
    viewModel.onToggleXShorts(false)
    viewModel.onToggleDebugLogging(true)
    viewModel.onToggleContinuousScrollGuard(false)
    viewModel.onSelectScrollGuardSensitivity(ScrollGuardSensitivity.STRICT)
    viewModel.onSelectScrollGuardAction(ScrollGuardAction.NAVIGATE_HOME)

    val expected =
      BlockerSettings(
        blockYouTubeShorts = false,
        blockInstagramReels = false,
        blockTikTok = false,
        blockFacebookReels = false,
        blockXShorts = false,
        debugLoggingEnabled = true,
        continuousScrollGuardEnabled = false,
        scrollGuardSensitivity = ScrollGuardSensitivity.STRICT,
        scrollGuardAction = ScrollGuardAction.NAVIGATE_HOME,
      )
    assertEquals(expected, repo.currentSettings())
    assertEquals(expected, viewModel.uiState.value.settings)
  }

  @Test
  fun refreshServiceStatus_updatesIsServiceEnabledAndRefreshesStatsDate() {
    var serviceEnabled = false
    val repo = FakeSettingsRepository()
    val statsRepo = FakeStatsRepository()
    val viewModel =
      HomeViewModel(
        settingsRepository = repo,
        statsRepository = statsRepo,
        serviceEnabledProvider = { serviceEnabled },
      )

    assertFalse(viewModel.uiState.value.isServiceEnabled)

    serviceEnabled = true
    statsRepo.recordShortVideoBlock()
    statsRepo.recordScrollBreak()
    viewModel.refreshServiceStatus()

    assertTrue(viewModel.uiState.value.isServiceEnabled)
    assertTrue(statsRepo.refreshCalled)
    assertEquals(1, viewModel.uiState.value.stats.todayShortVideoBlocks)
    assertEquals(1, viewModel.uiState.value.stats.todayScrollBreaks)
    assertEquals(2, viewModel.uiState.value.stats.todayTotal)
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

  override fun setContinuousScrollGuardEnabled(enabled: Boolean) {
    _settings.update { it.copy(continuousScrollGuardEnabled = enabled) }
  }

  override fun setScrollGuardSensitivity(sensitivity: ScrollGuardSensitivity) {
    _settings.update { it.copy(scrollGuardSensitivity = sensitivity) }
  }

  override fun setScrollGuardAction(action: ScrollGuardAction) {
    _settings.update { it.copy(scrollGuardAction = action) }
  }
}

private class FakeStatsRepository(
  initial: ProtectionStats = ProtectionStats(dateKey = "2026-11-24"),
) : StatsRepository {

  private val _stats = MutableStateFlow(initial)
  override val stats: StateFlow<ProtectionStats> = _stats.asStateFlow()
  var refreshCalled: Boolean = false

  override fun currentStats(): ProtectionStats = _stats.value

  override fun recordShortVideoBlock() {
    _stats.update {
      it.copy(
        todayShortVideoBlocks = it.todayShortVideoBlocks + 1,
        totalBlocksAllTime = it.totalBlocksAllTime + 1,
      )
    }
  }

  override fun recordScrollBreak() {
    _stats.update {
      it.copy(
        todayScrollBreaks = it.todayScrollBreaks + 1,
        totalBlocksAllTime = it.totalBlocksAllTime + 1,
      )
    }
  }

  override fun refreshIfDateChanged() {
    refreshCalled = true
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
