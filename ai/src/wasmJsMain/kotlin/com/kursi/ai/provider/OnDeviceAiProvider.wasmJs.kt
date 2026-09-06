package com.kursi.ai.provider

import com.siddharth.kmp.llmchat.AiConfig
import com.siddharth.kmp.llmchat.AiMessage
import com.siddharth.kmp.llmchat.AiProvider

// ponytail: NOT migrated to toolkit :ai (consolidation #7) — toolkit's :ai module only targets
// jvm/iosArm64/iosSimulatorArm64/android, no wasmJs, so it can't be a dependency of this source set.
// Stays a local always-unavailable stub. Upgrade path: add a wasmJs target + an
// UnavailableOnDeviceLlm-equivalent to toolkit :ai, then wire this the same way as
// OnDeviceAiProvider.jvm.kt.
actual class OnDeviceAiProvider actual constructor() : AiProvider {
    actual override val id = "on_device"
    actual override val displayName = "On-device AI"

    actual override suspend fun isAvailable() = false

    actual override suspend fun complete(
        messages: List<AiMessage>,
        config: AiConfig,
    ) = ""
}
