package com.kursi.ai.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import com.siddharth.kmp.ai.CompositeOnDeviceLlm
import com.siddharth.kmp.ai.MediaPipeModelManager
import com.siddharth.kmp.ai.MediaPipeOnDeviceLlm
import com.siddharth.kmp.ai.MlKitGenAiOnDeviceLlm
import com.siddharth.kmp.ai.OnDeviceLlm
import com.siddharth.kmp.llmchat.AiChunk
import com.siddharth.kmp.llmchat.AiConfig
import com.siddharth.kmp.llmchat.AiMessage
import com.siddharth.kmp.llmchat.AiProvider
import com.siddharth.kmp.result.AiResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// ponytail: process-wide Application Context capture via a no-op ContentProvider — the standard
// Android idiom (WorkManager/Firebase/Coil) for library code that needs a Context but must keep a
// zero-arg constructor. `expect class OnDeviceAiProvider()` is shared with jvm/ios/wasmJs actuals, so
// it can't grow a Context parameter just for Android. Registered once in AndroidManifest.xml below;
// android auto-instantiates it (and calls onCreate()) before Application.onCreate() runs.
internal class KursiAiContextProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        context?.applicationContext?.let { appContext = it }
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Nothing? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = null

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    companion object {
        lateinit var appContext: Context
    }
}

/**
 * Android on-device LLM tier (consolidation #7): routes through toolkit `:ai`'s detection-ordered
 * chain — ML Kit GenAI Prompt (Gemini Nano, AICore devices) → MediaPipe LLM Inference (Gemma,
 * downloaded on demand) — instead of a hand-rolled always-unavailable stub.
 */
actual class OnDeviceAiProvider actual constructor() : AiProvider {
    override val id = "on_device"
    override val displayName = "On-device AI (Gemini Nano / Gemma)"

    private val llm: OnDeviceLlm by lazy {
        val context = KursiAiContextProvider.appContext
        CompositeOnDeviceLlm(
            listOf(
                MlKitGenAiOnDeviceLlm(context),
                MediaPipeOnDeviceLlm(context, MediaPipeModelManager(context)),
            ),
        )
    }

    override suspend fun isAvailable(): Boolean = llm.isAvailable()

    override suspend fun complete(
        messages: List<AiMessage>,
        config: AiConfig,
    ): AiResult<String> = llm.generate(messages.toOnDevicePrompt())

    // Real per-token streaming (not the AiProvider default's one-chunk replay of complete()) —
    // ML Kit GenAI / MediaPipe's generateStream is Flow-native and cancellable mid-generation
    // (see CompositeOnDeviceLlm's own kdoc), so cancelling the collector reaches the model call.
    override fun completeStream(
        messages: List<AiMessage>,
        config: AiConfig,
    ): Flow<AiChunk> = llm.generateStream(messages.toOnDevicePrompt()).map { AiChunk.Token(it) }
}
