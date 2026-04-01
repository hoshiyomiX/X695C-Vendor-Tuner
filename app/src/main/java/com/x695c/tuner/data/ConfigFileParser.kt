package com.x695c.tuner.data

import org.json.JSONObject
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Parses XML and JSON config files from the vendor partition.
 * Supports parsing of game tuning configs, performance scenarios,
 * memory management configs, and GPU DVFS settings.
 */
object ConfigFileParser {

    // Config file paths (private - never exposed in logs for security)
    private val gameConfigPaths = listOf(
        "/vendor/etc/power_app_cfg.xml",
        "/vendor/etc/powerhint.xml",
        "/data/vendor/power/power_app_cfg.xml"
    )
    private val scenarioConfigPaths = listOf(
        "/vendor/etc/powerscntbl.xml",
        "/vendor/etc/powerhint_scene.xml"
    )
    private val memoryConfigPaths = listOf(
        "/vendor/etc/policy_config_6g_ram.json",
        "/vendor/etc/policy_config.json",
        "/data/vendor/lmkd/policy_config.json"
    )
    private val gpuConfigPaths = listOf(
        "/vendor/etc/gpu_dvfs_setting.xml",
        "/vendor/etc/hwservicectrl.json"
    )

    /**
     * Parse game tuning configurations from config files.
     * Returns a map of package names to their tuning configs.
     */
    fun parseGameConfigs(): Map<String, GameTuningConfig> {
        val content = findReadableFile(gameConfigPaths)
        
        if (content == null) {
            ActivityLogger.log("ConfigParser", "GAME_CONFIG", "No readable game config file found")
            return emptyMap()
        }

        return try {
            val configs = mutableMapOf<String, GameTuningConfig>()
            val doc = parseXmlDocument(content)
            
            val packages = doc.getElementsByTagName("Package")
            for (i in 0 until packages.length) {
                val packageElement = packages.item(i) as Element
                val packageName = packageElement.getAttribute("name")
                
                if (packageName.isNotEmpty()) {
                    val config = parseGamePackageConfig(packageName, packageElement)
                    configs[packageName] = config
                }
            }
            
            ActivityLogger.log("ConfigParser", "GAME_CONFIG", "Parsed ${configs.size} game configurations")
            configs
        } catch (e: Exception) {
            ActivityLogger.logError("ConfigParser", "Failed to parse game config: ${e.message}")
            emptyMap()
        }
    }

    /**
     * Parse performance scenario configurations from config files.
     * Returns a map of scenario names to their configs.
     */
    fun parseScenarioConfigs(): Map<String, PerformanceScenarioConfig> {
        val content = findReadableFile(scenarioConfigPaths)
        
        if (content == null) {
            ActivityLogger.log("ConfigParser", "SCENARIO_CONFIG", "No readable scenario config file found")
            return emptyMap()
        }

        return try {
            val configs = mutableMapOf<String, PerformanceScenarioConfig>()
            val doc = parseXmlDocument(content)
            
            val scenarios = doc.getElementsByTagName("Scenario")
            for (i in 0 until scenarios.length) {
                val scenarioElement = scenarios.item(i) as Element
                val scenarioName = scenarioElement.getAttribute("name")
                
                if (scenarioName.isNotEmpty()) {
                    val config = parseScenarioConfig(scenarioName, scenarioElement)
                    configs[scenarioName] = config
                }
            }
            
            ActivityLogger.log("ConfigParser", "SCENARIO_CONFIG", "Parsed ${configs.size} scenario configurations")
            configs
        } catch (e: Exception) {
            ActivityLogger.logError("ConfigParser", "Failed to parse scenario config: ${e.message}")
            emptyMap()
        }
    }

    /**
     * Parse memory management configuration from config files.
     * Returns the memory management config.
     */
    fun parseMemoryConfig(): MemoryManagementConfig? {
        val content = findReadableFile(memoryConfigPaths)
        
        if (content == null) {
            ActivityLogger.log("ConfigParser", "MEMORY_CONFIG", "No readable memory config file found")
            return null
        }

        return try {
            val json = JSONObject(content)
            val config = parseMemoryJsonConfig(json)
            ActivityLogger.log("ConfigParser", "MEMORY_CONFIG", "Successfully parsed memory configuration")
            config
        } catch (e: Exception) {
            ActivityLogger.logError("ConfigParser", "Failed to parse memory config: ${e.message}")
            null
        }
    }

