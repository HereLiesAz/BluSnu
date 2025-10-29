# Blu Snu: File Descriptions

This document provides a description of the purpose of each file and directory in the Blu Snu project.

## Root Directory

*   `README.md`: The main README file for the project, providing a high-level overview.
*   `app/`: The main application module.
*   `build.gradle.kts`: The main build script for the project.
*   `docs/`: The project's documentation.
*   `gradle/`: The Gradle wrapper files.
*   `gradle.properties`: The Gradle configuration properties.
*   `gradlew`: The Gradle wrapper script for Linux and macOS.
*   `gradlew.bat`: The Gradle wrapper script for Windows.
*   `settings.gradle.kts`: The Gradle settings script.

## `app/`

*   `build.gradle.kts`: The build script for the `app` module.
*   `proguard-rules.pro`: The ProGuard rules for the `app` module.
*   `src/`: The source code for the `app` module.

## `app/src/main/`

*   `AndroidManifest.xml`: The Android manifest file.
*   `assets/`: The application's assets.
*   `java/`: The Java source code.
*   `res/`: The application's resources.

## `app/src/main/java/com/hereliesaz/blusnu/`

*   `MainActivity.kt`: The main activity for the application.
*   `data/`: The data layer for the application.
*   `ui/`: The UI layer for the application.
*   `utils/`: The utility classes for the application.

## `app/src/main/java/com/hereliesaz/blusnu/data/`

*   `AppDatabase.kt`: The Room database for the application.
*   `BluetoothScanner.kt`: The class responsible for scanning for Bluetooth devices.
*   `DeviceRepository.kt`: The repository for managing `TargetDevice` data.
*   `TargetDevice.kt`: The data class for representing a Bluetooth device.
*   `VulnerabilityCorrelator.kt`: The class responsible for correlating discovered devices with known vulnerabilities.

## `app/src/main/java/com/hereliesaz/blusnu/ui/`

*   `dashboard/`: The dashboard screen.
*   `devicemanagement/`: The device management screen.
*   `attackchaining/`: The attack chaining screen.
*   `bluesnarfing/`: The Bluesnarfing screen.
*   `...`: Other UI screens and components.

## `docs/`

*   `Bluetooth Hacking App Blueprint.md`: A detailed blueprint for the Blu Snu application.
*   `INDEX.md`: The main entry point for the documentation.
*   `TODO.md`: The project's task list.
*   `UI_UX.md`: A description of the application's UI/UX design.
*   `auth.md`: A description of the application's authentication and authorization mechanisms.
*   `conduct.md`: The code of conduct for contributors.
*   `data_layer.md`: A description of the application's data layer.
*   `fauxpas.md`: A list of common mistakes and anti-patterns to avoid.
*   `file_descriptions.md`: A description of the purpose of each file in the project.
*   `misc.md`: Miscellaneous documentation.
*   `performance.md`: A discussion of performance considerations.
*   `screens.md`: A detailed description of each screen in the application.
*   `task_flow.md`: A description of the typical workflow for a user of the application.
*   `testing.md`: A description of the testing strategy for the project.
*   `workflow.md`: A description of the development workflow for contributors.

## `gradle/`

*   `libs.versions.toml`: The version catalog for the project's dependencies.
*   `wrapper/`: The Gradle wrapper files.
