package com.x695c.tuner.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TunerViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(TunerUiState())
    val uiState: StateFlow<TunerUiState> = _uiState.asStateFlow()

    private val _selectedProfile = MutableStateFlow(TuningProfile.DEFAULT)
    val selectedProfile: StateFlow<TuningProfile> = _selectedProfile.asStateFlow()

    private val _gameConfigs = MutableStateFlow(getDefaultGameConfigs())
    val gameConfigs: StateFlow<Map<String, GameTuningConfig>> = _gameConfigs.asStateFlow()

    private val _scenarioConfigs = MutableStateFlow(getDefaultScenarioConfigs())
    val scenarioConfigs: StateFlow<Map<String, PerformanceScenarioConfig>> = _scenarioConfigs.asStateFlow()

    private val _memoryConfig = MutableStateFlow(MemoryManagementConfig())
    val memoryConfig: StateFlow<MemoryManagementConfig> = _memoryConfig.asStateFlow()

    private val _gpuConfig = MutableStateFlow(GpuDvfsConfig())
    val gpuConfig: StateFlow<GpuDvfsConfig> = _gpuConfig.asStateFlow()

    // Config file availability status
    private val _configAvailability = MutableStateFlow<Map<ConfigFileDetector.ConfigType, ConfigFileDetector.ConfigStatus>>(emptyMap())
    val configAvailability: StateFlow<Map<ConfigFileDetector.ConfigType, ConfigFileDetector.ConfigStatus>> = _configAvailability.asStateFlow()

    init {
        // Log app start
        ActivityLogger.log("App", "INIT", "X695C Vendor Tuner started")

        // Detect and parse config files
        detectAndLoadConfigs()
    }

    private fun detectAndLoadConfigs() {
        viewModelScope.launch {
            ActivityLogger.log("FileDetection", "START", "Scanning for config files...")
            
            // Detect config files
            val configs = ConfigFileDetector.detectConfigs()
            _configAvailability.value = configs

            // Log summary
            ActivityLogger.log("FileDetection", "COMPLETE", ConfigFileDetector.getStatusSummary())

            // Parse configs from device files
            loadConfigsFromDevice()
        }
    }

    private fun loadConfigsFromDevice() {
        viewModelScope.launch {
            // Try to parse game configs
            val parsedGameConfigs = ConfigFileParser.parseGameConfigs()
            if (parsedGameConfigs.isNotEmpty()) {
                // Merge with defaults (parsed configs take precedence)
                val mergedConfigs = getDefaultGameConfigs().toMutableMap()
                mergedConfigs.putAll(parsedGameConfigs)
                _gameConfigs.value = mergedConfigs
                ActivityLogger.log("ConfigParser", "GAME_CONFIGS_LOADED", "Loaded ${parsedGameConfigs.size} game configs from device")
            }

            // Try to parse scenario configs
            val parsedScenarioConfigs = ConfigFileParser.parseScenarioConfigs()
            if (parsedScenarioConfigs.isNotEmpty()) {
                val mergedConfigs = getDefaultScenarioConfigs().toMutableMap()
                mergedConfigs.putAll(parsedScenarioConfigs)
                _scenarioConfigs.value = mergedConfigs
                ActivityLogger.log("ConfigParser", "SCENARIO_CONFIGS_LOADED", "Loaded ${parsedScenarioConfigs.size} scenario configs from device")
            }

            // Try to parse memory config
            val parsedMemoryConfig = ConfigFileParser.parseMemoryConfig()
            if (parsedMemoryConfig != null) {
                _memoryConfig.value = parsedMemoryConfig
                ActivityLogger.log("ConfigParser", "MEMORY_CONFIG_LOADED", "Loaded memory config from device")
            }

            // Try to parse GPU config
            val parsedGpuConfig = ConfigFileParser.parseGpuConfig()
            if (parsedGpuConfig != null) {
                _gpuConfig.value = parsedGpuConfig
                ActivityLogger.log("ConfigParser", "GPU_CONFIG_LOADED", "Loaded GPU config from device")
            }
        }
    }

    fun isConfigAvailable(type: ConfigFileDetector.ConfigType): Boolean {
        return _configAvailability.value[type]?.available ?: false
    }

    fun isConfigReadable(type: ConfigFileDetector.ConfigType): Boolean {
        val status = _configAvailability.value[type]
        return status?.available == true && status.readable
    }

    fun setProfile(profile: TuningProfile) {
        val oldProfile = _selectedProfile.value
        _selectedProfile.value = profile
        ActivityLogger.logProfileChange(oldProfile.name, profile.name)

        when (profile) {
            TuningProfile.DEFAULT -> loadDefaultProfile()
            TuningProfile.POWER_SAVING -> loadPowerSavingProfile()
            TuningProfile.BALANCED -> loadBalancedProfile()
            TuningProfile.PERFORMANCE -> loadPerformanceProfile()
            TuningProfile.GAMING -> loadGamingProfile()
            TuningProfile.CUSTOM -> { /* Keep current settings */ }
        }
    }

    private fun loadDefaultProfile() {
        ActivityLogger.log("Profile", "LOAD_DEFAULT", "Loading default profile settings")
        _gameConfigs.value = getDefaultGameConfigs()
        _scenarioConfigs.value = getDefaultScenarioConfigs()
        _memoryConfig.value = MemoryManagementConfig()
        _gpuConfig.value = GpuDvfsConfig()
    }

    private fun loadPowerSavingProfile() {
        ActivityLogger.log("Profile", "LOAD_POWER_SAVING", "Loading power saving profile settings")
        _gpuConfig.value = GpuDvfsConfig(
            marginMode = GpuMarginMode.MINIMUM,
            timerBaseDvfsMargin = 10,
            loadingBaseDvfsStep = 2
        )
        _memoryConfig.value = MemoryManagementConfig(
            features = MemoryFeatureConfig(
                appStartLimit = true,
                oomAdjClean = true,
                lowRamClean = true,
                lowSwapClean = true,
                oneKeyClean = true,
                heavyCpuClean = true,
                heavyIowClean = true,
                sleepClean = true,
                fixAdj = true,
                limit3rdStart = true,
                allowClean3rd = true
            )
        )
    }

    private fun loadBalancedProfile() {
        ActivityLogger.log("Profile", "LOAD_BALANCED", "Loading balanced profile settings")
        _gpuConfig.value = GpuDvfsConfig(
            marginMode = GpuMarginMode.BALANCED,
            timerBaseDvfsMargin = 10,
            loadingBaseDvfsStep = 4
        )
        _memoryConfig.value = MemoryManagementConfig(
            features = MemoryFeatureConfig(
                appStartLimit = true,
                oomAdjClean = true,
                lowRamClean = true,
                lowSwapClean = true,
                oneKeyClean = true,
                heavyCpuClean = false,
                heavyIowClean = false,
                sleepClean = true,
                fixAdj = true,
                limit3rdStart = true,
                allowClean3rd = true
            )
        )
    }

    private fun loadPerformanceProfile() {
        ActivityLogger.log("Profile", "LOAD_PERFORMANCE", "Loading performance profile settings")
        _gpuConfig.value = GpuDvfsConfig(
            marginMode = GpuMarginMode.HIGH,
            timerBaseDvfsMargin = 20,
            loadingBaseDvfsStep = 6
        )
        _memoryConfig.value = MemoryManagementConfig(
            features = MemoryFeatureConfig(
                appStartLimit = false,
                oomAdjClean = true,
                lowRamClean = true,
                lowSwapClean = false,
                oneKeyClean = false,
                heavyCpuClean = false,
                heavyIowClean = false,
                sleepClean = false,
                fixAdj = true,
                limit3rdStart = false,
                allowClean3rd = false
            ),
            thresholds = MemoryThresholdConfig(
                adjCached = 500,
                freeCached = 900
            )
        )
    }

    private fun loadGamingProfile() {
        ActivityLogger.log("Profile", "LOAD_GAMING", "Loading gaming profile settings")
        _gpuConfig.value = GpuDvfsConfig(
            marginMode = GpuMarginMode.MAXIMUM,
            timerBaseDvfsMargin = 30,
            loadingBaseDvfsStep = 8
        )
        _memoryConfig.value = MemoryManagementConfig(
            features = MemoryFeatureConfig(
                appStartLimit = false,
                oomAdjClean = true,
                lowRamClean = false,
                lowSwapClean = false,
                oneKeyClean = false,
                heavyCpuClean = false,
                heavyIowClean = false,
                sleepClean = false,
                fixAdj = true,
                limit3rdStart = false,
                allowClean3rd = false
            ),
            thresholds = MemoryThresholdConfig(
                adjCached = 400,
                freeCached = 1000
            ),
            processLimits = ProcessMemoryConfig(
                thirdParty = 150,
                gms = 150,
                system = 150,
                systemBg = 150,
                game = 400
            )
        )
    }

    fun updateGameConfig(packageName: String, config: GameTuningConfig) {
        val oldConfig = _gameConfigs.value[packageName]
        _gameConfigs.update { configs ->
            configs.toMutableMap().apply {
                this[packageName] = config
            }
        }
        _selectedProfile.value = TuningProfile.CUSTOM

        if (oldConfig != null) {
            ActivityLogger.logConfigChange(
                "GameTuning",
                "Game[$packageName]",
                oldConfig.toString(),
                config.toString()
            )
        }
    }

    fun updateScenarioConfig(scenarioName: String, config: PerformanceScenarioConfig) {
        val oldConfig = _scenarioConfigs.value[scenarioName]
        _scenarioConfigs.update { configs ->
            configs.toMutableMap().apply {
                this[scenarioName] = config
            }
        }
        _selectedProfile.value = TuningProfile.CUSTOM

        if (oldConfig != null) {
            ActivityLogger.logConfigChange(
                "PerformanceScenario",
                "Scenario[$scenarioName]",
                oldConfig.toString(),
                config.toString()
            )
        }
    }

    fun updateMemoryConfig(config: MemoryManagementConfig) {
        val oldConfig = _memoryConfig.value
        _memoryConfig.value = config
        _selectedProfile.value = TuningProfile.CUSTOM

        ActivityLogger.logConfigChange(
            "MemoryManagement",
            "MemoryConfig",
            oldConfig.toString(),
            config.toString()
        )
    }

    fun updateGpuConfig(config: GpuDvfsConfig) {
        val oldConfig = _gpuConfig.value
        _gpuConfig.value = config
        _selectedProfile.value = TuningProfile.CUSTOM

        ActivityLogger.logConfigChange(
            "GpuSettings",
            "GpuConfig",
            oldConfig.toString(),
            config.toString()
        )
    }

    fun exportConfiguration(): String {
        val config = FullTuningConfig(
            profile = _selectedProfile.value,
            gameConfigs = _gameConfigs.value,
            scenarioConfigs = _scenarioConfigs.value,
            memoryConfig = _memoryConfig.value,
            gpuConfig = _gpuConfig.value
        )

        ActivityLogger.logExport("Full Configuration")
        return buildXmlConfiguration(config)
    }

    fun getLogs(): String {
        return ActivityLogger.getFormattedLogs()
    }

    fun clearLogs() {
        ActivityLogger.clearLogs()
        ActivityLogger.log("App", "LOGS_CLEARED", "Activity log cleared by user")
    }

    private fun buildXmlConfiguration(config: FullTuningConfig): String {
        val sb = StringBuilder()
        sb.appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        sb.appendLine("<!-- X695C Vendor Tuning Configuration -->")
        sb.appendLine("<!-- Profile: ${config.profile.displayName} -->")
        sb.appendLine()

        // Game configurations
        sb.appendLine("<WHITELIST>")
        config.gameConfigs.forEach { (packageName, gameConfig) ->
            sb.appendLine("    <Package name=\"$packageName\">")
            sb.appendLine("        <Activity name=\"Common\">")

            if (gameConfig.thermalPolicy != ThermalPolicy.DEFAULT) {
                sb.appendLine("            <data cmd=\"PERF_RES_THERMAL_POLICY\" param1=\"${gameConfig.thermalPolicy.value}\"></data>")
            }
            if (gameConfig.gpuMarginMode != GpuMarginMode.BALANCED) {
                sb.appendLine("            <data cmd=\"PERF_RES_GPU_GED_MARGIN_MODE\" param1=\"${gameConfig.gpuMarginMode.value}\"></data>")
            }
            if (gameConfig.uclampMin != UclampMin.NONE) {
                sb.appendLine("            <data cmd=\"PERF_RES_SCHED_UCLAMP_MIN_TA\" param1=\"${gameConfig.uclampMin.value}\"></data>")
            }
            if (gameConfig.schedBoost != SchedBoost.DISABLED) {
                sb.appendLine("            <data cmd=\"PERF_RES_SCHED_BOOST\" param1=\"${gameConfig.schedBoost.value}\"></data>")
            }
            if (gameConfig.fpsMarginMode != FpsMarginMode.DISABLED) {
                sb.appendLine("            <data cmd=\"PERF_RES_FPS_FPSGO_MARGIN_MODE\" param1=\"${gameConfig.fpsMarginMode.value}\"></data>")
            }
            if (gameConfig.fpsAdjustLoading) {
                sb.appendLine("            <data cmd=\"PERF_RES_FPS_FPSGO_ADJ_LOADING\" param1=\"1\"></data>")
            }
            if (gameConfig.fpsLoadingThreshold != FpsLoadingThreshold.STANDARD) {
                sb.appendLine("            <data cmd=\"PERF_RES_FPS_FPSGO_LLF_TH\" param1=\"${gameConfig.fpsLoadingThreshold.value}\"></data>")
            }
            if (gameConfig.gpuBlockBoost != GpuBlockBoost.DISABLED) {
                sb.appendLine("            <data cmd=\"PERF_RES_FPS_FPSGO_GPU_BLOCK_BOOST\" param1=\"${gameConfig.gpuBlockBoost.value}\"></data>")
            }
            if (gameConfig.networkBoost != NetworkBoost.DISABLED) {
                sb.appendLine("            <data cmd=\"PERF_RES_NET_NETD_BOOST_UID\" param1=\"${gameConfig.networkBoost.value}\"></data>")
            }
            if (gameConfig.wifiLowLatency == WifiLowLatency.ENABLED) {
                sb.appendLine("            <data cmd=\"PERF_RES_NET_WIFI_LOW_LATENCY\" param1=\"1\"></data>")
            }
            if (gameConfig.weakSignalOpt == WeakSignalOpt.ENABLED) {
                sb.appendLine("            <data cmd=\"PERF_RES_NET_MD_WEAK_SIG_OPT\" param1=\"1\"></data>")
            }

            sb.appendLine("        </Activity>")
            sb.appendLine("    </Package>")
        }
        sb.appendLine("</WHITELIST>")

        return sb.toString()
    }
}

data class TunerUiState(
    val isLoading: Boolean = false,
    val message: String? = null,
    val exportResult: String? = null
)