    /**
     * Parse GPU DVFS configuration from config files.
     * Returns the GPU DVFS config.
     */
    fun parseGpuConfig(): GpuDvfsConfig? {
        val content = findReadableFile(gpuConfigPaths)
        
        if (content == null) {
            ActivityLogger.log("ConfigParser", "GPU_CONFIG", "No readable GPU config file found")
            return null
        }

        return try {
            val doc = parseXmlDocument(content)
            val config = parseGpuXmlConfig(doc)
            ActivityLogger.log("ConfigParser", "GPU_CONFIG", "Successfully parsed GPU configuration")
            config
        } catch (e: Exception) {
            ActivityLogger.logError("ConfigParser", "Failed to parse GPU config: ${e.message}")
            null
        }
    }

    // ==================== PRIVATE HELPER METHODS ====================

    private fun findReadableFile(paths: List<String>): String? {
        for (path in paths) {
            val file = File(path)
            if (file.exists() && file.canRead()) {
                return file.readText()
            }
        }
        return null
    }

    private fun parseXmlDocument(content: String): Document {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        return builder.parse(content.byteInputStream())
    }

    private fun parseGamePackageConfig(packageName: String, element: Element): GameTuningConfig {
        var config = GameTuningConfig(packageName = packageName)
        
        val activities = element.getElementsByTagName("Activity")
        for (i in 0 until activities.length) {
            val activityElement = activities.item(i) as Element
            config = parseActivityDataElements(activityElement, config)
        }
        
        return config
    }

    private fun parseActivityDataElements(activityElement: Element, config: GameTuningConfig): GameTuningConfig {
        var currentConfig = config
        val dataElements = activityElement.getElementsByTagName("data")
        
        for (i in 0 until dataElements.length) {
            val dataElement = dataElements.item(i) as Element
            val cmd = dataElement.getAttribute("cmd")
            val param1 = dataElement.getAttribute("param1").toIntOrNull() ?: 0
            
            currentConfig = when (cmd) {
                "PERF_RES_THERMAL_POLICY" -> currentConfig.copy(
                    thermalPolicy = ThermalPolicy.fromValue(param1)
                )
                "PERF_RES_GPU_GED_MARGIN_MODE" -> currentConfig.copy(
                    gpuMarginMode = GpuMarginMode.fromValue(param1)
                )
                "PERF_RES_SCHED_UCLAMP_MIN_TA" -> currentConfig.copy(
                    uclampMin = UclampMin.fromValue(param1)
                )
                "PERF_RES_SCHED_BOOST" -> currentConfig.copy(
                    schedBoost = SchedBoost.fromValue(param1)
                )
                "PERF_RES_FPS_FPSGO_MARGIN_MODE" -> currentConfig.copy(
                    fpsMarginMode = FpsMarginMode.fromValue(param1)
                )
                "PERF_RES_FPS_FPSGO_ADJ_LOADING" -> currentConfig.copy(
                    fpsAdjustLoading = param1 == 1
                )
                "PERF_RES_FPS_FPSGO_LLF_TH" -> currentConfig.copy(
                    fpsLoadingThreshold = FpsLoadingThreshold.fromValue(param1)
                )
                "PERF_RES_FPS_FPSGO_GPU_BLOCK_BOOST" -> currentConfig.copy(
                    gpuBlockBoost = GpuBlockBoost.fromValue(param1)
                )
                "PERF_RES_NET_NETD_BOOST_UID" -> currentConfig.copy(
                    networkBoost = NetworkBoost.fromValue(param1)
                )
                "PERF_RES_NET_WIFI_LOW_LATENCY" -> currentConfig.copy(
                    wifiLowLatency = if (param1 == 1) WifiLowLatency.ENABLED else WifiLowLatency.DISABLED
                )
                "PERF_RES_NET_MD_WEAK_SIG_OPT" -> currentConfig.copy(
                    weakSignalOpt = if (param1 == 1) WeakSignalOpt.ENABLED else WeakSignalOpt.DISABLED
                )
                else -> currentConfig
            }
        }
        
        return currentConfig
    }

