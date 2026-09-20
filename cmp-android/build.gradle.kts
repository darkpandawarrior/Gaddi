import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.FileInputStream
import java.util.Properties
import javax.inject.Inject

plugins {
    id("com.android.application")
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// ── Three-tier versioning ──────────────────────────────────────────────────────
// FINGERPRINT/MARKETING/BUILDCODE are computed in gradle/versioning.gradle.kts from
// MILESTONE + git commit count (see docs/RELEASE.md). Bump MILESTONE with
// scripts/bump_version.sh --milestone.
apply(from = "$rootDir/gradle/versioning.gradle.kts")

fun readVersionName(): String = extra["kursiMarketing"] as String

fun readFingerprint(): String = extra["kursiFingerprint"] as String

fun readBuildCode(): Int = extra["kursiBuildCode"] as Int

// ── Release signing ────────────────────────────────────────────────────────────
// Reads from keystore.properties (copy keystore.properties.template and fill in).
// Falls back to RELEASE_* environment variables for CI.
// Falls back to debug signing if neither is present so `assembleRelease` still
// works locally and in CI without secrets configured.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties =
    Properties().apply {
        if (keystorePropertiesFile.exists()) {
            FileInputStream(keystorePropertiesFile).use { load(it) }
        }
    }
val hasReleaseSigning =
    keystorePropertiesFile.exists() || System.getenv("RELEASE_STORE_FILE") != null

// F-Droid reproducible build flag (`./gradlew :cmp-android:assembleNoGmsRelease -Pfdroid`).
// Disables R8/resource shrinking, which isn't bit-for-bit reproducible across machines.
val fdroidBuild = providers.gradleProperty("fdroid").isPresent

android {
    namespace = "com.kursi.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.kursi.android"
        minSdk = 26
        targetSdk = 37
        ndk { abiFilters += setOf("arm64-v8a", "armeabi-v7a") } // drop emulator-only x86/x86_64 — ~61MB of libllm_inference_engine_jni.so
        // No values-<locale> dir ships in this app (checked: only values/strings.xml exists) — this
        // only strips the *other* locales' copies of strings that AndroidX/Compose/Material bring in
        // for their own UI (e.g. accessibility labels), not anything the app itself translates.
        resourceConfigurations += setOf("en")
        versionCode = readBuildCode()
        versionName = readVersionName()
        buildConfigField("String", "FINGERPRINT", "\"${readFingerprint()}\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // gms: full feature set (Firebase Cloud Messaging, Play Core review/update) — what ships to
    // Play/Indus. noGms: strips those non-free deps for F-Droid (see publish-fdroid.yml).
    flavorDimensions += "services"
    productFlavors {
        create("gms") {
            dimension = "services"
        }
        create("noGms") {
            dimension = "services"
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile =
                    file(
                        keystoreProperties.getProperty("storeFile")
                            ?: System.getenv("RELEASE_STORE_FILE"),
                    )
                storePassword =
                    keystoreProperties.getProperty("storePassword")
                        ?: System.getenv("RELEASE_STORE_PASSWORD")
                keyAlias =
                    keystoreProperties.getProperty("keyAlias")
                        ?: System.getenv("RELEASE_KEY_ALIAS")
                keyPassword =
                    keystoreProperties.getProperty("keyPassword")
                        ?: System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
            isMinifyEnabled = false
            versionNameSuffix = "-${readFingerprint()}"
        }
        // QA/staging: minified but debug-signed; installs alongside the debug build.
        create("staging") {
            initWith(getByName("release"))
            applicationIdSuffix = ".staging"
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            versionNameSuffix = "-staging"
            matchingFallbacks += "release"
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = !fdroidBuild
            isShrinkResources = !fdroidBuild
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig =
                if (hasReleaseSigning) {
                    signingConfigs.getByName("release")
                } else {
                    signingConfigs.getByName("debug")
                }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// ── Compose resources on Android ──────────────────────────────────────────────
// Consumer half of the workaround in core/designsystem/build.gradle.kts. That module cannot
// package its own composeResources because AGP's KMP library plugin has no assets support, so
// it publishes them as a variant and this module, a real com.android.application with working
// assets, merges them in. Without this the app crashes on first frame with
// MissingResourceException.
val composeAndroidAssets =
    configurations.create("composeAndroidAssets") {
        isCanBeConsumed = false
        isCanBeResolved = true
    }

// A real @OutputDirectory DirectoryProperty (not Sync's plain destinationDir) so
// addGeneratedSourceDirectory can reference the property and AGP infers the task dependency.
abstract class CopyComposeResourcesForAssetsTask : DefaultTask() {
    @get:InputFiles
    abstract val inputFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Inject
    abstract val fs: FileSystemOperations

    @TaskAction
    fun copy() {
        fs.sync {
            from(inputFiles)
            into(outputDir)
        }
    }
}

val copyComposeResourcesForAssets =
    tasks.register<CopyComposeResourcesForAssetsTask>("copyComposeResourcesForAssets") {
        inputFiles.from(composeAndroidAssets)
        outputDir.set(layout.buildDirectory.dir("composeResourcesForAppAssets"))
    }

// sourceSets.assets.srcDir() rejects Providers, so register a generated assets dir per variant.
androidComponents {
    onVariants(selector().all()) { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(
            copyComposeResourcesForAssets,
            CopyComposeResourcesForAssetsTask::outputDir,
        )
    }
}

dependencies {
    implementation(project(":cmp-shared"))
    implementation(project(":feature:game"))
    implementation(project(":core:designsystem"))
    // Pulls core:designsystem's prepared compose-resources into composeAndroidAssets above.
    add(
        "composeAndroidAssets",
        project(mapOf("path" to ":core:designsystem", "configuration" to "composeAndroidAssetsElements")),
    )
    implementation("com.siddharth.kmp:feedback:1.0.0")
    implementation(project(":core:prefs"))
    implementation(project(":game-services"))

    implementation(libs.activity.compose)
    implementation(libs.core.ktx)

    testImplementation(libs.junit)

    // Non-free, gms-flavor only — F-Droid's noGms flavor ships without these (PlayFeatures.kt
    // and KursiFirebaseMessagingService.kt have a noGms no-op counterpart under src/noGms).
    add("gmsImplementation", platform(libs.firebase.bom))
    add("gmsImplementation", libs.firebase.messaging)
    add("gmsImplementation", libs.play.review.ktx)
    add("gmsImplementation", libs.play.app.update.ktx)
    // Play Games Services lives in the gms flavor only: noGms/F-Droid ships
    // GameServicesFactory.create() = null rather than link a non-free Google SDK.
    add("gmsImplementation", libs.play.services.games)
    add("gmsImplementation", libs.kotlinx.coroutines.play.services)
}
