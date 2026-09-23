import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Convention plugin for Gaddi's pure (dependency-free) core modules: :engine and :ai.
 *
 * Applies Kotlin Multiplatform + the AGP KMP-library plugin and declares ALL shared targets:
 *   jvm, iosArm64, iosSimulatorArm64, wasmJs.
 *
 * Each consuming module supplies its own `android { namespace / compileSdk / minSdk }` block,
 * exactly as in the MileTracker reference implementation.
 *
 * Deliberately applies NO Compose, NO Ktor, NO kotlinx-serialization — the engine must stay a leaf.
 */
class GaddiKmpPureConventionPlugin : Plugin<Project> {
    @OptIn(ExperimentalWasmDsl::class)
    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            apply("org.jetbrains.kotlin.multiplatform")
            apply("com.android.kotlin.multiplatform.library")
        }
        extensions.configure<KotlinMultiplatformExtension> {
            jvm()
            iosArm64()
            iosSimulatorArm64()
            wasmJs {
                browser()
                nodejs()
            }
        }

        // JVM test fork heap sizing.
        //
        // :ai:jvmTest runs sim-heavy ISMCTS suites (ExpertPolicyTest plays 200-600 full games,
        // each spinning up many ISMCTS trees). On the default test-fork heap (~512m) the worker JVM
        // OOMs and the Gradle worker dies with an EOFException ("the forked VM terminated without
        // properly saying goodbye"), which presents as a non-deterministic test-infrastructure crash
        // rather than a test failure. Raising the fork heap to 4g (with a 1g initial size to cut GC
        // churn during the long sim runs) lets the full suite run green without weakening any test.
        //
        // Applies to both :engine and :ai (both use this convention plugin); the engine suite is
        // lighter and simply benefits from the headroom.
        tasks.withType(Test::class.java).configureEach {
            maxHeapSize = "4g"
            minHeapSize = "1g"
            jvmArgs("-XX:MaxMetaspaceSize=512m")
        }
    }
}