    private fun parseScenarioConfig(scenarioName: String, element: Element): PerformanceScenarioConfig {
        var config = PerformanceScenarioConfig(scenarioName = scenarioName)
        
        val dataElements = element.getElementsByTagName("data")
        for (i in 0 until dataElements.length) {
            val dataElement = dataElements.item(i) as Element
            val cmd = dataElement.getAttribute("cmd")
            val param1 = dataElement.getAttribute("param1").toLongOrNull() ?: 0L
            val param1Int = param1.toInt()
            
            config = when (cmd) {
                "CPU_FREQ_MIN_CLUSTER0" -> config.copy(cpuFreqMinCluster0 = param1)
                "CPU_FREQ_MIN_CLUSTER1" -> config.copy(cpuFreqMinCluster1 = param1)
                "DRAM_OPP" -> config.copy(dramOpp = DramOpp.fromValue(param1Int))
                "SCHED_UCLAMP_MIN" -> config.copy(uclampMin = UclampMin.fromValue(param1Int))
                "SCHED_BOOST" -> config.copy(schedBoost = SchedBoost.fromValue(param1Int))
                "TOUCH_BOOST_OPP" -> config.copy(touchBoostOpp = TouchBoostOpp.fromValue(param1Int))
                "TOUCH_BOOST_DURATION" -> config.copy(touchBoostDuration = param1)
                "BHR_OPP" -> config.copy(bhrOpp = param1Int)
                "HOLD_TIME" -> config.copy(holdTime = param1)
                "EXT_HINT" -> config.copy(extHint = param1Int)
                "EXT_HINT_HOLD_TIME" -> config.copy(extHintHoldTime = param1)
                else -> config
            }
        }
        
        return config
    }

    private fun parseMemoryJsonConfig(json: JSONObject): MemoryManagementConfig {
        var thresholds = MemoryThresholdConfig()
        var processLimits = ProcessMemoryConfig()
        var features = MemoryFeatureConfig()
        var recentTaskCount = 6
        var notificationCount = 4
        var cachedProcCount = 16

        // Parse thresholds
        if (json.has("thresholds")) {
            val thresholdsJson = json.getJSONObject("thresholds")
            thresholds = MemoryThresholdConfig(
                adjNative = thresholdsJson.optInt("adj_native", 1024),
                adjSystem = thresholdsJson.optInt("adj_system", 1024),
                adjPersist = thresholdsJson.optInt("adj_persist", 1024),
                adjForeground = thresholdsJson.optInt("adj_foreground", 200),
                adjVisible = thresholdsJson.optInt("adj_visible", 400),
                adjPerceptible = thresholdsJson.optInt("adj_perceptible", 300),
                adjBackup = thresholdsJson.optInt("adj_backup", 300),
                adjHeavyweight = thresholdsJson.optInt("adj_heavyweight", 150),
                adjService = thresholdsJson.optInt("adj_service", 200),
                adjHome = thresholdsJson.optInt("adj_home", 150),
                adjPrevious = thresholdsJson.optInt("adj_previous", 200),
                adjServiceB = thresholdsJson.optInt("adj_service_b", 200),
                adjCached = thresholdsJson.optInt("adj_cached", 700),
                swapfreeMinPercent = thresholdsJson.optInt("swapfree_min_percent", 5),
                swapfreeMaxPercent = thresholdsJson.optInt("swapfree_max_percent", 10),
                freeCached = thresholdsJson.optInt("free_cached", 700)
            )
        }

        // Parse process limits
        if (json.has("process_limits")) {
            val limitsJson = json.getJSONObject("process_limits")
            processLimits = ProcessMemoryConfig(
                thirdParty = limitsJson.optInt("third_party", 100),
                gms = limitsJson.optInt("gms", 100),
                system = limitsJson.optInt("system", 100),
                systemBg = limitsJson.optInt("system_bg", 100),
                game = limitsJson.optInt("game", 300)
            )
        }

        // Parse features
        if (json.has("features")) {
            val featuresJson = json.getJSONObject("features")
            features = MemoryFeatureConfig(
                appStartLimit = featuresJson.optBoolean("app_start_limit", true),
                oomAdjClean = featuresJson.optBoolean("oom_adj_clean", true),
                lowRamClean = featuresJson.optBoolean("low_ram_clean", true),
                lowSwapClean = featuresJson.optBoolean("low_swap_clean", true),
                oneKeyClean = featuresJson.optBoolean("one_key_clean", true),
                heavyCpuClean = featuresJson.optBoolean("heavy_cpu_clean", false),
                heavyIowClean = featuresJson.optBoolean("heavy_iow_clean", false),
                sleepClean = featuresJson.optBoolean("sleep_clean", true),
                fixAdj = featuresJson.optBoolean("fix_adj", true),
                limitSysStart = featuresJson.optBoolean("limit_sys_start", false),
                limitGmsStart = featuresJson.optBoolean("limit_gms_start", false),
                limit3rdStart = featuresJson.optBoolean("limit_3rd_start", true),
                allowCleanSys = featuresJson.optBoolean("allow_clean_sys", false),
                allowCleanGms = featuresJson.optBoolean("allow_clean_gms", false),
                allowClean3rd = featuresJson.optBoolean("allow_clean_3rd", true)
            )
        }

        // Parse counts
        recentTaskCount = json.optInt("recent_task_count", 6)
        notificationCount = json.optInt("notification_count", 4)
        cachedProcCount = json.optInt("cached_proc_count", 16)

        return MemoryManagementConfig(
            thresholds = thresholds,
            processLimits = processLimits,
            features = features,
            recentTaskCount = recentTaskCount,
            notificationCount = notificationCount,
            cachedProcCount = cachedProcCount
        )
    }

