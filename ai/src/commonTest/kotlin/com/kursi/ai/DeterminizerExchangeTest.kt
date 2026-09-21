package com.kursi.ai

import com.kursi.engine.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Determinizer must populate the reconstructed [Phase.AwaitingExchange.drawn] from the sampled
 * deck pool so ISMCTS can enumerate keep-sets and value an Exchange — previously it was always empty,
 * which made Exchange unevaluable in search.
 */
class DeterminizerExchangeTest {
    private val cfg = GameConfig.forPlayers(2)
    private val me = PlayerId(0)
    private val opp = PlayerId(1)

    private fun exchangeView(drawn: List<Role>): PlayerView {
        val ownFaceDown = listOf(Role.NETA, Role.JUGAADU)
        val myCards =
            listOf(
                OwnCard(CardId(0), ownFaceDown[0], faceUp = false),
                OwnCard(CardId(1), ownFaceDown[1], faceUp = false),
            )
        // Drawn cards carry CardIds that are irrelevant to the determinizer (it assigns its own).
        val drawnCards = drawn.mapIndexed { i, role -> OwnCard(CardId(100 + i), role, faceUp = false) }
        // Keep coins conserved (treasury + all player coins == coinSupply) so checkInvariants holds.
        val myCoins = 2
        val oppCoins = 2
        return PlayerView(
            viewer = me,
            config = cfg,
            treasury = cfg.coinSupply - myCoins - oppCoins,
            deckCount = cfg.deckSize - 4 - 2, // own 2 + opp 2 + drawn 2 (rough; not load-bearing for the test)
            turnNumber = 6,
            myCoins = myCoins,
            myInfluence = ownFaceDown.sortedBy { it.ordinal },
            myFaceUp = emptyList(),
            myCards = myCards,
            players =
                listOf(
                    OpponentView(me, 0, 2, emptyList(), 2, false),
                    OpponentView(opp, 1, 2, emptyList(), 2, false),
                ),
            phase = PhaseView.Exchange(me, drawnCards),
        )
    }

    @Test
    fun determinizer_populatesDrawn_withKnownRoles() {
        val det = Determinizer(BeliefModel())
        val view = exchangeView(listOf(Role.BABU, Role.BHAI))
        val (state, _) = det.sample(view, BotMemory(), Rng(42L))

        val ph = state.phase
        assertTrue(ph is Phase.AwaitingExchange, "phase should be AwaitingExchange")
        assertEquals(me, ph.actor)
        assertEquals(2, ph.drawn.size, "drawn must be populated, not empty")

        // The drawn CardIds must resolve (via the sampled cards map) to exactly the known drawn roles,
        // and must be physically located as ExchangeHeld for the actor.
        val drawnRoles = ph.drawn.map { state.cards.getValue(it) }.sorted()
        assertEquals(listOf(Role.BABU, Role.BHAI).sorted(), drawnRoles)
        for (c in ph.drawn) {
            assertEquals(
                CardLocation.ExchangeHeld(me),
                state.locations.getValue(c),
                "drawn card $c must be ExchangeHeld by the actor",
            )
        }
    }

    @Test
    fun determinizer_exchangeState_isLegalForEngine() {
        // The reconstructed state must satisfy the engine invariants and produce legal exchange intents
        // whose keep-sets the engine accepts — proving the determinization is engine-valid.
        val det = Determinizer(BeliefModel())
        val view = exchangeView(listOf(Role.VAKIL, Role.NETA))
        val (state, _) = det.sample(view, BotMemory(), Rng(7L))

        checkInvariants(state) // card + coin conservation must hold with the drawn cards accounted for

        val legal = legalIntents(state, me)
        assertTrue(legal.isNotEmpty(), "actor should have legal exchange intents")
        assertTrue(legal.all { it is Intent.ChooseExchange }, "all intents should be ChooseExchange")

        // Apply the role-optimal keep through the real engine — it must be accepted (not rejected).
        val redacted = redact(state, me)
        val best = CardChoice.bestExchange(redacted, legal)!!
        val outcome = applyIntent(state, best)
        assertTrue(
            outcome is ApplyOutcome.Accepted,
            "engine must accept the determinized exchange keep-set; got $outcome",
        )
    }

    private fun List<Role>.sorted() = this.sortedBy { it.ordinal }
}
