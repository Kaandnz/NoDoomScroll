package com.nodoomscroll.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nodoomscroll.app.service.AccessibilityStatusHelper
import com.nodoomscroll.app.ui.home.HomeRoute
import com.nodoomscroll.app.ui.home.HomeViewModel
import com.nodoomscroll.app.ui.theme.NoDoomScrollTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    enableEdgeToEdge()
    setContent {
      NoDoomScrollTheme {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background,
        ) {
          val homeViewModel: HomeViewModel =
            viewModel(factory = HomeViewModel.factory(applicationContext))
          HomeRoute(
            viewModel = homeViewModel,
            onOpenAccessibilitySettings = {
              AccessibilityStatusHelper.openAccessibilitySettings(this@MainActivity)
            },
          )
        }
      }
    }
  }
}
