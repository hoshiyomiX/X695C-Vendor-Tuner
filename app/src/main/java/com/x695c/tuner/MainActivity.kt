package com.x695c.tuner

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import com.x695c.tuner.data.*
import com.x695c.tuner.ui.screens.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            X695CTunerTheme {
                TunerApp()
            }
        }
    }
}

@Composable
fun X695CTunerTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = md_theme_primary,
            onPrimary = md_theme_onPrimary,
            primaryContainer = md_theme_primaryContainer,
            onPrimaryContainer = md_theme_onPrimaryContainer,
            secondary = md_theme_secondary,
            onSecondary = md_theme_onSecondary,
            secondaryContainer = md_theme_secondaryContainer,
            onSecondaryContainer = md_theme_onSecondaryContainer,
            tertiary = md_theme_tertiary,
            onTertiary = md_theme_onTertiary,
            tertiaryContainer = md_theme_tertiaryContainer,
            onTertiaryContainer = md_theme_onTertiaryContainer,
            error = md_theme_error,
            onError = md_theme_onError,
            errorContainer = md_theme_errorContainer,
            onErrorContainer = md_theme_onErrorContainer,
            background = md_theme_background,
            onBackground = md_theme_onBackground,
            surface = md_theme_surface,
            onSurface = md_theme_onSurface,
            surfaceVariant = md_theme_surfaceVariant,
            onSurfaceVariant = md_theme_onSurfaceVariant
        ),
        content = content
    )
}

// Dark theme colors - Material 3 Expressive
private val md_theme_primary = Color(0xFF6BB5FF)
private val md_theme_onPrimary = Color(0xFF003258)
private val md_theme_primaryContainer = Color(0xFF1F4E79)
private val md_theme_onPrimaryContainer = Color(0xFFD1E4FF)
private val md_theme_secondary = Color(0xFFBAC6DC)
private val md_theme_onSecondary = Color(0xFF243043)
private val md_theme_secondaryContainer = Color(0xFF3A465A)
private val md_theme_onSecondaryContainer = Color(0xFFD6E2F9)
private val md_theme_tertiary = Color(0xFFD9BDE6)
private val md_theme_onTertiary = Color(0xFF3C2849)
private val md_theme_tertiaryContainer = Color(0xFF533E60)
private val md_theme_onTertiaryContainer = Color(0xFFf5D9FF)
private val md_theme_error = Color(0xFFFFB4AB)
private val md_theme_onError = Color(0xFF690005)
private val md_theme_errorContainer = Color(0xFF93000A)
private val md_theme_onErrorContainer = Color(0xFFFFDAD6)
private val md_theme_background = Color(0xFF0A1118)
private val md_theme_onBackground = Color(0xFFE1E2E8)
private val md_theme_surface = Color(0xFF0A1118)
private val md_theme_onSurface = Color(0xFFE1E2E8)
private val md_theme_surfaceVariant = Color(0xFF42474E)
private val md_theme_onSurfaceVariant = Color(0xFFC2C7CF)

