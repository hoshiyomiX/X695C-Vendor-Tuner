package com.x695c.tuner.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

/**
 * Writes configuration files to the vendor partition using root access.
 * All write operations require root privileges to modify /vendor/etc/ files.
 *
 * IMPORTANT: Without root access, NO configuration changes can be applied!
 */
object ConfigWriter {
    // Only paths verified from the X695C vendor dump are used.

    // Config file paths (obfuscated in logs)
    private const val GAME_CONFIG_PATH = "/vendor/etc/power_app_cfg.xml"
    private const val SCENARIO_CONFIG_PATH = "/vendor/etc/powerscntbl.xml"
    private const val MEMORY_CONFIG_PATH = "/vendor/etc/performance/policy_config_6g_ram.json"
    // Obfuscated names for logging
    private const val GAME_CONFIG_NAME = "game_cfg"
    private const val SCENARIO_CONFIG_NAME = "scenario_cfg"
    private const val MEMORY_CONFIG_NAME = "mem_cfg"
    /**
     * Result of a write operation
     */
    data class WriteResult(
        val success: Boolean,
        val configName: String,
        val errorMessage: String? = null
    )

    /**
     * Check if root is available before attempting any write.
     * Returns true if root is available, false otherwise.
     */
    private fun checkRootOrLog(): Boolean {
        if (!RootChecker.isRootAvailable()) {
            ActivityLogger.logError("ConfigWriter", "Root access required for write operations")
            return false
        }
        return true
    }

