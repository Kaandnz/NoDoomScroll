package com.nodoomscroll.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
    onToggleDebugLogging = viewModel::onToggleDebugLogging,
    modifier = modifier,
  )
}

/**
 * Stateless control panel for enabling the accessibility service and toggling platform rules.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
  uiState: HomeUiState,
  onOpenAccessibilitySettings: () -> Unit,
  onToggleYouTubeShorts: (Boolean) -> Unit,
  onToggleInstagramReels: (Boolean) -> Unit,
  onToggleTikTok: (Boolean) -> Unit,
  onToggleFacebookReels: (Boolean) -> Unit,
  onToggleXShorts: (Boolean) -> Unit,
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
          subtitle = "Ana akış açık kalır, sadece Facebook Reels/Shorts kapatılır.",
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

      SectionCard(title = "Kalibrasyon") {
        SettingToggleRow(
          title = "Debug Log Modu (Logcat)",
          subtitle = "Test sırasında ekrandaki bileşen ID'lerini Logcat'e (NoDoomScroll) yazar.",
          checked = uiState.settings.debugLoggingEnabled,
          onCheckedChange = onToggleDebugLogging,
        )
      }
    }
  }
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
        ),
      onOpenAccessibilitySettings = {},
      onToggleYouTubeShorts = {},
      onToggleInstagramReels = {},
      onToggleTikTok = {},
      onToggleFacebookReels = {},
      onToggleXShorts = {},
      onToggleDebugLogging = {},
    )
  }
}
