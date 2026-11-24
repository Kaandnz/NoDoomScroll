package com.nodoomscroll.app.service

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory ring buffer storing recent calibration diagnostics so users can inspect and copy
 * live accessibility signals directly inside the app without a PC/adb connection.
 */
object DebugDiagnosticsLog {
  private const val MAX_ENTRIES = 25
  private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
  private val entries = ArrayDeque<String>(MAX_ENTRIES)
  private val _logs = MutableStateFlow<List<String>>(emptyList())
  val logs: StateFlow<List<String>> = _logs.asStateFlow()

  @Synchronized
  fun append(line: String) {
    val timestamp = LocalTime.now().format(timeFormatter)
    val formatted = "[$timestamp] $line"
    if (entries.size >= MAX_ENTRIES) {
      entries.removeFirst()
    }
    entries.addLast(formatted)
    _logs.value = entries.toList()
  }

  @Synchronized
  fun clear() {
    entries.clear()
    _logs.value = emptyList()
  }

  @Synchronized
  fun asShareableText(): String = entries.joinToString("\n")
}
