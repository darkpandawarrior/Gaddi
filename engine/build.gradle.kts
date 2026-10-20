plugins {
    id("kursi.kmp.pure")
    id("shared.purity")
}

kotlin {
    android {
        namespace = "com.kursi.engine"
        compileSdk {
            version = release(37) { minorApiLevel = 1 }
        }
        minSdk = 26
        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            // NOTHING. Pure Kotlin stdlib only — no coroutines, datetime, serialization.
            // Determinism (seed + event log => byte-for-byte state) forbids any platform-dependent dep.
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// Purity tripwire (shared.purity, backlog #20): the engine must stay a dependency-free leaf.
// `checkPurity` (wired into `check`) fails the build if compose/ktor/serialization/coroutines/
// datetime ever creep onto its runtime classpath.
purity {
    forbidden.set(
        listOf(
            "org.jetbrains.compose",
            "androidx.compose",
            "io.ktor",
            "kotlinx-serialization",
            "kotlinx-coroutines",
            "kotlinx-datetime",
        ),
    )
}
