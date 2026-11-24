package com.nodoomscroll.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nodoomscroll.app.R
import com.nodoomscroll.app.data.BlockerSettings
import com.nodoomscroll.app.data.ProtectionStats
import com.nodoomscroll.app.data.ScrollGuardAction
import com.nodoomscroll.app.data.ScrollGuardSensitivity
import com.nodoomscroll.app.ui.theme.NoDoomScrollTheme

/**
 * Stateful entry point that binds [HomeViewModel] and lifecycle resume refreshes to [HomeScreen].
 */
@Composable
fun HomeRoute(
  viewModel: HomeViewModel,
  onOpenAccessibilitySettings: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
    viewModel.refreshServiceStatus()
  }

  HomeScreen(
    uiState = uiState,
    onOpenAccessibilitySettings = onOpenAccessibilitySettings,
    onToggleYouTubeShorts = viewModel::onToggleYouTubeShorts,
    onToggleInstagramReels = viewModel::onToggleInstagramReels,
    onToggleTikTok = viewModel::onToggleTikTok,
    onToggleFacebookReels = viewModel::onToggleFacebookReels,
    onToggleXShorts = viewModel::onToggleXShorts,
    onToggleContinuousScrollGuard = viewModel::onToggleContinuousScrollGuard,
    onSelectScrollGuardSensitivity = viewModel::onSelectScrollGuardSensitivity,
    onSelectScrollGuardAction = viewModel::onSelectScrollGuardAction,
    onToggleDebugLogging = viewModel::onToggleDebugLogging,
    modifier = modifier,
  )
}

