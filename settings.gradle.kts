@file:Suppress("UnstableApiUsage")

pluginManagement {
    includeBuild("build-logic")
    // kmp-toolkit's own settings.gradle.kts does includeBuild("../kmp-build-logic") — a relative
    // path that only resolves if this repo also vendors kmp-build-logic as a sibling of
    // external/kmp-toolkit (see includeBuild("external/kmp-toolkit") below) and includes it here
    // too. Gradle then collapses both references into the same included build. Mirrors PaymentsLab.
    includeBuild("external/kmp-build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://maven.pkg.jetbrains.space/public/p/compose/dev") }
    }
}

dependencyResolutionManagement {
    // PREFER_SETTINGS (not FAIL_ON_PROJECT_REPOS): the Kotlin/Wasm Node.js toolchain setup plugin
    // adds a nodejs distribution repo programmatically at project-level. FAIL_ON_PROJECT_REPOS
    // rejects that and breaks :engine:build when wasmJs { nodejs() } is declared.
    // PREFER_SETTINGS still uses settings repos first; project-level repos only used as fallback.
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://maven.pkg.jetbrains.space/public/p/compose/dev") }
        // Node.js distribution for Kotlin/Wasm nodejs() target: resolves org.nodejs:node artifacts.
        ivy {
            name = "Node.js Distributions"
            url = uri("https://nodejs.org/dist")
            patternLayout { artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]") }
            metadataSources { artifact() }
            content { includeModule("org.nodejs", "node") }
        }
        // Yarn distribution for Kotlin/Wasm browser() target: resolves com.yarnpkg:yarn artifacts.
        ivy {
            name = "Yarn Distributions"
            url = uri("https://github.com/yarnpkg/yarn/releases/download")
            patternLayout { artifact("v[revision]/[artifact](-v[revision]).[ext]") }
            metadataSources { artifact() }
            content { includeModule("com.yarnpkg", "yarn") }
        }
        // Binaryen (Wasm optimizer) for Kotlin/Wasm browser() production builds.
        // kotlinWasmBinaryenSetup resolves com.github.webassembly:binaryen:<version>.
        ivy {
            name = "Binaryen Distributions"
            url = uri("https://github.com/WebAssembly/binaryen/releases/download")
            patternLayout { artifact("version_[revision]/[artifact]-version_[revision]-[classifier].[ext]") }
            metadataSources { artifact() }
            content { includeModule("com.github.webassembly", "binaryen") }
        }
    }
}

rootProject.name = "Kursi"

// kmp-toolkit monorepo — vendored as one submodule, wired via composite build so the toolkit
// coordinates resolve to the local checkout instead of remote repos. One included build replaces
// the former per-leaf submodules (kmp-mvi-core, kmp-feedback); natural module paths (:mvi-core,
// :feedback) mean no ":lib" substitution-collision workaround is needed anymore.
includeBuild("external/kmp-toolkit") {
    dependencySubstitution {
        substitute(module("com.siddharth.kmp:mvi-core")).using(project(":mvi-core"))
        substitute(module("com.siddharth.kmp:common")).using(project(":common"))
        substitute(module("com.siddharth.kmp:feedback")).using(project(":feedback"))
        substitute(module("com.siddharth.kmp:bots-policy")).using(project(":bots-policy"))
        substitute(module("com.siddharth.kmp:network")).using(project(":network"))
        // NOTE: toolkit's :ai project path collides in name with Kursi's OWN root :ai module
        // (Kursi's bot-policy/ISMCTS module, see include(":ai") below) — but they live in separate
        // Gradle builds (this includeBuild's project(":ai") resolves inside external/kmp-toolkit
        // only), so there is no path clash. Coordinate-only consume: routes the on-device-LLM arm
        // of Kursi's own AiProvider through the toolkit's real OnDeviceLlm backends.
        substitute(module("com.siddharth.kmp:ai")).using(project(":ai"))
        // Consolidation #10: Kursi's cloud-LLM chat client (AiProvider + Anthropic/OpenAI/Gemini +
        // chain builder) now lives in toolkit :llm-chat. Kursi's own :ai module consumes it; only
        // OnDeviceAiProvider.* (consumes toolkit :ai) stayed behind as Kursi-specific (the other
        // Kursi-specific AiProvider, IsmctsOnlyProvider, was dead code and was deleted).
        substitute(module("com.siddharth.kmp:llm-chat")).using(project(":llm-chat"))
        // AiResult<T>/AiFailure — :llm-chat's own dependency on this is `implementation`, so it
        // doesn't reach a consumer's classpath transitively; any module (like Kursi's own :ai)
        // whose AiProvider implementations expose these types on their public complete()/
        // completeStream() overrides needs this substitution too.
        substitute(module("com.siddharth.kmp:result")).using(project(":result"))
        // AiBotDecisionEngineTest (:ai's commonTest) scripts a fake on-device backend through this
        // instead of a hand-rolled local fake, same double the toolkit's own tests use.
        substitute(module("com.siddharth.kmp:ai-testing")).using(project(":ai-testing"))
        // Adaptive substrate for core:designsystem — see that module's build.gradle.kts.
        substitute(module("com.siddharth.kmp:designsystem")).using(project(":designsystem"))
    }
}

