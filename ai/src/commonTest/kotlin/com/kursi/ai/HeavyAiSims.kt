package com.kursi.ai

/**
 * Platform gate for the heavy ISMCTS simulation suites (Expert/Grandmaster strength regressions,
 * full Expert policy probes, 2v2 team-awareness sims).
 *
 * These tests each run hundreds of full self-play games, every one spinning up many ISMCTS trees.
 * That is fine on the JVM (where [GaddiKmpPureConventionPlugin] gives the test fork a 4g heap and
 * the real strength regression actually runs) and on native, but it is hostile to the Kotlin/Wasm
 * **browser** test runner: each long synchronous computation starves Karma's 2000 ms ping, the
 * browser is declared disconnected ("reconnect failed before timeout of 2000ms (ping timeout)"),
 * and the whole `:ai:wasmJsBrowserTest` task fails — and, because the runner never reports a single
 * test, Gradle additionally complains it "did not discover any tests to execute".
 *
 * Rather than mask the timeout (e.g. failOnNoDiscoveredTests=false) or weaken any threshold, we gate
 * the heavy sims off the wasm target via expect/actual:
 *   - jvm / native / android  -> true  (full strength coverage runs)
 *   - wasmJs                  -> false (heavy sims early-return as a no-op skip)
 *
 * The lighter functional AI tests (policy smoke, bluff odds, card choice, persona neutrality, etc.)
 * remain in commonTest and DO run on wasm, so the wasm target still discovers and executes tests.
 */
expect fun heavyAiSimsEnabled(): Boolean
