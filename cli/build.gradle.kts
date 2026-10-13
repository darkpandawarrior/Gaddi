// A headless client for :engine, and the point it proves: the engine is a real SDK, not an
// internal package. The Compose apps (cmp-android / cmp-ios / cmp-desktop / cmp-web) are one
// renderer over it; this terminal client is another, written against exactly the same public API
// with no UI framework anywhere.
//
// Plain JVM on purpose. :engine already targets jvm, android, iosArm64, iosSimulatorArm64 and
// wasmJs, so anything that can host Kotlin can host it; this module only needs one of them to
// demonstrate that.
plugins {
    kotlin("jvm")
    application
}

kotlin { jvmToolchain(25) } // must match :engine (class file 69)

dependencies {
    implementation(project(":engine"))
}

application {
    mainClass.set("com.kursi.cli.MainKt")
}