/**
 * Stateless control panel for enabling the accessibility service and toggling platform rules.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
  uiState: HomeUiState,
  onOpenAccessibilitySettings: () -> Unit,
  onToggleYouTubeShorts: (Boolean) -> Unit,
  onToggleInstagramReels: (Boolean) -> Unit,
  onToggleTikTok: (Boolean) -> Unit,
  onToggleFacebookReels: (Boolean) -> Unit,
  onToggleXShorts: (Boolean) -> Unit,
  onToggleContinuousScrollGuard: (Boolean) -> Unit,
  onSelectScrollGuardSensitivity: (ScrollGuardSensitivity) -> Unit,
  onSelectScrollGuardAction: (ScrollGuardAction) -> Unit,
  onToggleDebugLogging: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  Scaffold(
    modifier = modifier.fillMaxSize(),
    topBar = {
      TopAppBar(
        title = { Text(text = stringResource(R.string.app_name)) },
      )
    },
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      ServiceStatusCard(
        isServiceEnabled = uiState.isServiceEnabled,
        onOpenAccessibilitySettings = onOpenAccessibilitySettings,
      )

      DailyStatsCard(stats = uiState.stats)

      SectionCard(title = "Engellenecek Akışlar") {
        SettingToggleRow(
          title = "YouTube Shorts",
          subtitle = "Normal videolar açık kalır, sadece Shorts akışı kapatılır.",
          checked = uiState.settings.blockYouTubeShorts,
          onCheckedChange = onToggleYouTubeShorts,
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        SettingToggleRow(
          title = "Instagram Reels",
          subtitle = "Ana akış ve DM'ler açık kalır, sadece Reels kapatılır.",
          checked = uiState.settings.blockInstagramReels,
          onCheckedChange = onToggleInstagramReels,
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        SettingToggleRow(
          title = "TikTok",
          subtitle = "TikTok açıldığında ana ekrana döndürür.",
          checked = uiState.settings.blockTikTok,
          onCheckedChange = onToggleTikTok,
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        SettingToggleRow(
          title = "Facebook Reels",
          subtitle = "Ana akış açık kalır; Reels sekmesi ve akıştan açılan Reels kapatılır.",
          checked = uiState.settings.blockFacebookReels,
          onCheckedChange = onToggleFacebookReels,
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        SettingToggleRow(
          title = "X (Twitter) Shorts",
          subtitle = "Ana akış açık kalır, sadece tam ekran dikey video akışı kapatılır.",
          checked = uiState.settings.blockXShorts,
          onCheckedChange = onToggleXShorts,
        )
      }

      SectionCard(title = "Sürekli Kaydırma Koruması") {
        SettingToggleRow(
          title = "Sonsuz Kaydırma Freni",
          subtitle = "Sosyal medya akışlarında uzun süre durmadan aşağı kaydırdığında mola verdirir.",
          checked = uiState.settings.continuousScrollGuardEnabled,
          onCheckedChange = onToggleContinuousScrollGuard,
        )

        if (uiState.settings.continuousScrollGuardEnabled) {
          HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text = "Hassasiyet",
              style = MaterialTheme.typography.bodyLarge,
              fontWeight = FontWeight.Medium,
            )
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              ScrollGuardSensitivity.entries.forEach { preset ->
                FilterChip(
                  selected = uiState.settings.scrollGuardSensitivity == preset,
                  onClick = { onSelectScrollGuardSensitivity(preset) },
                  label = { Text(text = sensitivityLabel(preset)) },
                )
              }
            }
            Text(
              text = sensitivityDescription(uiState.settings.scrollGuardSensitivity),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text = "Müdahale Yöntemi",
              style = MaterialTheme.typography.bodyLarge,
              fontWeight = FontWeight.Medium,
            )
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              ScrollGuardAction.entries.forEach { action ->
                FilterChip(
                  selected = uiState.settings.scrollGuardAction == action,
                  onClick = { onSelectScrollGuardAction(action) },
                  label = { Text(text = actionLabel(action)) },
                )
              }
            }
            Text(
              text = actionDescription(uiState.settings.scrollGuardAction),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      SectionCard(title = "Kalibrasyon") {
        SettingToggleRow(
          title = "Debug Log Modu (Logcat)",
          subtitle = "Test sırasında ekrandaki bileşen ID'lerini, Facebook yapısal sinyallerini ve kaydırma sayacını Logcat'e (NoDoomScroll) yazar.",
          checked = uiState.settings.debugLoggingEnabled,
          onCheckedChange = onToggleDebugLogging,
        )
      }
    }
  }
}

@Composable
private fun DailyStatsCard(stats: ProtectionStats) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Bugünkü Koruma",
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.primary,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          text = "Tüm zamanlar: ${stats.totalBlocksAllTime}",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        StatMetricColumn(
          label = "Kısa Video",
          value = stats.todayShortVideoBlocks,
          modifier = Modifier.weight(1f),
        )
        StatMetricColumn(
          label = "Kaydırma Molası",
          value = stats.todayScrollBreaks,
          modifier = Modifier.weight(1f),
        )
        StatMetricColumn(
          label = "Toplam",
          value = stats.todayTotal,
          modifier = Modifier.weight(1f),
        )
      }
    }
  }
}

@Composable
private fun StatMetricColumn(
  label: String,
  value: Int,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    Text(
      text = value.toString(),
      style = MaterialTheme.typography.headlineSmall,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.primary,
    )
    Text(
      text = label,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

private fun sensitivityLabel(sensitivity: ScrollGuardSensitivity): String =
  when (sensitivity) {
    ScrollGuardSensitivity.STRICT -> "Sıkı"
    ScrollGuardSensitivity.BALANCED -> "Dengeli"
    ScrollGuardSensitivity.RELAXED -> "Esnek"
  }

private fun sensitivityDescription(sensitivity: ScrollGuardSensitivity): String =
  when (sensitivity) {
    ScrollGuardSensitivity.STRICT -> "12 kaydırma veya 45 sn kesintisiz kaydırmada devreye girer."
    ScrollGuardSensitivity.BALANCED -> "20 kaydırma veya 90 sn kesintisiz kaydırmada devreye girer."
    ScrollGuardSensitivity.RELAXED -> "35 kaydırma veya 3 dk kesintisiz kaydırmada devreye girer."
  }

private fun actionLabel(action: ScrollGuardAction): String =
  when (action) {
    ScrollGuardAction.BREAK_OVERLAY -> "Mola Ekranı"
    ScrollGuardAction.NAVIGATE_HOME -> "Ana Ekrana Dön"
  }

private fun actionDescription(action: ScrollGuardAction): String =
  when (action) {
    ScrollGuardAction.BREAK_OVERLAY ->
      "5 saniyelik nefes molası gösterir; istersen 2 dk daha izin verebilirsin."
    ScrollGuardAction.NAVIGATE_HOME ->
      "Eşik aşıldığında doğrudan telefonun ana ekranına döndürür."
  }

@Composable
private fun ServiceStatusCard(
  isServiceEnabled: Boolean,
  onOpenAccessibilitySettings: () -> Unit,
) {
  val containerColor =
    if (isServiceEnabled) {
      MaterialTheme.colorScheme.secondaryContainer
    } else {
      MaterialTheme.colorScheme.errorContainer
    }
  val contentColor =
    if (isServiceEnabled) {
      MaterialTheme.colorScheme.onSecondaryContainer
    } else {
      MaterialTheme.colorScheme.onErrorContainer
    }

  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(
      containerColor = containerColor,
      contentColor = contentColor,
    ),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        text = if (isServiceEnabled) "Koruma Servisi Aktif" else "Koruma Servisi Kapalı",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
      )
      Text(
        text =
          if (isServiceEnabled) {
            "Seçili kısa video akışları otomatik olarak engelleniyor."
          } else {
            "Çalışması için Ayarlar → Erişilebilirlik → Yüklü uygulamalar → NoDoomScroll yolundan izni aç."
          },
        style = MaterialTheme.typography.bodyMedium,
      )
      Spacer(modifier = Modifier.height(4.dp))
      if (isServiceEnabled) {
        OutlinedButton(onClick = onOpenAccessibilitySettings) {
          Text(text = "Erişilebilirlik Ayarlarını Aç")
        }
      } else {
        Button(onClick = onOpenAccessibilitySettings) {
          Text(text = "Erişilebilirlik Ayarlarını Aç")
        }
      }
    }
  }
}

@Composable
private fun SectionCard(
  title: String,
  content: @Composable () -> Unit,
) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.fillMaxWidth()) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp),
      )
      content()
    }
  }
}

@Composable
private fun SettingToggleRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .toggleable(
        value = checked,
        onValueChange = onCheckedChange,
        role = Role.Switch,
      )
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium,
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Switch(
      checked = checked,
      onCheckedChange = null,
    )
  }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun HomeScreenDisabledPreview() {
  NoDoomScrollTheme {
    HomeScreen(
      uiState = HomeUiState(isServiceEnabled = false, settings = BlockerSettings()),
      onOpenAccessibilitySettings = {},
      onToggleYouTubeShorts = {},
      onToggleInstagramReels = {},
      onToggleTikTok = {},
      onToggleFacebookReels = {},
      onToggleXShorts = {},
      onToggleContinuousScrollGuard = {},
      onSelectScrollGuardSensitivity = {},
      onSelectScrollGuardAction = {},
      onToggleDebugLogging = {},
    )
  }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun HomeScreenEnabledPreview() {
  NoDoomScrollTheme {
    HomeScreen(
      uiState =
        HomeUiState(
          isServiceEnabled = true,
          settings = BlockerSettings(debugLoggingEnabled = true),
          stats =
            ProtectionStats(
              dateKey = "2026-11-24",
              todayShortVideoBlocks = 4,
              todayScrollBreaks = 2,
              totalBlocksAllTime = 19,
            ),
        ),
      onOpenAccessibilitySettings = {},
      onToggleYouTubeShorts = {},
      onToggleInstagramReels = {},
      onToggleTikTok = {},
      onToggleFacebookReels = {},
      onToggleXShorts = {},
      onToggleContinuousScrollGuard = {},
      onSelectScrollGuardSensitivity = {},
      onSelectScrollGuardAction = {},
      onToggleDebugLogging = {},
    )
  }
}
