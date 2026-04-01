package com.x695c.tuner.data

import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Activity Logger for recording all user actions for debugging and development.
 * Thread-safe singleton implementation.
 */
object ActivityLogger {
    private val logs = CopyOnWriteArrayList<LogEntry>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    data class LogEntry(
        val timestamp: Long,
        val action: String,
        val details: String,
        val screen: String
    )

    fun log(screen: String, action: String, details: String = "") {
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            action = action,
            details = details,
            screen = screen
        )
        logs.add(entry)
    }

    fun logNavigation(from: String, to: String) {
        log(from, "NAVIGATE", "From: $from -> To: $to")
    }

    fun logConfigChange(screen: String, configName: String, oldValue: String, newValue: String) {
        log(screen, "CONFIG_CHANGE", "$configName: '$oldValue' -> '$newValue'")
    }

    fun logProfileChange(oldProfile: String, newProfile: String) {
        log("MainDashboard", "PROFILE_CHANGE", "'$oldProfile' -> '$newProfile'")
    }

    fun logExport(configType: String) {
        log("MainDashboard", "EXPORT", "Exported: $configType")
    }

    fun logFileDetection(filePath: String, exists: Boolean) {
        log("FileDetection", "FILE_CHECK", "$filePath: ${if (exists) "FOUND" else "NOT FOUND"}")
    }

    fun logError(screen: String, error: String) {
        log(screen, "ERROR", error)
    }

    fun getLogs(): List<LogEntry> = logs.toList()

    fun getFormattedLogs(): String {
        val sb = StringBuilder()
        sb.appendLine("=== X695C Vendor Tuner Activity Log ===")
        sb.appendLine("Generated: ${dateFormat.format(Date())}")
        sb.appendLine("Total entries: ${logs.size}")
        sb.appendLine("==========================================")
        sb.appendLine()

        logs.forEach { entry ->
            sb.appendLine("[${dateFormat.format(Date(entry.timestamp))}] [${entry.screen}] ${entry.action}")
            if (entry.details.isNotEmpty()) {
                sb.appendLine("    $entry.details")
            }
        }

        return sb.toString()
    }

    fun clearLogs() {
        logs.clear()
    }

    fun getLogsCount(): Int = logs.size
}
