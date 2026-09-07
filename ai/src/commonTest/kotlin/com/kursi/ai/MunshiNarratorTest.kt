package com.kursi.ai

import com.kursi.engine.GameConfig
import com.kursi.engine.GameEvent
import com.kursi.engine.PlayerId
import com.kursi.engine.initialState
import com.kursi.engine.redact
import com.siddharth.kmp.llmchat.AiChunk
import com.siddharth.kmp.llmchat.AiConfig
import com.siddharth.kmp.llmchat.AiMessage
import com.siddharth.kmp.llmchat.AiProvider
import com.siddharth.kmp.llmchat.AiProviderConfig
import com.siddharth.kmp.result.AiResult
import com.siddharth.kmp.result.Result
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A fake on-device slot that reports itself unavailable — the tier-1 "no real SDK wired" case. */
private class UnavailableProvider : AiProvider {
    override val id = "on_device"
    override val displayName = "fake on-device"

    override suspend fun isAvailable() = false

    override suspend fun complete(
        messages: List<AiMessage>,
        config: AiConfig,
    ): AiResult<String> = Result.Success("")
}

/**
 * A fake on-device slot that IS available and only implements the single-shot [complete] —
 * exercises [AiProvider.completeStream]'s DEFAULT one-chunk replay, i.e. a provider that hasn't
 * (yet) been taught real token streaming still upgrades the headline, just in one piece.
 */
private class FakeAvailableProvider(
    private val response: String,
) : AiProvider {
    override val id = "on_device"
    override val displayName = "fake on-device"

    override suspend fun isAvailable() = true

    override suspend fun complete(
        messages: List<AiMessage>,
        config: AiConfig,
    ): AiResult<String> = Result.Success(response)
}

/** A fake on-device slot with REAL token streaming — emits [tokens] one at a time, [delayMs] apart. */
private class FakeStreamingProvider(
    private val tokens: List<String>,
    private val delayMs: Long = 0L,
) : AiProvider {
    override val id = "on_device"
    override val displayName = "fake streaming on-device"

    /** How many of [tokens] the fake's OWN generation loop actually ran — proves a cancellation reached it. */
    var emitted = 0
        private set

    override suspend fun isAvailable() = true

    override suspend fun complete(
        messages: List<AiMessage>,
        config: AiConfig,
    ): AiResult<String> = Result.Success(tokens.joinToString(""))

    override fun completeStream(
        messages: List<AiMessage>,
        config: AiConfig,
    ): Flow<AiChunk> =
        flow {
            for (token in tokens) {
                if (delayMs > 0) delay(delayMs)
                emitted++
                emit(AiChunk.Token(token))
            }
        }
}

class MunshiNarratorTest {
    private val config = GameConfig.forPlayers(2)
    private val state = initialState(config, seed = 1L)
    private val view = redact(state, PlayerId(0))
    private val events = listOf<GameEvent>(GameEvent.TurnAdvanced(toSeat = 0, turnNumber = 1))

    @Test
    fun narrate_emitsNothing_whenNoOnDeviceAndNoByokKey_selectionFallsBackToTemplated() =
        runTest {
            val munshi = MunshiNarrator(cloudConfig = AiProviderConfig(useOnDevice = true), onDevice = UnavailableProvider())

            assertEquals(emptyList(), munshi.narrate(view, events).toList())
        }

    @Test
    fun narrate_emitsNothing_whenUseOnDeviceFalse_evenIfProviderWouldBeAvailable() =
        runTest {
            // SELECTION POLICY (spec §8.5): on-device is auto-detected only when opted into; BYOK/on-device
            // are never silently used behind the caller's back.
            val munshi = MunshiNarrator(cloudConfig = AiProviderConfig(useOnDevice = false), onDevice = FakeAvailableProvider("hello"))

            assertEquals(emptyList(), munshi.narrate(view, events).toList())
        }

    @Test
    fun narrate_upgradesInPlace_viaDefaultOneChunkReplay_whenProviderOnlyImplementsComplete() =
        runTest {
            val munshi =
                MunshiNarrator(
                    cloudConfig = AiProviderConfig(useOnDevice = true),
                    onDevice = FakeAvailableProvider("  Bahenji stamped the seal.  "),
                )

            assertEquals(listOf("Bahenji stamped the seal."), munshi.narrate(view, events).toList())
        }

    @Test
    fun narrate_streamsEachTokenAsItArrives_accumulatedAndTrimmed() =
        runTest {
            val munshi =
                MunshiNarrator(
                    cloudConfig = AiProviderConfig(useOnDevice = true),
                    onDevice = FakeStreamingProvider(listOf("Bahenji ", "stamped ", "the seal.")),
                )

            val lines = munshi.narrate(view, events).toList()

            assertEquals(
                listOf("Bahenji", "Bahenji stamped", "Bahenji stamped the seal."),
                lines,
            )
        }

    @Test
    fun narrate_emitsNothing_onBlankProviderResponse_neverAnEmptyUpgrade() =
        runTest {
            val munshi = MunshiNarrator(cloudConfig = AiProviderConfig(useOnDevice = true), onDevice = FakeAvailableProvider("   "))

            assertEquals(emptyList(), munshi.narrate(view, events).toList())
        }

    /**
     * MID-GENERATION CANCELLATION (spec §8.6, this lane's own theme): cancelling the collecting
     * coroutine — exactly what [com.kursi.feature.game.GameViewModel.requestNarration] does for a
     * beat that changed before the Munshi finished — must stop the underlying generation itself,
     * not just this call's wait for it. [FakeStreamingProvider.emitted] is the fake's own token
     * loop's counter: if cancellation only cancelled the collector, this fake (an unbounded-looking
     * `for` loop with a `delay` a real backend's generate-loop would have too) would keep running
     * to completion in the background and `emitted` would still reach 5.
     */
    @Test
    fun narrate_cancellingTheCollector_stopsTheGenerationItself() =
        runTest {
            val provider = FakeStreamingProvider(List(5) { i -> "token$i " }, delayMs = 100L)
            val munshi = MunshiNarrator(cloudConfig = AiProviderConfig(useOnDevice = true), onDevice = provider)
            val collected = mutableListOf<String>()

            val job = launch { munshi.narrate(view, events).collect { collected.add(it) } }
            advanceTimeBy(250L) // ~2 tokens' worth of delay, not all 5
            job.cancel()
            advanceUntilIdle()

            assertTrue(provider.emitted < 5, "cancelling the collector must reach the in-flight generation, not just stop reading it")
            assertEquals(provider.emitted, collected.size, "every token the fake DID emit before cancellation should have been collected")
        }
}