// ── M0: PURE DOMAIN CORE (JVM target only until an Android SDK + apps land in M1/M2) ──
include(":engine")            // PURE deterministic reducer. LEAF — depends on nothing.
include(":ai")                // Bot policies (Easy, Medium). Depends on :engine only.

// ── PROTOCOL (T8: serialization wire types + envelopes + mappers; multi-target) ──────
include(":shared-protocol")   // @Serializable mirrors of PlayerView/Intent; ClientMessage/ServerMessage envelopes. Depends on :engine only.

// ── CORE (T3: first Compose Multiplatform module — JVM/desktop target only at M0) ──
include(":core:designsystem") // CMP theme engine, color tokens, reusable composables (CardFace, CoinPill, etc.)

// ── CORE (T10: multiplatform Ktor WebSocket client — android/ios/jvm/wasmJs) ─────
include(":core:network")      // KursiClient + OnlineGameSession: WS connect, encode/decode, reconnect.
include(":core:prefs")        // Multiplatform key-value preferences (AppPrefs) backed by multiplatform-settings.
// core:feedback extracted to external/kmp-feedback (composite build above) — expect/actual
// SoundPlayer + haptics (jvm tone synth, android AudioTrack/Vibrator, ios AVAudioPlayer, wasm Web Audio).

// ── FEATURES (T4: offline game-driving layer + MVI ViewModel + table composables) ──
include(":feature:game")      // Offline game session, MVI GameViewModel, GameScreen composables.

// ── APP ENTRYPOINTS (T5: shared Compose root + JVM desktop shell) ──────────────
include(":cmp-shared")        // Shared KursiApp() root composable; wraps KursiTheme + GameViewModel + GameScreen.
include(":cmp-desktop")       // JVM Compose Desktop application shell; calls KursiApp().

// ── APP ENTRYPOINTS (T12: Android application shell) ─────────────────────────
include(":cmp-android")       // Android application shell; hosts KursiApp() via ComponentActivity.setContent.

// ── APP ENTRYPOINTS (T13: iOS framework umbrella) ────────────────────────────
include(":cmp-ios")           // iOS umbrella framework; export()s :cmp-shared so an Xcode/SwiftUI app can host KursiKit.framework.

// ── APP ENTRYPOINTS (T11: wasmJs browser client) ─────────────────────────────
include(":cmp-web")           // wasmJs browser shell; calls ComposeViewport(document.body!!) { KursiApp() }.

// ── SERVER (T9: JVM Ktor authoritative game server) ───────────────────────────
include(":server")            // Ktor/Netty WebSocket server; authoritative GameState; Channel-actor per match.

// Headless client for :engine — see cli/build.gradle.kts.
include(":cli")
