package com.kursi.feature.game.narrative

import com.kursi.ai.persona.PersonalityProfile
import com.kursi.ai.persona.TargetingBias
import com.kursi.feature.game.Language
import com.siddharth.kmp.ai.testing.FakeOnDeviceLlm
import com.siddharth.kmp.llmchat.AiConfig
import com.siddharth.kmp.llmchat.AiMessage
import com.siddharth.kmp.llmchat.AiProvider
import com.siddharth.kmp.result.AiFailure
import com.siddharth.kmp.result.AiResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Wraps [FakeOnDeviceLlm] (the toolkit's scriptable on-device double) as an [AiProvider], the same
 *  shape production's `OnDeviceAiProvider` actuals use. */
private class FakeProvider(
    private val llm: FakeOnDeviceLlm,
) : AiProvider {
    override val id = "fake"
    override val displayName = "fake"

    override suspend fun isAvailable(): Boolean = llm.isAvailable()

    override suspend fun complete(
        messages: List<AiMessage>,
        config: AiConfig,
    ): AiResult<String> = llm.generate(messages.joinToString("\n") { it.content })
}

private val REQUEST =
    EmbellishRequest(
        personaId = "bhai_teja",
        personaName = "Bhai Teja",
        personaTitle = "The Enforcer",
        archetype = "silent",
        baseLine = "Table pe baith.",
        tone = MessageTone.NEUTRAL,
        arc = null,
        targetName = null,
        language = Language.HINGLISH,
    )

/** [LlmChatEmbellisher]'s own contract: every failure mode collapses to null, same as [NoopEmbellisher]. */
class ChatEmbellisherTest {
    @Test
    fun embellish_unavailableProvider_returnsNull_neverReadsTheQueuedReply() =
        runTest {
            val llm = FakeOnDeviceLlm(available = false)
            llm.enqueueSuccess("this must never be read")

            val result = LlmChatEmbellisher(FakeProvider(llm)).embellish(REQUEST)

            assertNull(result)
        }

    @Test
    fun embellish_providerFails_returnsNull() =
        runTest {
            val llm = FakeOnDeviceLlm(available = true)
            llm.enqueueFailure(AiFailure.Network)

            val result = LlmChatEmbellisher(FakeProvider(llm)).embellish(REQUEST)

            assertNull(result)
        }

    @Test
    fun embellish_providerAnswersBlank_returnsNull() =
        runTest {
            val llm = FakeOnDeviceLlm(available = true)
            llm.enqueueSuccess("   ")

            val result = LlmChatEmbellisher(FakeProvider(llm)).embellish(REQUEST)

            assertNull(result)
        }

    @Test
    fun embellish_providerAnswers_returnsItTrimmedAndTruncated() =
        runTest {
            val llm = FakeOnDeviceLlm(available = true)
            llm.enqueueSuccess("  " + "x".repeat(200) + "  ")

            val result = LlmChatEmbellisher(FakeProvider(llm), maxChars = 20).embellish(REQUEST)

            assertEquals("x".repeat(20), result)
        }

    @Test
    fun noopEmbellisher_alwaysReturnsNull() =
        runTest {
            assertNull(NoopEmbellisher.embellish(REQUEST))
        }
}

/** [SocialDirector.embellish] — restyling replaces the template body in place, or is a safe no-op. */
class SocialDirectorEmbellishTest {
    private val botProfile =
        PersonalityProfile(
            bluffRate = 0.30f,
            challengeAggression = 0.40f,
            economicAggression = 0.50f,
            targetingBias = TargetingBias.LEADER,
            risk = 0.50f,
            vindictiveness = 0.40f,
            predictability = 0.60f,
        )

    private fun seats() =
        listOf(
            SeatInfo(0, "Aap", personaId = null, profile = null, isHuman = true),
            SeatInfo(1, "Bhai Teja", personaId = "bhai_teja", profile = botProfile, isHuman = false),
        )

    private object FixedEmbellisher : ChatEmbellisher {
        override suspend fun embellish(request: EmbellishRequest): String? = "RESTYLED: ${request.baseLine}"
    }

    @Test
    fun embellish_withRealEmbellisher_replacesTheTemplateBody() =
        runTest {
            val director = SocialDirector(seed = 1L, seats = seats(), humanSeat = 0, embellisher = FixedEmbellisher)
            director.greetTable(turn = 1)
            val original = director.feed().single()

            director.embellish(original.id)

            val updated = director.feed().single()
            assertEquals("RESTYLED: ${original.body}", updated.body)
            assertEquals(original.id, updated.id)
            assertEquals(original.senderSeat, updated.senderSeat)
        }

    @Test
    fun embellish_withDefaultNoopEmbellisher_leavesTheTemplateBodyUnchanged() =
        runTest {
            val director = SocialDirector(seed = 1L, seats = seats(), humanSeat = 0)
            director.greetTable(turn = 1)
            val original = director.feed().single()

            director.embellish(original.id)

            assertEquals(original, director.feed().single())
        }

    @Test
    fun embellish_unknownId_isANoOp() =
        runTest {
            val director = SocialDirector(seed = 1L, seats = seats(), humanSeat = 0, embellisher = FixedEmbellisher)
            director.greetTable(turn = 1)
            val original = director.feed().single()

            director.embellish(id = 999_999L)

            assertEquals(original, director.feed().single())
        }
}