    /**
     * Execute a command with root privileges.
     * Returns the exit code and output.
     */
    private fun executeRootCommand(command: String): Pair<Int, String> {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val outputStream = DataOutputStream(process.outputStream)
            val inputStream = BufferedReader(InputStreamReader(process.inputStream))
            val errorStream = BufferedReader(InputStreamReader(process.errorStream))

            outputStream.writeBytes("$command\n")
            outputStream.flush()
            outputStream.writeBytes("exit\n")
            outputStream.flush()

            val output = StringBuilder()
            var line: String?
            while (inputStream.readLine().also { line = it } != null) {
                output.appendLine(line)
            }
            while (errorStream.readLine().also { line = it } != null) {
                output.appendLine("[ERROR] $line")
            }

            val exitCode = process.waitFor()
            exitCode to output.toString()
        } catch (e: Exception) {
            ActivityLogger.logError("ConfigWriter", "Failed to execute root command: ${e.message}")
            -1 to (e.message ?: "Unknown error")
        }
    }

    /**
     * Write content to a file using root.
     * Uses 'cat' with heredoc for reliable writing of multi-line content.
     */
    private fun writeToFileWithRoot(filePath: String, content: String): WriteResult {
        if (!checkRootOrLog()) {
            return WriteResult(
                success = false,
                configName = "unknown",
                errorMessage = "Root access not available"
            )
        }

        // First, backup the original file
        val backupPath = "$filePath.bak"
        executeRootCommand("cp $filePath $backupPath 2>/dev/null || true")

        // Write content using printf for reliability
        // Escape single quotes and special characters
        val escapedContent = content
            .replace("'", "'\\''")
            .replace("`", "\\`")
            .replace("$", "\\$")

        val command = "printf '%s' '$escapedContent' > $filePath"
        val (exitCode, output) = executeRootCommand(command)

        return if (exitCode == 0) {
            WriteResult(success = true, configName = "unknown")
        } else {
            WriteResult(
                success = false,
                configName = "unknown",
                errorMessage = "Write failed (exit $exitCode): $output"
            )
        }
    }

    /**
     * Write game tuning configurations to power_app_cfg.xml
     * Requires ROOT access!
     */
    fun writeGameConfigs(configs: Map<String, GameTuningConfig>): WriteResult {
        if (!checkRootOrLog()) {
            return WriteResult(
                success = false,
                configName = GAME_CONFIG_NAME,
                errorMessage = "Root access required"
            )
        }

        ActivityLogger.log("ConfigWriter", "WRITE_START", "Writing $GAME_CONFIG_NAME with ${configs.size} entries")

        return try {
            val xmlContent = buildGameConfigXml(configs)
            val result = writeToFileWithRoot(GAME_CONFIG_PATH, xmlContent)

            if (result.success) {
                ActivityLogger.log("ConfigWriter", "WRITE_SUCCESS", "$GAME_CONFIG_NAME written successfully")
                WriteResult(success = true, configName = GAME_CONFIG_NAME)
            } else {
                ActivityLogger.logError("ConfigWriter", "Failed to write $GAME_CONFIG_NAME: ${result.errorMessage}")
                result.copy(configName = GAME_CONFIG_NAME)
            }
        } catch (e: Exception) {
            ActivityLogger.logError("ConfigWriter", "Exception writing $GAME_CONFIG_NAME: ${e.message}")
            WriteResult(success = false, configName = GAME_CONFIG_NAME, errorMessage = e.message)
        }
    }

    /**
     * Write performance scenario configurations to powerscntbl.xml
     * Requires ROOT access!
     */
    fun writeScenarioConfigs(configs: Map<String, PerformanceScenarioConfig>): WriteResult {
        if (!checkRootOrLog()) {
            return WriteResult(
                success = false,
                configName = SCENARIO_CONFIG_NAME,
                errorMessage = "Root access required"
            )
        }

        ActivityLogger.log("ConfigWriter", "WRITE_START", "Writing $SCENARIO_CONFIG_NAME with ${configs.size} entries")

        return try {
            val xmlContent = buildScenarioConfigXml(configs)
            val result = writeToFileWithRoot(SCENARIO_CONFIG_PATH, xmlContent)

            if (result.success) {
                ActivityLogger.log("ConfigWriter", "WRITE_SUCCESS", "$SCENARIO_CONFIG_NAME written successfully")
                WriteResult(success = true, configName = SCENARIO_CONFIG_NAME)
            } else {
                ActivityLogger.logError("ConfigWriter", "Failed to write $SCENARIO_CONFIG_NAME: ${result.errorMessage}")
                result.copy(configName = SCENARIO_CONFIG_NAME)
            }
        } catch (e: Exception) {
            ActivityLogger.logError("ConfigWriter", "Exception writing $SCENARIO_CONFIG_NAME: ${e.message}")
            WriteResult(success = false, configName = SCENARIO_CONFIG_NAME, errorMessage = e.message)
        }
    }

    /**
     * Write memory management configuration to policy_config_6g_ram.json
     * Requires ROOT access!
     */
    fun writeMemoryConfig(config: MemoryManagementConfig): WriteResult {
        if (!checkRootOrLog()) {
            return WriteResult(
                success = false,
                configName = MEMORY_CONFIG_NAME,
                errorMessage = "Root access required"
            )
        }

        ActivityLogger.log("ConfigWriter", "WRITE_START", "Writing $MEMORY_CONFIG_NAME")

        return try {
            val jsonContent = buildMemoryConfigJson(config)
            val result = writeToFileWithRoot(MEMORY_CONFIG_PATH, jsonContent)

            if (result.success) {
                ActivityLogger.log("ConfigWriter", "WRITE_SUCCESS", "$MEMORY_CONFIG_NAME written successfully")
                WriteResult(success = true, configName = MEMORY_CONFIG_NAME)
            } else {
                ActivityLogger.logError("ConfigWriter", "Failed to write $MEMORY_CONFIG_NAME: ${result.errorMessage}")
                result.copy(configName = MEMORY_CONFIG_NAME)
            }
        } catch (e: Exception) {
            ActivityLogger.logError("ConfigWriter", "Exception writing $MEMORY_CONFIG_NAME: ${e.message}")
            WriteResult(success = false, configName = MEMORY_CONFIG_NAME, errorMessage = e.message)
        }
    }

    /**
     * Write all configurations at once.
     * Returns a list of results for each config type.
     */
    fun writeAllConfigs(
        gameConfigs: Map<String, GameTuningConfig>,
        scenarioConfigs: Map<String, PerformanceScenarioConfig>,
        memoryConfig: MemoryManagementConfig
    ): List<WriteResult> {
        if (!checkRootOrLog()) {
            ActivityLogger.logError("ConfigWriter", "Cannot write configs: Root access required")
            return listOf(
                WriteResult(false, GAME_CONFIG_NAME, "Root access required"),
                WriteResult(false, SCENARIO_CONFIG_NAME, "Root access required"),
                WriteResult(false, MEMORY_CONFIG_NAME, "Root access required")
            )
        }

        ActivityLogger.log("ConfigWriter", "WRITE_ALL_START", "Starting full configuration write")

        val results = mutableListOf<WriteResult>()
        results.add(writeGameConfigs(gameConfigs))
        results.add(writeScenarioConfigs(scenarioConfigs))
        results.add(writeMemoryConfig(memoryConfig))

        val successCount = results.count { it.success }
        ActivityLogger.log("ConfigWriter", "WRITE_ALL_COMPLETE", "$successCount/${results.size} configs written successfully")

        return results
    }

    // ==================== XML/JSON BUILDERS ====================

    private fun buildGameConfigXml(configs: Map<String, GameTuningConfig>): String {
        val sb = StringBuilder()
        sb.appendLine("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
        sb.appendLine("<WHITELIST>")

        configs.forEach { (packageName, config) ->
            sb.appendLine("  <Package name=\"$packageName\">")
            sb.appendLine("    <Activity name=\"Common\">")

            // Thermal policy
            sb.appendLine("      <data cmd=\"PERF_RES_THERMAL_POLICY\" param1=\"${config.thermalPolicy.value}\"/>")

            // GPU margin mode
            sb.appendLine("      <data cmd=\"PERF_RES_GPU_GED_MARGIN_MODE\" param1=\"${config.gpuMarginMode.value}\"/>")

            // GPU timer DVFS margin
            sb.appendLine("      <data cmd=\"PERF_RES_GPU_GED_TIMER_BASE_DVFS_MARGIN\" param1=\"${config.gpuTimerDvfsMargin}\"/>")

            // Uclamp min
            sb.appendLine("      <data cmd=\"PERF_RES_SCHED_UCLAMP_MIN_TA\" param1=\"${config.uclampMin.value}\"/>")

            // Sched boost
            sb.appendLine("      <data cmd=\"PERF_RES_SCHED_BOOST\" param1=\"${config.schedBoost.value}\"/>")

            // FPS margin mode
            if (config.fpsMarginMode != FpsMarginMode.DISABLED) {
                sb.appendLine("      <data cmd=\"PERF_RES_FPS_FPSGO_MARGIN_MODE\" param1=\"${config.fpsMarginMode.value}\"/>")
            }

            // FPS adjust loading
            if (config.fpsAdjustLoading) {
                sb.appendLine("      <data cmd=\"PERF_RES_FPS_FPSGO_ADJ_LOADING\" param1=\"1\"/>")
            }

            // FPS loading threshold
            sb.appendLine("      <data cmd=\"PERF_RES_FPS_FPSGO_LLF_TH\" param1=\"${config.fpsLoadingThreshold.value}\"/>")

            // GPU block boost
            if (config.gpuBlockBoost != GpuBlockBoost.DISABLED) {
                sb.appendLine("      <data cmd=\"PERF_RES_FPS_FPSGO_GPU_BLOCK_BOOST\" param1=\"${config.gpuBlockBoost.value}\"/>")
            }

            // Frame rescue
            if (config.frameRescuePercent != FrameRescuePercent.NONE) {
                sb.appendLine("      <data cmd=\"PERF_RES_FPS_FPSGO_FRAME_RESCUE_F\" param1=\"${config.frameRescueF}\"/>")
                sb.appendLine("      <data cmd=\"PERF_RES_FPS_FPSGO_FRAME_RESCUE_PERCENT\" param1=\"${config.frameRescuePercent.value}\"/>")
            }

            // Network boost
            if (config.networkBoost != NetworkBoost.DISABLED) {
                sb.appendLine("      <data cmd=\"PERF_RES_NET_NETD_BOOST_UID\" param1=\"${config.networkBoost.value}\"/>")
            }

            // WiFi low latency
            if (config.wifiLowLatency == WifiLowLatency.ENABLED) {
                sb.appendLine("      <data cmd=\"PERF_RES_NET_WIFI_LOW_LATENCY\" param1=\"1\"/>")
            }

            // Weak signal optimization
            if (config.weakSignalOpt == WeakSignalOpt.ENABLED) {
                sb.appendLine("      <data cmd=\"PERF_RES_NET_MD_WEAK_SIG_OPT\" param1=\"1\"/>")
            }

            sb.appendLine("    </Activity>")
            sb.appendLine("  </Package>")
        }

        sb.appendLine("</WHITELIST>")
        return sb.toString()
    }

    private fun buildScenarioConfigXml(configs: Map<String, PerformanceScenarioConfig>): String {
        val sb = StringBuilder()
        sb.appendLine("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
        sb.appendLine("<SCNTABLE>")

        configs.forEach { (_, config) ->
            sb.appendLine("  <scenario powerhint=\"${config.scenarioName}\">")

            // CPU frequency min cluster 0
            if (config.cpuFreqMinCluster0 > 0) {
                sb.appendLine("    <data cmd=\"PERF_RES_CPUFREQ_MIN_CLUSTER_0\" param1=\"${config.cpuFreqMinCluster0}\"></data>")
            }

            // CPU frequency min cluster 1
            if (config.cpuFreqMinCluster1 > 0) {
                sb.appendLine("    <data cmd=\"PERF_RES_CPUFREQ_MIN_CLUSTER_1\" param1=\"${config.cpuFreqMinCluster1}\"></data>")
            }

            // DRAM OPP
            sb.appendLine("    <data cmd=\"PERF_RES_DRAM_OPP_MIN\" param1=\"${config.dramOpp.value}\"></data>")

            // Uclamp min
            if (config.uclampMin != UclampMin.NONE) {
                sb.appendLine("    <data cmd=\"PERF_RES_SCHED_UCLAMP_MIN_TA\" param1=\"${config.uclampMin.value}\"></data>")
            }

            // Sched boost
            if (config.schedBoost != SchedBoost.DISABLED) {
                sb.appendLine("    <data cmd=\"PERF_RES_SCHED_BOOST\" param1=\"${config.schedBoost.value}\"></data>")
            }

            // BHR OPP
            if (config.bhrOpp > 0) {
                sb.appendLine("    <data cmd=\"PERF_RES_FPS_FBT_BHR_OPP\" param1=\"${config.bhrOpp}\"></data>")
            }

            // Hold time
            if (config.holdTime > 0) {
                sb.appendLine("    <data cmd=\"PERF_RES_POWER_HINT_HOLD_TIME\" param1=\"${config.holdTime}\"></data>")
            }

            // Ext hint
            if (config.extHint > 0) {
                sb.appendLine("    <data cmd=\"PERF_RES_POWER_HINT_EXT_HINT\" param1=\"${config.extHint}\"></data>")
            }

            // Ext hint hold time
            if (config.extHintHoldTime > 0) {
                sb.appendLine("    <data cmd=\"PERF_RES_POWER_HINT_EXT_HINT_HOLD_TIME\" param1=\"${config.extHintHoldTime}\"></data>")
            }

            sb.appendLine("  </scenario>")
        }

        sb.appendLine("</SCNTABLE>")
        return sb.toString()
    }

    private fun buildMemoryConfigJson(config: MemoryManagementConfig): String {
        val json = JSONObject()

        // Thresholds (vendor key: "total_mem")
        val thresholds = JSONObject().apply {
            put("adj_native", config.thresholds.adjNative)
            put("adj_system", config.thresholds.adjSystem)
            put("adj_persist", config.thresholds.adjPersist)
            put("adj_foreground", config.thresholds.adjForeground)
            put("adj_visible", config.thresholds.adjVisible)
            put("adj_perceptible", config.thresholds.adjPerceptible)
            put("adj_backup", config.thresholds.adjBackup)
            put("adj_heavyweight", config.thresholds.adjHeavyweight)
            put("adj_service", config.thresholds.adjService)
            put("adj_home", config.thresholds.adjHome)
            put("adj_previous", config.thresholds.adjPrevious)
            put("adj_service_b", config.thresholds.adjServiceB)
            put("adj_cached", config.thresholds.adjCached)
            put("swapfree_min_percent", config.thresholds.swapfreeMinPercent)
            put("swapfree_max_percent", config.thresholds.swapfreeMaxPercent)
            put("free_cached", config.thresholds.freeCached)
        }
        json.put("total_mem", thresholds)

        // Process limits (vendor key: "proc_mem")
        val processLimits = JSONObject().apply {
            put("3rd", config.processLimits.thirdParty)
            put("gms", config.processLimits.gms)
            put("sys", config.processLimits.system)
            put("sys_bg", config.processLimits.systemBg)
            put("game", config.processLimits.game)
        }
        json.put("proc_mem", processLimits)

        // Features (vendor key: "feature")
        val features = JSONObject().apply {
            put("app_start_limit", config.features.appStartLimit)
            put("oom_adj_clean", config.features.oomAdjClean)
            put("low_ram_clean", config.features.lowRamClean)
            put("low_swap_clean", config.features.lowSwapClean)
            put("one_key_clean", config.features.oneKeyClean)
            put("heavy_cpu_clean", config.features.heavyCpuClean)
            put("heavy_iow_clean", config.features.heavyIowClean)
            put("sleep_clean", config.features.sleepClean)
            put("fix_adj", config.features.fixAdj)
            put("limit_sys_start", config.features.limitSysStart)
            put("limit_gms_start", config.features.limitGmsStart)
            put("limit_3rd_start", config.features.limit3rdStart)
            put("allow_clean_sys", config.features.allowCleanSys)
            put("allow_clean_gms", config.features.allowCleanGms)
            put("allow_clean_3rd", config.features.allowClean3rd)
        }
        json.put("feature", features)

        // Counts (vendor nests them under "number")
        val number = JSONObject().apply {
            put("recent_task", config.recentTaskCount)
            put("notification", config.notificationCount)
            put("cached_proc", config.cachedProcCount)
        }
        json.put("number", number)

        return json.toString(2)
    }

    /**
     * Restore a config file from backup.
     */
    fun restoreFromBackup(configName: String): WriteResult {
        if (!checkRootOrLog()) {
            return WriteResult(success = false, configName = configName, errorMessage = "Root access required")
        }

        val (path, name) = when (configName) {
            GAME_CONFIG_NAME -> GAME_CONFIG_PATH to GAME_CONFIG_NAME
            SCENARIO_CONFIG_NAME -> SCENARIO_CONFIG_PATH to SCENARIO_CONFIG_NAME
            MEMORY_CONFIG_NAME -> MEMORY_CONFIG_PATH to MEMORY_CONFIG_NAME
            else -> return WriteResult(success = false, configName = configName, errorMessage = "Unknown config")
        }

        val backupPath = "$path.bak"
        val (exitCode, output) = executeRootCommand("cp $backupPath $path")

        return if (exitCode == 0) {
            ActivityLogger.log("ConfigWriter", "RESTORE_SUCCESS", "$name restored from backup")
            WriteResult(success = true, configName = name)
        } else {
            ActivityLogger.logError("ConfigWriter", "Failed to restore $name: $output")
            WriteResult(success = false, configName = name, errorMessage = output)
        }
    }
}
