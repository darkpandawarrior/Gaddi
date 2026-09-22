@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// AGP's KMP library plugin has no Android assets support, so CMP's
// copyAndroidMainComposeResourcesToAndroidAssets never gets its outputDirectory wired and the
// resources never reach the APK. Placed immediately after `plugins {}` so it runs before the KMP
// plugin finalises targets: appending it at the end of this file breaks composite-build
// substitution for :cmp-desktop.
// Publish that fixed output as a consumable variant so :cmp-android (a real
// com.android.application, which does have assets support) can merge it into its own assets.
// The artifact is a deferred provider: CMP registers the task later than this file is evaluated.
configurations.create("composeAndroidAssetsElements") {
    isCanBeConsumed = true
    isCanBeResolved = false
    val outputDirectory =
        provider {
            val task = tasks.getByName("copyAndroidMainComposeResourcesToAndroidAssets")
            task.javaClass.getMethod("getOutputDirectory").invoke(task)
                as org.gradle.api.file.DirectoryProperty
        }.flatMap { it }
    outgoing.artifact(outputDirectory) {
        builtBy("copyAndroidMainComposeResourcesToAndroidAssets")
    }
}

tasks.configureEach {
    if (name == "copyAndroidMainComposeResourcesToAndroidAssets") {
        val out =
            this.javaClass.getMethod("getOutputDirectory").invoke(this)
                as org.gradle.api.file.DirectoryProperty
        out.set(layout.buildDirectory.dir("composeResourcesForAndroidAssets"))
    }
}

kotlin {
    jvm()
    iosArm64()
    iosSimulatorArm64()
    wasmJs { browser() }

    android {
        namespace = "com.kursi.designsystem"
        compileSdk = 37
        minSdk = 26
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.runtime)
            implementation(libs.foundation)
            implementation(libs.material3)
            implementation(libs.ui)
            // @Preview for the design-system tokens (DesignSystemPreviews.kt). The RENDERER
            // (org.jetbrains.compose.ui:ui-tooling) is deliberately not declared: it is
            // androidRuntimeClasspath-only under AGP's KMP library plugin, and the IDE preview
            // panel needs an Android Studio that understands AGP 9.5.0-alpha06 anyway. These
            // previews compile on every target this module declares, which is the part that holds.
            implementation(libs.ui.tooling.preview.mp)
            // material-icons-extended: AccountBalance, LocalFireDepartment, Work, SwapHoriz, Gavel
            // for role glyphs — all targets supported by CMP 1.11.x
            implementation(libs.material.icons.extended)
            // compose.components.resources: enables Res.font.* for bundled TTF files
            // under src/commonMain/composeResources/font/ (Rozha One, Marcellus, DM Mono)
            implementation(libs.components.resources)
            implementation(project(":engine"))
            implementation("com.siddharth.kmp:feedback:1.0.0")
            implementation("com.siddharth.kmp:common:1.0.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// Res class defaults to internal; other modules (cmp-shared) read Res.string.* directly, so it
// must be public here (scaffold for spec §13 string-resource path — see KursiMotion / strings.xml).
compose.resources {
    publicResClass = true
}
