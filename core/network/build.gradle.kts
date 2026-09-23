plugins {
    id("kursi.kmp.pure")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    android {
        namespace = "com.kursi.core.network"
        compileSdk = 37
        minSdk = 26
        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":engine"))
            implementation(project(":shared-protocol"))
            // api (not implementation): LanHost/LanAdvertiser/LanDiscoverer moved into :network, so
            // downstream modules (:feature:game, :cmp-shared, :cmp-desktop) need them transitively.
            api("com.siddharth.kmp:network:1.0.0")
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.websockets)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kotlinx.coroutines.core)
        }

        // Per-platform engine deps (okhttp/darwin/js) now come transitively via :network's
        // httpClientEngine() — jvm moves OkHttp -> CIO via :network's jvmMain (both support
        // WebSockets, so GaddiClient/RemoteStanding are unaffected).

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