    private fun parseGpuXmlConfig(doc: Document): GpuDvfsConfig {
        var marginMode = GpuMarginMode.BALANCED
        var timerBaseDvfsMargin = 10
        var loadingBaseDvfsStep = 4
        var cwaitg = 0

        // Try to find GPU settings in various element structures
        val dvfsSettings = doc.getElementsByTagName("DvfsSetting")
        if (dvfsSettings.length > 0) {
            val settingElement = dvfsSettings.item(0) as Element
            
            marginMode = GpuMarginMode.fromValue(
                getAttributeValue(settingElement, "margin_mode", 50)
            )
            timerBaseDvfsMargin = getAttributeValue(settingElement, "timer_base_dvfs_margin", 10)
            loadingBaseDvfsStep = getAttributeValue(settingElement, "loading_base_dvfs_step", 4)
            cwaitg = getAttributeValue(settingElement, "cwaitg", 0)
        }

        // Alternative: look for individual elements
        val gpuMargin = doc.getElementsByTagName("gpu_margin_mode")
        if (gpuMargin.length > 0) {
            val textContent = (gpuMargin.item(0) as Element).textContent.trim()
            textContent.toIntOrNull()?.let { marginMode = GpuMarginMode.fromValue(it) }
        }

        val timerMargin = doc.getElementsByTagName("timer_base_dvfs_margin")
        if (timerMargin.length > 0) {
            val textContent = (timerMargin.item(0) as Element).textContent.trim()
            textContent.toIntOrNull()?.let { timerBaseDvfsMargin = it }
        }

        val loadingStep = doc.getElementsByTagName("loading_base_dvfs_step")
        if (loadingStep.length > 0) {
            val textContent = (loadingStep.item(0) as Element).textContent.trim()
            textContent.toIntOrNull()?.let { loadingBaseDvfsStep = it }
        }

        return GpuDvfsConfig(
            marginMode = marginMode,
            timerBaseDvfsMargin = timerBaseDvfsMargin,
            loadingBaseDvfsStep = loadingBaseDvfsStep,
            cwaitg = cwaitg
        )
    }

    private fun getAttributeValue(element: Element, name: String, default: Int): Int {
        return element.getAttribute(name).toIntOrNull() ?: default
    }
}
