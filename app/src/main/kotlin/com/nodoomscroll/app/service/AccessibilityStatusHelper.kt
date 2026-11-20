package com.nodoomscroll.app.service

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

/**
 * Checks whether [DoomScrollAccessibilityService] is enabled and navigates to system settings.
 */
object AccessibilityStatusHelper {

  fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val targetComponent = ComponentName(context, DoomScrollAccessibilityService::class.java)

    val accessibilityManager =
      context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    val enabledServices =
      accessibilityManager
        ?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        .orEmpty()

    val isEnabledInManager =
      enabledServices.any { info ->
        val serviceInfo = info.resolveInfo?.serviceInfo ?: return@any false
        serviceInfo.packageName == targetComponent.packageName &&
          serviceInfo.name == targetComponent.className
      }
    if (isEnabledInManager) return true

    val enabledSetting =
      Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
      ) ?: return false

    return enabledSetting
      .split(':')
      .mapNotNull { ComponentName.unflattenFromString(it.trim()) }
      .any { it == targetComponent }
  }

  fun openAccessibilitySettings(context: Context) {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    if (context !is Activity) {
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
  }
}
