plugins {
    id("kursi.kmp.pure")
}

kotlin {
    android {
        namespace = "com.kursi.gameservices"
        compileSdk = 37
        minSdk = 26
    }

    sourceSets {
        commonMain.dependencies {
            // api, not implementation: GameServices.currentPlayer exposes StateFlow on its
            // public signature, so every consumer needs the type on its compile classpath.
            api(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
