package com.x695c.tuner.data

import java.io.File

/**
 * Detects and reads vendor configuration files from the device.
 * Requires root access for actual file reading on production devices.
 * All paths are obfuscated in logs for security.
 */
object ConfigFileDetector {

    // Config file paths (private - never exposed in logs)
    private val gameConfigPaths = listOf("/vendor/etc/power_app_cfg.xml")
    
    private val scenarioConfigPaths = listOf("/vendor/etc/powerscntbl.xml")
    
    private val memoryConfigPaths = listOf("/vendor/etc/performance/policy_config_6g_ram.json")
    
    enum class ConfigType {
        GAME_WHITELIST,
        PERFORMANCE_SCENARIOS,
        MEMORY_MANAGEMENT
    }

    data class ConfigStatus(
        val type: ConfigType,
        val available: Boolean,
        val readable: Boolean
    ) {
        // Never expose the actual path in toString or any public output
        override fun toString(): String {
            return "ConfigStatus(type=$type, available=$available, readable=$readable)"
        }
    }

    private val detectedConfigs = mutableMapOf<ConfigType, ConfigStatus>()

    /**
     * Check if configuration files exist on the device.
     * Should be called during app initialization.
     */
    fun detectConfigs(): Map<ConfigType, ConfigStatus> {
        detectConfig(ConfigType.GAME_WHITELIST, gameConfigPaths)
        detectConfig(ConfigType.PERFORMANCE_SCENARIOS, scenarioConfigPaths)
        detectConfig(ConfigType.MEMORY_MANAGEMENT, memoryConfigPaths)
        
        return detectedConfigs.toMap()
    }

    private fun detectConfig(type: ConfigType, paths: List<String>) {
        var detected = false
        var readable = false

        for (path in paths) {
            val file = File(path)
            if (file.exists()) {
                detected = true
                readable = file.canRead()
                
                // Use obfuscated logging - never expose actual path
                ActivityLogger.logFileDetection(path, true)

                if (readable) {
                    break
                }
            } else {
                ActivityLogger.logFileDetection(path, false)
            }
        }

        val status = ConfigStatus(type, detected, readable)
        detectedConfigs[type] = status
    }

    /**
     * Get the status of a specific config type.
     */
    fun getConfigStatus(type: ConfigType): ConfigStatus {
        return detectedConfigs[type] ?: ConfigStatus(type, false, false)
    }

    /**
     * Check if a config type is available and readable.
     */
    fun isConfigAvailable(type: ConfigType): Boolean {
        return detectedConfigs[type]?.available ?: false
    }

    /**
     * Check if a config type is readable (file exists and can be read).
     */
    fun isConfigReadable(type: ConfigType): Boolean {
        val status = detectedConfigs[type]
        return status?.available == true && status.readable
    }

    /**
     * Read the content of a config file if available and readable.
     * Returns null if file is not available or not readable.
     */
    fun readConfigFile(type: ConfigType): String? {
        val paths = when (type) {
            ConfigType.GAME_WHITELIST -> gameConfigPaths
            ConfigType.PERFORMANCE_SCENARIOS -> scenarioConfigPaths
            ConfigType.MEMORY_MANAGEMENT -> memoryConfigPaths
        }

        for (path in paths) {
            val file = File(path)
            if (file.exists() && file.canRead()) {
                return try {
                    file.readText()
                } catch (e: Exception) {
                    ActivityLogger.logError("ConfigFileDetector", "Failed to read config: ${e.message}")
                    null
                }
            }
        }
        return null
    }

    /**
     * Get a summary of all config file statuses.
     * Does NOT expose actual file paths.
     */
    fun getStatusSummary(): String {
        val sb = StringBuilder()
        sb.appendLine("=== Config File Detection Status ===")

        detectedConfigs.forEach { (type, status) ->
            val statusText = when {
                !status.available -> "NOT FOUND"
                !status.readable -> "FOUND (No Read Permission)"
                else -> "AVAILABLE"
            }
            sb.appendLine("${type.name}: $statusText")
        }

        return sb.toString()
    }
}
