plugins {
    id("kursi.kmp.pure")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    android {
        namespace = "com.kursi.ai"
        compileSdk = 37
        minSdk = 26
        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":engine"))
            // api, not implementation: SearchBudget/Policy from this module are part of :ai's own
            // public surface (KursiRules.Policy typealias, IsmctsSearch/MoveAdvisor budget params),
            // consumed transitively by :feature:game and :server.
            api("com.siddharth.kmp:bots-policy:1.0.0")
            // Consolidation #10: AiProvider/AiMessage/AiConfig/AiProviderConfig/buildProviderChain +
            // the real Anthropic/OpenAI/Gemini Ktor clients moved to toolkit :llm-chat. Only
            // IsmctsOnlyProvider (implements AiProvider) and OnDeviceAiProvider.* (consumes toolkit
            // :ai) stayed here — neither needs ktor directly anymore, so the ktor-client-* deps this
            // module used to declare for the (now-moved) provider impls are gone too.
            implementation("com.siddharth.kmp:llm-chat:1.0.0")
            // AiResult<String>/AiFailure — every AiProvider.complete()/completeStream() override in
            // this module (TemplatedAiProvider, IsmctsOnlyProvider, OnDeviceAiProvider.*) returns or
            // emits these types directly, so :llm-chat's own (implementation-only) dependency on
            // :result doesn't reach this module's compile classpath transitively; needs its own line.
            implementation("com.siddharth.kmp:result:1.0.0")
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
        }
        jvmMain.dependencies {
            // Consolidation #7: OnDeviceAiProvider.jvm routes through toolkit :ai's UnavailableOnDeviceLlm
            // instead of hand-rolling the same always-false stub.
            implementation("com.siddharth.kmp:ai:1.0.0")
        }
        androidMain.dependencies {
            // Consolidation #7: OnDeviceAiProvider.android routes through toolkit :ai's real
            // MlKitGenAiOnDeviceLlm/MediaPipeOnDeviceLlm backends (CompositeOnDeviceLlm chain).
            //
            // F-Droid build only: drop com.google.mediapipe:tasks-genai (the 25.4MB arm64 +
            // 18.3MB armeabi-v7a libllm_inference_engine_jni.so, ~70% of the FOSS APK). Verified
            // safe to exclude — MediaPipeOnDeviceLlm/MediaPipeModelManager never touch mediapipe
            // types in their constructors or in isAvailable(); generate() is the only call site
            // that does, and it's wrapped in runCatching{}.getOrNull(), so a missing class there
            // degrades to null (composite falls through to MlKitGenAiOnDeviceLlm, then the
            // rule-based heuristic tier) instead of crashing. ML Kit GenAI (AICore) stays intact.
            val fdroidBuild = providers.gradleProperty("fdroid").isPresent
            implementation("com.siddharth.kmp:ai:1.0.0") {
                if (fdroidBuild) {
                    // Both on-device LLM backends come out of the F-Droid build, not just the
                    // MediaPipe one. Excluding tasks-genai alone still left genai-prompt pulling
                    // Play Services in transitively, so the published APK carried 28,909 GMS,
                    // 19,046 ML Kit and 2,228 Firebase class references and requested
                    // com.google.android.apps.aicore.service.BIND_SERVICE, while its own listing
                    // told installers there were no proprietary components. Verify against the
                    // built APK, never against this file.
                    //
                    // Safe: MlKitGenAiOnDeviceLlm holds its GenerativeModel `by lazy`, so nothing
                    // resolves at construction, and every call site sits inside runCatching or
                    // Flow.catch. CompositeOnDeviceLlm falls through to the rule-based tier, which
                    // is what the F-Droid build already relied on for MediaPipe.
                    exclude(group = "com.google.mediapipe", module = "tasks-genai")
                    exclude(group = "com.google.mlkit")
                    exclude(group = "com.google.android.gms")
                }
            }
        }
        iosMain.dependencies {
            // Consolidation #7: OnDeviceAiProvider.ios routes through toolkit :ai's
            // FoundationModelsOnDeviceLlm/MediaPipeOnDeviceLlm (both stubs pending a Swift bridge —
            // same behavior as before, now sourced from the shared toolkit instead of a local dupe).
            implementation("com.siddharth.kmp:ai:1.0.0")
        }
        wasmJsMain.dependencies {
            // ponytail: toolkit :ai has NO wasmJs target (jvm/iosArm64/iosSimulatorArm64/android only)
            // — can't consume it here. OnDeviceAiProvider.wasmJs stays a local stub. Upgrade path: add
            // a wasmJs target to toolkit :ai (UnavailableOnDeviceLlm-equivalent) if that ever changes.
            // (:llm-chat DOES have a wasmJs target now — consolidation #10 — so this source set picks
            // it up fine via the commonMain dependency above; only the on-device arm stays stubbed.)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
