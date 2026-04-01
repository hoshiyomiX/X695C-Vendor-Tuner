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

    // Root access state
    private val _rootState = MutableStateFlow(RootState())
    val rootState: StateFlow<RootState> = _rootState.asStateFlow()

    // Config change tracking state
    private val _configChangeStatus = MutableStateFlow<Map<ConfigFileDetector.ConfigType, ConfigChangeTracker.ChangeStatus>>(emptyMap())
    val configChangeStatus: StateFlow<Map<ConfigFileDetector.ConfigType, ConfigChangeTracker.ChangeStatus>> = _configChangeStatus.asStateFlow()

    // Apply state for writing configurations
    private val _applyState = MutableStateFlow(ApplyState())
    val applyState: StateFlow<ApplyState> = _applyState.asStateFlow()

    init {
        // Log app start
        ActivityLogger.log("App", "INIT", "X695C Vendor Tuner started")

        // Check root availability (non-blocking)
        checkRootAvailability()

        // Detect and parse config files
        detectAndLoadConfigs()
    }

    // ==================== ROOT ACCESS METHODS ====================

    /**
     * Check if root is available on the device.
     */
    private fun checkRootAvailability() {
        viewModelScope.launch {
            val isAvailable = RootChecker.isRootAvailable()
            _rootState.value = RootState(
                isAvailable = isAvailable,
                hasBeenRequested = false,
                isGranted = isAvailable
            )

            if (isAvailable) {
                ActivityLogger.log("Root", "DETECTED", "Root access detected on device")
            } else {
                ActivityLogger.log("Root", "NOT_DETECTED", "No root access detected")
            }
        }
    }

    /**
     * Request root access from the user.
     * This will show a popup on rooted devices.
     */
    fun requestRootAccess() {
        viewModelScope.launch {
            _rootState.value = _rootState.value.copy(isRequesting = true)

            val granted = RootChecker.requestRootAccess()

            _rootState.value = RootState(
                isAvailable = granted,
                hasBeenRequested = true,
                isGranted = granted,
                isRequesting = false
            )

            if (granted) {
                ActivityLogger.log("Root", "GRANTED", "Root access granted by user")
                // Initialize config change baseline after getting root
                initializeConfigChangeBaseline()
            } else {
                ActivityLogger.log("Root", "DENIED", "Root access denied by user or not available")
            }
        }
    }

    /**
     * Dismiss the root request dialog without granting.
     */
    fun dismissRootRequest() {
        _rootState.value = _rootState.value.copy(
            hasBeenRequested = true,
            isRequesting = false
        )
        ActivityLogger.log("Root", "DISMISSED", "Root request dialog dismissed")
    }

    /**
     * Check if root should be requested.
     */
    fun shouldRequestRoot(): Boolean {
        val state = _rootState.value
        return !state.isAvailable && !state.hasBeenRequested && !state.isRequesting
    }

    // ==================== CONFIG CHANGE TRACKING METHODS ====================

    /**
     * Initialize config change detection baseline.
     */
    private fun initializeConfigChangeBaseline() {
        viewModelScope.launch {
            ConfigChangeTracker.initializeBaseline()
            checkConfigChanges()
        }
    }

    /**
     * Check all config files for external changes.
     */
    fun checkConfigChanges() {
        viewModelScope.launch {
            val changes = ConfigChangeTracker.checkAllForChanges()
            val statuses = ConfigChangeTracker.getAllChangeStatuses()
            _configChangeStatus.value = statuses

            // Log any detected changes
            changes.values.filter { it.isExternal }.forEach { result ->
                ActivityLogger.log("ConfigChange", "EXTERNAL_MODIFICATION", "${result.obfuscatedPath} was modified externally")
            }
        }
    }

    /**
     * Get formatted logs for copying.
     */
    fun getFormattedLogs(): String {
        return ActivityLogger.getFormattedLogs()
    }

    /**
     * Check if any config has external changes.
     */
    fun hasExternalConfigChanges(): Boolean {
        return ConfigChangeTracker.hasAnyExternalChanges()
    }

    /**
     * Save current config states as known baseline.
     * Call this after applying config changes from the APK.
     */
    fun saveCurrentConfigStatesAsKnown() {
        ConfigChangeTracker.saveAllCurrentStatesAsKnown()
        checkConfigChanges()
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

        // Mark as having unsaved changes (require apply to write)
        _uiState.value = _uiState.value.copy(hasUnsavedChanges = true)
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
        _uiState.value = _uiState.value.copy(hasUnsavedChanges = true)

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
        _uiState.value = _uiState.value.copy(hasUnsavedChanges = true)

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
        _uiState.value = _uiState.value.copy(hasUnsavedChanges = true)

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
        _uiState.value = _uiState.value.copy(hasUnsavedChanges = true)

        ActivityLogger.logConfigChange(
            "GpuSettings",
            "GpuConfig",
            oldConfig.toString(),
            config.toString()
        )
    }

    /**
     * Add a custom game by package name.
     * Creates a default config for the new game.
     */
    fun addCustomGame(packageName: String) {
        if (packageName.isBlank() || _gameConfigs.value.containsKey(packageName)) {
            return
        }

        val newConfig = GameTuningConfig(packageName = packageName)
        _gameConfigs.update { configs ->
            configs.toMutableMap().apply {
                this[packageName] = newConfig
            }
        }
        _selectedProfile.value = TuningProfile.CUSTOM
        _uiState.value = _uiState.value.copy(hasUnsavedChanges = true)
        
        ActivityLogger.log("GameTuning", "ADD_GAME", "Added custom game: $packageName")
    }

    // ==================== CONFIGURATION APPLY METHODS ====================

    /**
     * Check if root is available and granted.
     * Returns true if the app can write to system files.
     */
    fun canWriteConfigs(): Boolean {
        return _rootState.value.isGranted || RootChecker.isRootAvailable()
    }

    /**
     * Apply all current configurations to the system.
     * REQUIRES ROOT ACCESS!
     * 
     * This will write to:
     * - /vendor/etc/power_app_cfg.xml (game configs)
     * - /vendor/etc/powerscntbl.xml (scenario configs)
     * - /vendor/etc/policy_config_6g_ram.json (memory config)
     * - /vendor/etc/gpu_dvfs_setting.xml (GPU config)
     */
    fun applyConfiguration() {
        // Check root access first
        if (!canWriteConfigs()) {
            ActivityLogger.logError("ConfigApply", "Cannot apply: Root access required")
            _applyState.value = ApplyState(
                isApplying = false,
                lastResult = ApplyResult(
                    success = false,
                    errorMessages = listOf("Root access required to modify system files")
                ),
                showResultDialog = true
            )
            return
        }

        viewModelScope.launch {
            _applyState.value = _applyState.value.copy(isApplying = true)
            ActivityLogger.log("ConfigApply", "START", "Applying configuration changes...")

            try {
                // Write all configs using ConfigWriter
                val results = ConfigWriter.writeAllConfigs(
                    gameConfigs = _gameConfigs.value,
                    scenarioConfigs = _scenarioConfigs.value,
                    memoryConfig = _memoryConfig.value,
                    gpuConfig = _gpuConfig.value
                )

                // Process results
                val gameResult = results.find { it.configName == "game_cfg" }
                val scenarioResult = results.find { it.configName == "scenario_cfg" }
                val memoryResult = results.find { it.configName == "mem_cfg" }
                val gpuResult = results.find { it.configName == "gpu_cfg" }

                val errors = results.filter { !it.success }.mapNotNull { it.errorMessage }
                val success = results.all { it.success }

                val applyResult = ApplyResult(
                    success = success,
                    gameConfigWritten = gameResult?.success ?: false,
                    scenarioConfigWritten = scenarioResult?.success ?: false,
                    memoryConfigWritten = memoryResult?.success ?: false,
                    gpuConfigWritten = gpuResult?.success ?: false,
                    errorMessages = errors
                )

                if (success) {
                    ActivityLogger.log("ConfigApply", "SUCCESS", "All configurations applied successfully")
                    // Update change tracking baseline
                    saveCurrentConfigStatesAsKnown()
                    // Clear unsaved changes flag
                    _uiState.value = _uiState.value.copy(hasUnsavedChanges = false)
                } else {
                    ActivityLogger.logError("ConfigApply", "Apply failed: ${errors.joinToString(", ")}")
                }

                _applyState.value = ApplyState(
                    isApplying = false,
                    lastResult = applyResult,
                    showResultDialog = true
                )
            } catch (e: Exception) {
                ActivityLogger.logError("ConfigApply", "Exception during apply: ${e.message}")
                _applyState.value = ApplyState(
                    isApplying = false,
                    lastResult = ApplyResult(
                        success = false,
                        errorMessages = listOf(e.message ?: "Unknown error")
                    ),
                    showResultDialog = true
                )
            }
        }
    }

    /**
     * Dismiss the apply result dialog.
     */
    fun dismissApplyResult() {
        _applyState.value = _applyState.value.copy(showResultDialog = false)
    }

    /**
     * Check if there are unsaved configuration changes.
     */
    fun hasUnsavedChanges(): Boolean = _uiState.value.hasUnsavedChanges
}

data class TunerUiState(
    val isLoading: Boolean = false,
    val message: String? = null,
    val hasUnsavedChanges: Boolean = false
)

/**
 * State for root access management
 */
data class RootState(
    val isAvailable: Boolean = false,       // Root is available on device
    val hasBeenRequested: Boolean = false,  // User has seen the request dialog
    val isGranted: Boolean = false,         // Root has been granted
    val isRequesting: Boolean = false       // Currently requesting root
)

/**
 * State for configuration apply operations
 */
data class ApplyState(
    val isApplying: Boolean = false,
    val lastResult: ApplyResult? = null,
    val showResultDialog: Boolean = false
)

/**
 * Result of an apply operation
 */
data class ApplyResult(
    val success: Boolean,
    val gameConfigWritten: Boolean = false,
    val scenarioConfigWritten: Boolean = false,
    val memoryConfigWritten: Boolean = false,
    val gpuConfigWritten: Boolean = false,
    val errorMessages: List<String> = emptyList()
) {
    val successCount: Int
        get() = listOf(gameConfigWritten, scenarioConfigWritten, memoryConfigWritten, gpuConfigWritten)
            .count { it }

    val totalConfigs: Int
        get() = 4
}
