package com.kursi.ai.provider

import com.siddharth.kmp.llmchat.AiConfig
import com.siddharth.kmp.llmchat.AiMessage
import com.siddharth.kmp.llmchat.AiProvider
import com.siddharth.kmp.result.AiResult
import com.siddharth.kmp.result.Result

// ponytail: NOT migrated to toolkit :ai (consolidation #7) — toolkit's :ai module only targets
// jvm/iosArm64/iosSimulatorArm64/android, no wasmJs, so it can't be a dependency of this source set.
// Stays a local always-unavailable stub. Upgrade path: add a wasmJs target + an
// UnavailableOnDeviceLlm-equivalent to toolkit :ai, then wire this the same way as
// OnDeviceAiProvider.jvm.kt.
actual class OnDeviceAiProvider actual constructor() : AiProvider {
    override val id = "on_device"
    override val displayName = "On-device AI"

    override suspend fun isAvailable() = false

    override suspend fun complete(
        messages: List<AiMessage>,
        config: AiConfig,
    ): AiResult<String> = Result.Success("")
}
