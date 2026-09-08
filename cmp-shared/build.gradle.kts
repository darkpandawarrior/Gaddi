@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvm()
    iosArm64()
    iosSimulatorArm64()
    wasmJs {
        browser()
        // CMP-4906: without a declared executable the Compose plugin's Skiko-runtime check fails
        // `check` outright, because Compose UI cannot load its renderer from a bare klib. This gate
        // was permanently red before 2026-08-27 - it never told anyone anything, it just failed.
        // Same fix the toolkit's :designsystem already carries.
        binaries.executable()
    }

    android {
        namespace = "com.kursi.shared"
        compileSdk = 37
        minSdk = 26
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.runtime)
            implementation(libs.foundation)
            implementation(libs.material3)
            implementation(libs.ui)
            implementation(libs.components.resources)
            implementation(project(":feature:game"))
            implementation(project(":core:designsystem"))
            implementation("com.siddharth.kmp:feedback:1.0.0")
            // capturable()/rememberCaptureController + ImageBitmap.toPngBytes(), for sharing the
            // Faisla certificate as an image rather than as plain text.
            implementation("com.siddharth.kmp:designsystem:1.0.0")
            // AiSettingsSection (from :designsystem above) takes ModelManager/OnDeviceLlm (:ai) and
            // ProviderId/SecureKeyStore (:llm-chat) types directly in its own signature — :designsystem
            // depends on both as `implementation`, which hides them from OUR compile classpath, so
            // SettingsScreen needs its own direct line for the same two coordinates to reference them.
            implementation("com.siddharth.kmp:ai:1.0.0")
            implementation("com.siddharth.kmp:llm-chat:1.0.0")
            implementation(project(":core:prefs"))
            implementation(project(":core:network"))
            implementation(project(":engine"))
            implementation(project(":ai"))
            implementation(libs.navigation.compose)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
