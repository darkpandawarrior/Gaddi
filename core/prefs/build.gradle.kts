@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
}

kotlin {
    jvm()
    iosArm64()
    iosSimulatorArm64()
    wasmJs { browser() }

    android {
        namespace = "com.kursi.core.prefs"
        compileSdk {
            version = release(37) { minorApiLevel = 1 }
        }
        minSdk = 26
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.multiplatform.settings.no.arg)
            implementation(libs.multiplatform.settings)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            // MapSettings (in-memory backing store) for deterministic ledger/snapshot tests.
            implementation(libs.multiplatform.settings.test)
        }
    }
}
