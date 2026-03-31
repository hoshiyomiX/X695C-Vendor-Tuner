# X695C Vendor Optimizer

Android application with Jetpack Compose for configuring INFINIX X695C vendor optimization parameters.

## Device Information

- **Device**: INFINIX Hot 11S / X695C
- **SoC**: MediaTek Helio G95 (MT6785)
- **GPU**: Mali-G76 MC4
- **RAM**: 6GB

## Features

### 🎮 Game Optimization
- Per-game performance profiles
- Thermal policy configuration
- GPU DVFS margin settings
- Network optimization for gaming
- FPS and frame control settings

### ⚡ Performance Scenarios
- App launch boost
- Touch response optimization
- Screen rotation boost
- Fingerprint scanner optimization
- Process creation boost

### 💾 Memory Management
- Memory threshold configuration
- Process memory limits
- Feature flags for memory behavior
- Swap settings

### 🎨 GPU Settings
- DVFS margin configuration
- Timer-based DVFS control
- Loading-based DVFS step
- Quick presets

## Optimization Profiles

| Profile | Description |
|---------|-------------|
| Default | Stock device configuration |
| Power Saving | Optimized for battery life |
| Balanced | Balance between performance and battery |
| Performance | Optimized for smooth operation |
| Gaming | Maximum performance for gaming |
| Custom | User-defined configuration |

## Parameters

### Thermal Policy
| Value | Description |
|-------|-------------|
| 0 | Default (No Override) |
| 1 | Conservative (Cool) |
| 4 | Balanced |
| 8 | Performance (Gaming) |
| 12 | Aggressive (Max Performance) |

### GPU Margin Mode
| Value | Description |
|-------|-------------|
| 10 | Minimum (Power Saving) |
| 30 | Low |
| 50 | Balanced |
| 80 | High |
| 110 | Maximum (Performance) |

### UCLAMP Min (CPU Capacity)
| Value | Description |
|-------|-------------|
| 0 | None (0%) |
| 20 | Low (20%) |
| 40 | Medium (40%) |
| 60 | High (60%) |
| 80 | Very High (80%) |
| 100 | Maximum (100%) |

## Building

### Prerequisites
- Android Studio Hedgehog or later
- JDK 17
- Android SDK 34

### Build APK
```bash
./gradlew assembleRelease
```

The APK will be generated at `app/build/outputs/apk/release/app-release.apk`

## Project Structure

```
app/src/main/java/com/x695c/optimizer/
├── MainActivity.kt          # Main activity and theme
├── data/
│   ├── OptimizationConfig.kt # Data models and enums
│   └── OptimizerViewModel.kt # ViewModel for state management
└── ui/
    ├── components/
    │   └── DropdownComponents.kt # Reusable dropdown components
    └── screens/
        ├── MainDashboardScreen.kt
        ├── GameListScreen.kt
        ├── GameOptimizationScreen.kt
        ├── ScenarioListScreen.kt
        ├── PerformanceScenarioScreen.kt
        ├── MemoryManagementScreen.kt
        └── GpuSettingsScreen.kt
```

## Technology Stack

- **UI**: Jetpack Compose with Material 3
- **Architecture**: MVVM with ViewModel
- **Kotlin**: 1.9.20
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 34 (Android 14)

## ⚠️ Disclaimer

This application generates configuration files that need to be applied to the vendor partition of a rooted device. Modifying vendor files can:

- Void your warranty
- Cause system instability
- Lead to bootloops if misconfigured
- Increase device temperature and power consumption

**Use at your own risk. Always backup your original configuration files before making changes.**

## Configuration Files

The generated configurations can be applied to:
- `/vendor/etc/power_app_cfg.xml` - Game optimization whitelist
- `/vendor/etc/powerscntbl.xml` - Performance scenario profiles
- `/vendor/etc/performance/policy_config_6g_ram.json` - Memory management

## License

This project is provided for educational and research purposes.

## Credits

- Device vendor analysis based on INFINIX X695C firmware
- MediaTek MT6785 performance tuning documentation