sealed class Screen {
    object Main : Screen()
    object Games : Screen()
    data class GameDetail(val packageName: String) : Screen()
    object Scenarios : Screen()
    data class ScenarioDetail(val scenarioName: String) : Screen()
    object Memory : Screen()
    object Gpu : Screen()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TunerApp(
    viewModel: TunerViewModel = viewModel()
) {
    val selectedProfile by viewModel.selectedProfile.collectAsState()
    val gameConfigs by viewModel.gameConfigs.collectAsState()
    val scenarioConfigs by viewModel.scenarioConfigs.collectAsState()
    val memoryConfig by viewModel.memoryConfig.collectAsState()
    val gpuConfig by viewModel.gpuConfig.collectAsState()
    val configAvailability by viewModel.configAvailability.collectAsState()

    // Navigation stack
    var screenStack by remember { mutableStateOf<List<Screen>>(listOf(Screen.Main)) }

    // Helper functions
    fun navigateTo(screen: Screen) {
        screenStack = screenStack + screen
    }

    fun navigateBack(): Boolean {
        return if (screenStack.size > 1) {
            screenStack = screenStack.dropLast(1)
            true
        } else {
            false
        }
    }

    // Back handler for gesture back - intercept and navigate within app
    BackHandler(enabled = screenStack.size > 1) {
        navigateBack()
    }

    // Check config availability
    val gameConfigAvailable = configAvailability[ConfigFileDetector.ConfigType.GAME_WHITELIST]?.available ?: false
    val scenarioConfigAvailable = configAvailability[ConfigFileDetector.ConfigType.PERFORMANCE_SCENARIOS]?.available ?: false
    val memoryConfigAvailable = configAvailability[ConfigFileDetector.ConfigType.MEMORY_MANAGEMENT]?.available ?: false
    val gpuConfigAvailable = configAvailability[ConfigFileDetector.ConfigType.GPU_DVFS]?.available ?: false

    // Scaffold with proper window insets handling for status bar
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            val currentScreen = screenStack.lastOrNull() ?: Screen.Main

            when (currentScreen) {
                is Screen.Main -> MainDashboardScreen(
                    selectedProfile = selectedProfile,
                    onProfileChange = { viewModel.setProfile(it) },
                    gameConfigs = gameConfigs,
                    scenarioConfigs = scenarioConfigs,
                    memoryConfig = memoryConfig,
                    gpuConfig = gpuConfig,
                    gameConfigAvailable = gameConfigAvailable,
                    scenarioConfigAvailable = scenarioConfigAvailable,
                    memoryConfigAvailable = memoryConfigAvailable,
                    gpuConfigAvailable = gpuConfigAvailable,
                    onNavigateToGames = { navigateTo(Screen.Games) },
                    onNavigateToScenarios = { navigateTo(Screen.Scenarios) },
                    onNavigateToMemory = { navigateTo(Screen.Memory) },
                    onNavigateToGpu = { navigateTo(Screen.Gpu) }
                )

                is Screen.Games -> GameListScreen(
                    gameConfigs = gameConfigs,
                    configAvailable = gameConfigAvailable,
                    onGameSelect = { navigateTo(Screen.GameDetail(it)) },
                    onAddGame = { packageName -> viewModel.addCustomGame(packageName) },
                    onBack = { navigateBack() }
                )

                is Screen.GameDetail -> {
                    val config = gameConfigs[currentScreen.packageName]
                    if (config != null) {
                        GameTuningScreen(
                            packageName = currentScreen.packageName,
                            config = config,
                            onConfigChange = { viewModel.updateGameConfig(currentScreen.packageName, it) },
                            onBack = { navigateBack() }
                        )
                    } else {
                        LaunchedEffect(Unit) { navigateBack() }
                    }
                }

                is Screen.Scenarios -> ScenarioListScreen(
                    scenarioConfigs = scenarioConfigs,
                    configAvailable = scenarioConfigAvailable,
                    onScenarioSelect = { navigateTo(Screen.ScenarioDetail(it)) },
                    onBack = { navigateBack() }
                )

                is Screen.ScenarioDetail -> {
                    val config = scenarioConfigs[currentScreen.scenarioName]
                    if (config != null) {
                        PerformanceScenarioScreen(
                            scenarioName = currentScreen.scenarioName,
                            config = config,
                            onConfigChange = { viewModel.updateScenarioConfig(currentScreen.scenarioName, it) },
                            onBack = { navigateBack() }
                        )
                    } else {
                        LaunchedEffect(Unit) { navigateBack() }
                    }
                }

                is Screen.Memory -> MemoryManagementScreen(
                    config = memoryConfig,
                    configAvailable = memoryConfigAvailable,
                    onConfigChange = { viewModel.updateMemoryConfig(it) },
                    onBack = { navigateBack() }
                )

                is Screen.Gpu -> GpuSettingsScreen(
                    config = gpuConfig,
                    configAvailable = gpuConfigAvailable,
                    onConfigChange = { viewModel.updateGpuConfig(it) },
                    onBack = { navigateBack() }
                )
            }
        }
    }
}
