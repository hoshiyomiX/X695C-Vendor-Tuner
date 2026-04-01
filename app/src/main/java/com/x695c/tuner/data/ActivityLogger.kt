package com.x695c.tuner.data

import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Activity Logger for recording all user actions for debugging and development.
 * Thread-safe singleton implementation.
 * All file paths are obfuscated for security.
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

    /**
     * Obfuscate file path for security - never expose real paths in logs
     */
    private fun obfuscatePath(path: String): String {
        // Return only the config type, not the actual path
        return when {
            path.contains("power_app_cfg") -> "[GAME_CONFIG]"
            path.contains("powerhint") -> "[POWER_HINT]"
            path.contains("powerscntbl") -> "[SCENARIO_TABLE]"
            path.contains("policy_config") -> "[MEMORY_CONFIG]"
            path.contains("gpu_dvfs") -> "[GPU_CONFIG]"
            path.contains("hwservicectrl") -> "[HW_SERVICE]"
            path.contains("lmkd") -> "[LMKD]"
            path.contains("mali") -> "[GPU_DRIVER]"
            path.contains("/vendor/") -> "[VENDOR_FILE]"
            path.contains("/data/vendor/") -> "[DATA_VENDOR]"
            path.contains("/sys/") -> "[SYSFS]"
            else -> "[CONFIG_FILE]"
        }
    }

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

    fun logFileDetection(filePath: String, exists: Boolean) {
        // Obfuscate the file path before logging
        val obfuscatedPath = obfuscatePath(filePath)
        log("FileDetection", "FILE_CHECK", "$obfuscatedPath: ${if (exists) "FOUND" else "NOT FOUND"}")
    }

    fun logError(screen: String, error: String) {
        // Remove any potential file paths from error messages
        val sanitizedError = error
            .replace(Regex("/vendor/\\S+"), "[VENDOR_FILE]")
            .replace(Regex("/data/\\S+"), "[DATA_FILE]")
            .replace(Regex("/sys/\\S+"), "[SYSFS]")
        log(screen, "ERROR", sanitizedError)
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
                sb.appendLine("    ${entry.details}")
            }
        }

        return sb.toString()
    }

    fun clearLogs() {
        logs.clear()
    }

    fun getLogsCount(): Int = logs.size
}
