package com.kursi.ai.advisor

import com.kursi.ai.*
import com.kursi.engine.*
import com.siddharth.kmp.botspolicy.SearchBudget
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.TimeSource

/**
 * Unit tests for [MoveAdvisor].
 *
 * Covers:
 *   1. bestMove returns a legal intent
 *   2. advise covers ALL legal intents, ranked, recommended==true for exactly one
 *   3. Truthful detection: holds role → truthful=true, bluff=false
 *   4. Bluff detection: does not hold role → bluff=true, truthful=false
 *   5. Challenge successOdds tracks BluffOdds (higher when claim is less supported)
 *   6. Runs within budget — no crash, on action and reaction states
 */
class MoveAdvisorTest {
    // ─────────────────────────────────────────────────────────────────────────
    // Local test-state builder (mirrors engine/commonTest's buildState)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Builds a fully-controlled [GameState]: each seat gets the exact face-down [hands] roles,
     * the rest of the deck is filled deterministically, card-conservation (I1) holds by construction.
     * Mirrors engine/commonTest/TestSupport.kt which is not accessible from :ai tests.
     */
    private fun buildState(
        config: GameConfig,
        hands: List<List<Role>>,
        coins: List<Int>,
        phase: Phase = Phase.AwaitingAction(0),
        turnNumber: Int = 1,
    ): GameState {
        require(hands.size == config.seatCount && coins.size == config.seatCount)
        val cards = LinkedHashMap<CardId, Role>()
        var id = 0
        for (role in Role.entries) repeat(config.copiesPerRole) { cards[CardId(id++)] = role }
        val byRole: Map<Role, MutableList<CardId>> =
            Role.entries.associateWith { r ->
                cards
                    .filter { it.value == r }
                    .keys
                    .sortedBy { it.raw }
                    .toMutableList()
            }

        val locations = LinkedHashMap<CardId, CardLocation>()
        val players = ArrayList<PlayerState>()
        hands.forEachIndexed { seat, hand ->
            val pid = PlayerId(seat)
            for (role in hand) {
                val c = byRole.getValue(role).removeAt(0)
                locations[c] = CardLocation.Hand(pid, faceUp = false)
            }
            players.add(PlayerState(pid, seat, coins[seat]))
        }
        for (i in 0 until config.deckSize) {
            if (!locations.containsKey(CardId(i))) locations[CardId(i)] = CardLocation.Deck
        }

        val treasury = config.coinSupply - coins.sum()
        return GameState(config, cards, locations, players, treasury, phase, RngState(seed = 1L, step = 0L), turnNumber)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Build a 2-player game state with controlled hands. Seat 0 = human. */
    private fun twoPlayerState(
        humanHand: List<Role>,
        botHand: List<Role>,
        humanCoins: Int = 2,
        botCoins: Int = 2,
        phase: Phase = Phase.AwaitingAction(0),
    ): GameState {
        val cfg = GameConfig.forPlayers(2)
        return buildState(cfg, listOf(humanHand, botHand), listOf(humanCoins, botCoins), phase)
    }

    /**
     * Build a state whose initial phase is AwaitingAction(1) (bot's turn), then advance one
     * DeclareAction so we land in the reaction window where the human must respond.
     * NOTE: For challengeable actions (Tax, Steal, Assassinate, Exchange), the phase will be
     * CHALLENGE_ACTION (Challenge/Pass), not BLOCK. For ForeignAid it goes directly to BLOCK.
     */
    private fun reactionState(
        humanHand: List<Role>,
        botHand: List<Role>,
        botAction: Action,
        humanCoins: Int = 2,
        botCoins: Int = 2,
    ): GameState {
        // Bot (seat 1) is active, so build the state with seat 1 to act first
        val cfg = GameConfig.forPlayers(2)
        val initial =
            buildState(
                cfg,
                listOf(humanHand, botHand),
                listOf(humanCoins, botCoins),
                phase = Phase.AwaitingAction(1), // bot's turn
            )
        // Apply bot's chosen action to enter the reaction phase
        val outcome = applyIntent(initial, Intent.DeclareAction(PlayerId(1), botAction))
        return (outcome as ApplyOutcome.Accepted).state
    }

    /**
     * Build a state where the human is in the BLOCK reaction step.
     * The bot (seat 1) has already declared [botAction] and the CHALLENGE_ACTION window has passed
     * (all passed). The human is now in the BLOCK window.
     *
     * This only makes sense for actions that are blockable (ForeignAid, Steal, Assassinate).
     * For Assassinate: we advance through the challenge step by having the human Pass.
     */
    private fun blockReactionState(
        humanHand: List<Role>,
        botHand: List<Role>,
        botAction: Action,
        humanCoins: Int = 2,
        botCoins: Int = 3,
    ): GameState {
        val cfg = GameConfig.forPlayers(2)
        val initial =
            buildState(
                cfg,
                listOf(humanHand, botHand),
                listOf(humanCoins, botCoins),
                phase = Phase.AwaitingAction(1),
            )
        // Bot declares action
        val afterDeclaration = (applyIntent(initial, Intent.DeclareAction(botId, botAction)) as ApplyOutcome.Accepted).state

        // If we're in CHALLENGE_ACTION, the human must respond (Pass to get to BLOCK)
        return if (afterDeclaration.phase is Phase.AwaitingReactions &&
            (afterDeclaration.phase as Phase.AwaitingReactions).ctx.step == ReactionStep.CHALLENGE_ACTION
        ) {
            // Human passes the challenge to proceed to BLOCK step
            val passOutcome = applyIntent(afterDeclaration, Intent.Pass(humanId))
            (passOutcome as ApplyOutcome.Accepted).state
        } else {
            // Already at BLOCK step (e.g. ForeignAid) or some other phase
            afterDeclaration
        }
    }

    private val humanId = PlayerId(0)
    private val botId = PlayerId(1)

    // Fast budget for tests
    private val testBudget = SearchBudget(maxMillis = 2_000L, maxIterations = 500, rolloutHorizon = 6)

    // ─────────────────────────────────────────────────────────────────────────
    // 1. bestMove returns a legal intent
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun bestMove_returnsLegalIntent_actionPhase() =
        runTest {
            val state = twoPlayerState(listOf(Role.NETA, Role.VAKIL), listOf(Role.BHAI, Role.BABU))
            val legal = legalIntents(state, humanId)
            assertTrue(legal.isNotEmpty(), "should have legal intents on action turn")

            val advisor = MoveAdvisor(seed = 42L, adviceBudget = testBudget)
            val best = advisor.bestMove(state, humanId, legal)

            assertTrue(best in legal, "bestMove must return a move from the legal list")
        }

    @Test
    fun bestMove_returnsLegalIntent_reactionChallengePhase() =
        runTest {
            // Bot declares Tax (claims NETA), human can Challenge or Pass
            val state =
                reactionState(
                    humanHand = listOf(Role.VAKIL, Role.BABU),
                    botHand = listOf(Role.BHAI, Role.JUGAADU), // bot does NOT hold NETA → bluff
                    botAction = Action.Tax,
                )
            val legal = legalIntents(state, humanId)
            assertTrue(legal.isNotEmpty(), "human should have reactions to bot's Tax")

            val advisor = MoveAdvisor(seed = 99L, adviceBudget = testBudget)
            val best = advisor.bestMove(state, humanId, legal)
            assertTrue(best in legal, "bestMove must be in the legal list")
        }

    @Test
    fun bestMove_singleLegal_returnsThatIntent() =
        runTest {
            val state = twoPlayerState(listOf(Role.NETA, Role.VAKIL), listOf(Role.BHAI, Role.BABU))
            val legal = legalIntents(state, humanId)
            val single = listOf(legal.first())

            val advisor = MoveAdvisor(seed = 1L, adviceBudget = testBudget)
            val best = advisor.bestMove(state, humanId, single)
            assertEquals(single.single(), best)
        }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. advise covers all legal intents; recommended==true for exactly one
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun advise_coversAllLegalIntents_actionPhase() =
        runTest {
            val state = twoPlayerState(listOf(Role.NETA, Role.BABU), listOf(Role.BHAI, Role.VAKIL))
            val legal = legalIntents(state, humanId)
            assertTrue(legal.isNotEmpty())

            val advisor = MoveAdvisor(seed = 7L, adviceBudget = testBudget)
            val advices = advisor.advise(state, humanId, legal)

            assertEquals(legal.size, advices.size, "advise must return one entry per legal intent")

            // Every legal intent must appear exactly once
            val coveredIntents = advices.map { it.intent }.toSet()
            for (l in legal) {
                assertTrue(l in coveredIntents, "legal intent $l missing from advise output")
            }

            // Exactly one recommended
            val recommendedCount = advices.count { it.recommended }
            assertEquals(1, recommendedCount, "exactly one advice entry must be recommended")
        }

    @Test
    fun advise_coversAllLegalIntents_reactionPhase() =
        runTest {
            // Bot declares Steal targeting human
            val state =
                reactionState(
                    humanHand = listOf(Role.BABU, Role.VAKIL),
                    botHand = listOf(Role.BABU, Role.NETA),
                    botAction = Action.Steal(humanId),
                    botCoins = 2,
                )
            val legal = legalIntents(state, humanId)
            assertTrue(legal.isNotEmpty(), "human should have reactions to Steal")

            val advisor = MoveAdvisor(seed = 13L, adviceBudget = testBudget)
            val advices = advisor.advise(state, humanId, legal)

            assertEquals(legal.size, advices.size)
            assertEquals(1, advices.count { it.recommended }, "exactly one recommended")

            val coveredIntents = advices.map { it.intent }.toSet()
            for (l in legal) assertTrue(l in coveredIntents, "legal intent $l missing")
        }

    @Test
    fun advise_ranked_bestFirstByWinProb() =
        runTest {
            val state = twoPlayerState(listOf(Role.NETA, Role.BHAI), listOf(Role.VAKIL, Role.BABU))
            val legal = legalIntents(state, humanId)
            val advisor = MoveAdvisor(seed = 55L, adviceBudget = testBudget)
            val advices = advisor.advise(state, humanId, legal)

            // List must be sorted descending by winProb (equal ties allowed)
            for (i in 0 until advices.size - 1) {
                assertTrue(
                    advices[i].winProb >= advices[i + 1].winProb,
                    "advices not ranked best-first at index $i: ${advices[i].winProb} < ${advices[i + 1].winProb}",
                )
            }
        }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Truthful detection
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun truthful_taxAction_whenHoldingNeta() =
        runTest {
            // Human holds NETA → Tax is truthful
            val state = twoPlayerState(listOf(Role.NETA, Role.VAKIL), listOf(Role.BHAI, Role.BABU))
            val legal = legalIntents(state, humanId)
            val taxIntent =
                legal
                    .filterIsInstance<Intent.DeclareAction>()
                    .firstOrNull { it.action == Action.Tax }
            assertNotNull(taxIntent, "Tax must be a legal option")

            val advisor = MoveAdvisor(seed = 77L, adviceBudget = testBudget)
            val advices = advisor.advise(state, humanId, legal)
            val taxAdvice = advices.first { it.intent == taxIntent }

            assertEquals(true, taxAdvice.truthful, "Tax with NETA in hand must be truthful")
            assertEquals(false, taxAdvice.bluff, "Tax with NETA in hand must not be a bluff")
        }

    @Test
    fun truthful_block_whenHoldingBlockingRole() =
        runTest {
            // Human holds VAKIL; bot assassinates → human can block truthfully as VAKIL.
            // Use blockReactionState to advance past the CHALLENGE_ACTION window to the BLOCK step.
            val state =
                blockReactionState(
                    humanHand = listOf(Role.VAKIL, Role.NETA),
                    botHand = listOf(Role.BHAI, Role.BABU),
                    botAction = Action.Assassinate(humanId),
                    botCoins = 3,
                )
            val legal = legalIntents(state, humanId)
            val blockVakil =
                legal
                    .filterIsInstance<Intent.Block>()
                    .firstOrNull { it.role == Role.VAKIL }
            assertNotNull(blockVakil, "Block(VAKIL) must be legal at BLOCK step when bot assassinates")

            val advisor = MoveAdvisor(seed = 88L, adviceBudget = testBudget)
            val advices = advisor.advise(state, humanId, legal)
            val blockAdvice = advices.first { it.intent == blockVakil }

            assertEquals(true, blockAdvice.truthful, "Block(VAKIL) while holding VAKIL must be truthful")
            assertEquals(false, blockAdvice.bluff, "Block(VAKIL) while holding VAKIL must not be a bluff")
        }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. Bluff detection
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun bluff_taxAction_whenNotHoldingNeta() =
        runTest {
            // Human does NOT hold NETA → Tax is a bluff
            val state = twoPlayerState(listOf(Role.BHAI, Role.VAKIL), listOf(Role.NETA, Role.BABU))
            val legal = legalIntents(state, humanId)
            val taxIntent =
                legal
                    .filterIsInstance<Intent.DeclareAction>()
                    .firstOrNull { it.action == Action.Tax }
            assertNotNull(taxIntent, "Tax must be legal (bluffing allowed)")

            val advisor = MoveAdvisor(seed = 101L, adviceBudget = testBudget)
            val advices = advisor.advise(state, humanId, legal)
            val taxAdvice = advices.first { it.intent == taxIntent }

            assertEquals(false, taxAdvice.truthful, "Tax without NETA must not be truthful")
            assertEquals(true, taxAdvice.bluff, "Tax without NETA must be flagged as bluff")
        }

    @Test
    fun bluff_block_whenNotHoldingBlockingRole() =
        runTest {
            // Human does NOT hold VAKIL but can bluff-block an assassination.
            // Use blockReactionState to advance past the CHALLENGE_ACTION window to the BLOCK step.
            val state =
                blockReactionState(
                    humanHand = listOf(Role.NETA, Role.BABU), // no VAKIL
                    botHand = listOf(Role.BHAI, Role.JUGAADU),
                    botAction = Action.Assassinate(humanId),
                    botCoins = 3,
                )
            val legal = legalIntents(state, humanId)
            val blockVakil =
                legal
                    .filterIsInstance<Intent.Block>()
                    .firstOrNull { it.role == Role.VAKIL }
            assertNotNull(blockVakil, "Block(VAKIL) must be legal at BLOCK step even without holding VAKIL")

            val advisor = MoveAdvisor(seed = 202L, adviceBudget = testBudget)
            val advices = advisor.advise(state, humanId, legal)
            val blockAdvice = advices.first { it.intent == blockVakil }

            assertEquals(false, blockAdvice.truthful, "Block(VAKIL) without VAKIL must not be truthful")
            assertEquals(true, blockAdvice.bluff, "Block(VAKIL) without VAKIL must be a bluff")
        }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. Challenge successOdds tracks BluffOdds
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun challengeOdds_higher_whenClaimLessSupported() =
        runTest {
            // Scenario A: bot claims Tax (NETA); many NEτΑ copies unseen (more support → lower pBluff)
            // Scenario B: bot claims Tax (NETA); fewer NETA copies unseen (less support → higher pBluff)
            //
            // We compare the Challenge successOdds in two game states:
            //   - stateA: human holds 1 NETA (fewer unseen copies of NETA → bot more likely bluffing)
            //   - stateB: human holds 0 NETA (more unseen copies of NETA → bot less likely bluffing)
            //
            // Holding 1 NETA means 1 fewer unseen NETA in the pool, making the bot's claim harder to justify.
            // So stateA challenge odds > stateB challenge odds.

            val stateA =
                reactionState(
                    humanHand = listOf(Role.NETA, Role.VAKIL), // human holds NETA → fewer unseen NETA
                    botHand = listOf(Role.BHAI, Role.BABU), // bot claims NETA via Tax (bluffing)
                    botAction = Action.Tax,
                )
            val stateB =
                reactionState(
                    humanHand = listOf(Role.VAKIL, Role.BABU), // human holds no NETA → more unseen NETA
                    botHand = listOf(Role.BHAI, Role.JUGAADU), // bot claims NETA via Tax (bluffing)
                    botAction = Action.Tax,
                )

            val advisor = MoveAdvisor(seed = 303L, adviceBudget = testBudget)

            val legalA = legalIntents(stateA, humanId)
            val advicesA = advisor.advise(stateA, humanId, legalA)
            val challengeA = advicesA.firstOrNull { it.intent is Intent.Challenge }
            assertNotNull(challengeA, "Challenge must be legal in stateA")
            assertNotNull(challengeA.successOdds, "Challenge in stateA must have successOdds")

            val legalB = legalIntents(stateB, humanId)
            val advicesB = advisor.advise(stateB, humanId, legalB)
            val challengeB = advicesB.firstOrNull { it.intent is Intent.Challenge }
            assertNotNull(challengeB, "Challenge must be legal in stateB")
            assertNotNull(challengeB.successOdds, "Challenge in stateB must have successOdds")

            // stateA: human holds NETA → fewer unseen → bot's claim less credible → higher bluff odds
            assertTrue(
                challengeA.successOdds!! >= challengeB.successOdds!!,
                "Challenge odds should be >= when fewer unseen NETA exist: " +
                    "stateA=${challengeA.successOdds} vs stateB=${challengeB.successOdds}",
            )
        }

    @Test
    fun challengeOdds_inRange_0_to_1() =
        runTest {
            val state =
                reactionState(
                    humanHand = listOf(Role.VAKIL, Role.BABU),
                    botHand = listOf(Role.BHAI, Role.JUGAADU),
                    botAction = Action.Tax,
                )
            val legal = legalIntents(state, humanId)
            val advisor = MoveAdvisor(seed = 404L, adviceBudget = testBudget)
            val advices = advisor.advise(state, humanId, legal)
            val challenge = advices.firstOrNull { it.intent is Intent.Challenge }
            assertNotNull(challenge)
            val odds = challenge.successOdds
            assertNotNull(odds)
            assertTrue(odds in 0.0..1.0, "successOdds must be in [0, 1], got $odds")
        }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. Runs within budget — no crash, on action and reaction states
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun advise_completesWithinBudget_actionPhase() =
        runTest {
            val budget = SearchBudget(maxMillis = 600L, maxIterations = 400, rolloutHorizon = 6)
            val state = twoPlayerState(listOf(Role.NETA, Role.BHAI), listOf(Role.VAKIL, Role.BABU))
            val legal = legalIntents(state, humanId)

            val advisor = MoveAdvisor(seed = 500L, adviceBudget = budget)
            val mark = TimeSource.Monotonic.markNow()
            val advices = advisor.advise(state, humanId, legal)
            val elapsed = mark.elapsedNow().inWholeMilliseconds

            // Should never throw; should finish well within 2x budget
            assertTrue(
                elapsed < budget.maxMillis * 2 + 500L,
                "advise took ${elapsed}ms which seems over budget ${budget.maxMillis}ms",
            )
            assertEquals(legal.size, advices.size)
        }

    @Test
    fun advise_completesWithinBudget_reactionPhase() =
        runTest {
            val budget = SearchBudget(maxMillis = 600L, maxIterations = 400, rolloutHorizon = 6)
            val state =
                reactionState(
                    humanHand = listOf(Role.VAKIL, Role.NETA),
                    botHand = listOf(Role.BHAI, Role.JUGAADU),
                    botAction = Action.Assassinate(humanId),
                    botCoins = 3,
                )
            val legal = legalIntents(state, humanId)
            assertTrue(legal.isNotEmpty())

            val advisor = MoveAdvisor(seed = 600L, adviceBudget = budget)
            val mark = TimeSource.Monotonic.markNow()
            val advices = advisor.advise(state, humanId, legal)
            val elapsed = mark.elapsedNow().inWholeMilliseconds

            assertTrue(elapsed < budget.maxMillis * 2 + 500L)
            assertEquals(legal.size, advices.size)
            assertEquals(1, advices.count { it.recommended })
        }

    @Test
    fun advise_noMoves_reclaimedRole_null_forNonClaimMoves() =
        runTest {
            // Income and ForeignAid should have truthful==null (no role claim)
            val state = twoPlayerState(listOf(Role.BABU, Role.VAKIL), listOf(Role.NETA, Role.BHAI))
            val legal = legalIntents(state, humanId)
            val advisor = MoveAdvisor(seed = 700L, adviceBudget = testBudget)
            val advices = advisor.advise(state, humanId, legal)

            val incomeAdvice =
                advices.firstOrNull {
                    it.intent is Intent.DeclareAction && (it.intent as Intent.DeclareAction).action == Action.Income
                }
            assertNotNull(incomeAdvice, "Income must be in advices")
            assertNull(incomeAdvice.truthful, "Income makes no role claim — truthful must be null")
            assertFalse(incomeAdvice.bluff, "Income is not a bluff")

            val faAdvice =
                advices.firstOrNull {
                    it.intent is Intent.DeclareAction && (it.intent as Intent.DeclareAction).action == Action.ForeignAid
                }
            assertNotNull(faAdvice, "ForeignAid must be in advices")
            assertNull(faAdvice.truthful, "ForeignAid makes no role claim — truthful must be null")
            assertFalse(faAdvice.bluff, "ForeignAid is not a bluff")
        }

    @Test
    fun advise_multipleGames_noExceptions() =
        runTest {
            // Run advise on action states for 2-player and 3-player games without crashing
            val budget = SearchBudget(maxMillis = 500L, maxIterations = 200, rolloutHorizon = 5)
            for (n in 2..3) {
                val cfg = GameConfig.forPlayers(n)
                val state = initialState(cfg, seed = n.toLong() * 37L)
                val who = whoActsNext(state)!!
                val legal = legalIntents(state, who)
                val view = redact(state, who)
                val advisor = MoveAdvisor(seed = n.toLong(), adviceBudget = budget)
                val advices = advisor.advise(state, who, legal)

                assertEquals(legal.size, advices.size, "n=$n: wrong number of advices")
                assertEquals(1, advices.count { it.recommended }, "n=$n: not exactly one recommended")
            }
        }

    @Test
    fun advise_bluffSuccessOdds_inRange() =
        runTest {
            // When human bluffs Tax (doesn't hold NETA), successOdds should be in [0, 1]
            val state = twoPlayerState(listOf(Role.BHAI, Role.VAKIL), listOf(Role.NETA, Role.BABU))
            val legal = legalIntents(state, humanId)
            val advisor = MoveAdvisor(seed = 800L, adviceBudget = testBudget)
            val advices = advisor.advise(state, humanId, legal)

            val taxAdvice =
                advices.firstOrNull {
                    it.intent is Intent.DeclareAction && (it.intent as Intent.DeclareAction).action == Action.Tax
                }
            assertNotNull(taxAdvice)
            assertTrue(taxAdvice.bluff, "Tax without NETA is a bluff")
            val odds = taxAdvice.successOdds
            assertNotNull(odds, "Bluff Tax should have successOdds")
            assertTrue(odds in 0.0..1.0, "successOdds must be in [0,1], got $odds")
        }
}
