plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

/**
 * iOS umbrella module: produces the single `KursiKit.framework` the Xcode app links against.
 *
 * This module sits ABOVE :cmp-shared (and transitively above :feature:game, :core:designsystem,
 * :engine, :ai) — mirroring the Mileway :shared pattern exactly. It must never be depended upon
 * by any feature or core module (that would introduce a cycle). Adding more iOS-facing entry
 * points in the future means adding api()/export() here, never making a feature depend on
 * its siblings.
 *
 * Exported API surfaced to Swift:
 *  - :cmp-shared → KursiApp() composable, via MainViewController() entry point defined below.
 */
kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "KursiKit"
            isStatic = true
            // export() requires api() in the source set below to surface the module's public API.
            // `project(":x")` here resolves to Project.project(String) — a Project OBJECT, which is
            // deprecated dependency notation and fails in Gradle 10. Inside the sourceSets
            // dependencies { } block below the same call hits KotlinDependencyHandler.project and is
            // already fine; only this one, on a Framework receiver, needed the DependencyHandler.
            export(dependencies.project(":cmp-shared"))
        }
    }

    sourceSets {
        iosMain.dependencies {
            // api(...) is required for export(...) above to surface :cmp-shared's public API in the framework.
            api(project(":cmp-shared"))
            // compose.ui exposes ComposeUIViewController on iOS targets.
            implementation(libs.ui)
        }
    }
}
