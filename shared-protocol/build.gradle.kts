plugins {
    id("kursi.kmp.pure")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    android {
        namespace = "com.kursi.protocol"
        compileSdk {
            version = release(37) { minorApiLevel = 1 }
        }
        minSdk = 26
        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":engine"))
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
