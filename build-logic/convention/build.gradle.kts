plugins {
    `kotlin-dsl`
}

group = "com.kursi.buildlogic"

dependencies {
    // implementation (not compileOnly): the convention plugin's apply() references KotlinMultiplatformExtension,
    // so that class must be on the convention plugin's RUNTIME classpath. compileOnly keeps it off the runtime
    // classpath → NoClassDefFoundError when the plugin is applied. The ClassCastException that compileOnly guards
    // against only occurs when the same Gradle plugin is loaded by two classloaders — which can't happen here,
    // since modules apply Kotlin ONLY via this convention plugin (never also in their own plugins {} block).
    implementation(libs.kotlin.gradlePlugin)
    // android-gradlePlugin: compileOnly so the convention plugin can reference AGP DSL types
    // (KotlinMultiplatformAndroidExtension) at compile time. compileOnly avoids classloader
    // conflicts — the Android plugin is already on the classpath via the root-project's pluginManagement.
    compileOnly(libs.android.gradlePlugin)
    // compose-gradlePlugin is compileOnly here: build-logic itself doesn't apply the Compose plugin — it is
    // applied directly by modules (e.g. :core:designsystem) via alias(libs.plugins.composeMultiplatform).
    // We only need it in dependencies so that if we ever write a GaddiKmpComposeConventionPlugin the class
    // is available at compile time. No ClassLoader conflict risk since no module also pulls it transitively.
    compileOnly(libs.compose.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("kmpPure") {
            id = "kursi.kmp.pure"
            implementationClass = "GaddiKmpPureConventionPlugin"
        }
    }
}
