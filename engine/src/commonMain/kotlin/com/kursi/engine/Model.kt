package com.kursi.engine

// ─────────────────────────── Roles & actions ───────────────────────────

/**
 * The Gaddi influence cards. Mapping to Coup: NETA=Duke, BHAI=Assassin, BABU=Captain, JUGAADU=Ambassador,
 * VAKIL=Contessa, PATRAKAAR=Inquisitor (the 6th role, Reformation/Inquisitor pattern — see §2 scaling doc).
 *
 * ORDER IS LOAD-BEARING: PATRAKAAR is declared LAST so that [baseRoles] (the first five) is a stable prefix.
 * The 6th role only ENTERS the deck at large tables (N>=[PATRAKAAR_MIN_PLAYERS]); the first five are the
 * classic set used at every table size.
 */
enum class Role { NETA, BHAI, BABU, JUGAADU, VAKIL, PATRAKAAR }

/** The classic five-role set — every table includes these. PATRAKAAR is the only role outside it. */
val baseRoles: List<Role> = listOf(Role.NETA, Role.BHAI, Role.BABU, Role.JUGAADU, Role.VAKIL)

/** A turn action. Cost/target legality lives in [legalActions]; the engine never role-gates a declaration (bluffing is allowed). */
sealed interface Action {
    data object Income : Action // Dehaadi (+1, unchallengeable, unblockable)

    data object ForeignAid : Action // FDI (+2, blockable by Neta)

    data class Coup(
        val target: PlayerId,
    ) : Action // Khela (pay 7, unblockable, unchallengeable)

    data object Tax : Action // Ghotala  (claims Neta) — +3

    data class Assassinate(
        val target: PlayerId,
    ) : Action // Supari (claims Bhai) — pay 3, target loses influence

    data class Steal(
        val target: PlayerId,
    ) : Action // Vasooli (claims Babu) — steal 2

    data object Exchange : Action // Setting (claims Jugaadu) — draw 2, swap

    /**
     * Jaanch (claims PATRAKAAR — the Inquisitor/Journalist). Privately examine ONE of [target]'s face-down
     * cards: the examiner LEARNS that card (secrecy boundary — no one else does), then chooses whether to
     * force the target to shuffle it back into the deck and redraw (an info-then-disrupt action). Not blockable.
     */
    data class Investigate(
        val target: PlayerId,
    ) : Action

    // ── Variant actions (flag-gated; only appear in legalActions when their flag is set) ──────────────────

    /** BAIL PE BAHAR — spend [GameConfig.bailCost] coins to restore the first face-up (revealed) influence
     *  card back to face-down. Unchallengeable, unblockable. Requires [GameConfig.bailEnabled]. */
    data object BailPe : Action

    /** BALI KHEL (Sabotage) — voluntarily sacrifice one face-down influence card (player chooses via
     *  [Phase.AwaitingInfluenceLoss] with reason SABOTAGED), and gain [GameConfig.sabotageGain] coins from
     *  the treasury. Cannot be used when on last influence. Requires [GameConfig.sabotageEnabled]. */
    data object Sabotage : Action

    /** HAWALA — gift up to [GameConfig.hawalaMaxGift] of your coins directly to [to]. Bypasses the treasury.
     *  Unchallengeable, unblockable. Requires [GameConfig.hawalaEnabled]. Creates alliances; game-theory rich. */
    data class Hawala(
        val to: PlayerId,
    ) : Action

    /** ADHYADESH (Emergency) — declare Emergency once [GameConfig.emergencyThreshold] lifetime coins earned.
     *  Pay ALL current coins (min 7), then force every alive opponent to lose one influence (unchallengeable,
     *  unblockable mass-Coup). Requires [GameConfig.emergencyEnabled]. Extremely rare — must be farm-built. */
    data object Emergency : Action
}

/** Static capability tables — the single source of truth for what each action claims and who blocks it. */
object Rules {
    fun claimedRole(a: Action): Role? =
        when (a) {
            Action.Tax -> Role.NETA
            is Action.Assassinate -> Role.BHAI
            is Action.Steal -> Role.BABU
            Action.Exchange -> Role.JUGAADU
            is Action.Investigate -> Role.PATRAKAAR
            // Variant actions carry no role claim — any claim is a provable bluff.
            Action.BailPe, Action.Sabotage, is Action.Hawala, Action.Emergency -> null
            else -> null
        }

    fun isChallengeable(a: Action): Boolean = claimedRole(a) != null

    fun rolesThatBlock(a: Action): Set<Role> =
        when (a) {
            Action.ForeignAid -> setOf(Role.NETA)
            is Action.Steal -> setOf(Role.BABU, Role.JUGAADU)
            is Action.Assassinate -> setOf(Role.VAKIL)
            // Variant actions are unblockable — they bypass all reaction windows.
            Action.BailPe, Action.Sabotage, is Action.Hawala, Action.Emergency -> emptySet()
            else -> emptySet()
        }

    fun isBlockable(a: Action): Boolean = rolesThatBlock(a).isNotEmpty()

    fun targetOf(a: Action): PlayerId? =
        when (a) {
            is Action.Coup -> a.target
            is Action.Assassinate -> a.target
            is Action.Steal -> a.target
            is Action.Investigate -> a.target
            is Action.Hawala -> a.to
            else -> null
        }
}

// ─────────────────────────── Identifiers ───────────────────────────
// PlayerId and CardId are declared as expect/actual in PlayerIds.kt (JVM/Android need @JvmInline).

/** Where a card physically is. Ownership + face state are POSITION, never fields on the card. */
sealed interface CardLocation {
    data class Hand(
        val player: PlayerId,
        val faceUp: Boolean,
    ) : CardLocation

    data object Deck : CardLocation

    data class ExchangeHeld(
        val player: PlayerId,
    ) : CardLocation // transiently held during a Setting/Exchange
}

// ─────────────────────────── Config ───────────────────────────

data class GameConfig(
    val seatCount: Int,
    val copiesPerRole: Int,
    /**
     * The DISTINCT roles present in this game's deck — the deck is a uniform [copiesPerRole]-per-role
     * multiset over exactly these roles (equiprobability constraint, §1 of the scaling doc). Small tables
     * use [baseRoles] (5); large tables (N >= [PATRAKAAR_MIN_PLAYERS]) add [Role.PATRAKAAR] as the 6th.
     * Never assume all of [Role.entries] is present — iterate [activeRoles] instead.
     */
    val activeRoles: List<Role> = baseRoles,
    val influencePerPlayer: Int = 2,
    val startingCoins: Int = 2,
    val coupCost: Int = 7,
    val assassinateCost: Int = 3,
    val taxAmount: Int = 3,
    val foreignAidAmount: Int = 2,
    val stealAmount: Int = 2,
    val incomeAmount: Int = 1,
    val exchangeDrawCount: Int = 2,
    val forcedCoupThreshold: Int = 10,
    val coinSupply: Int = GameConfig.COIN_SUPPLY_BASE + GameConfig.COIN_SUPPLY_PER_SEAT * seatCount,
    /**
     * TEAMS variant (ADDITIVE + flag-gated). `null` = free-for-all (the default, classic behavior — the
     * win condition is "last player standing"). When non-null, this maps EVERY seat index (0 until
     * [seatCount]) to a team id; the win condition becomes "last TEAM standing" (a player keeps acting
     * while any teammate is alive). Team ids are opaque ints; only their partition matters. The map MUST
     * be total over seats and describe at least two distinct teams. Free-for-all behavior is byte-for-byte
     * unchanged when [teams] is null — no engine path consults teams unless this is set.
     */
    val teams: Map<Int, Int>? = null,
    /**
     * ANARCHY variant ("Andher Nagari") — ADDITIVE + flag-gated. `false` = classic fixed economy. When
     * true, the cost to seize the chair (Khela / Coup) FALLS by 1 each full round once the early game is
     * past (see [effectiveCoupCost]), down to [coupCostFloor] — a self-escalating, bloodier endgame.
     * No other path consults it; free-for-all + teams are byte-for-byte unchanged when this is false.
     */
    val anarchy: Boolean = false,
    /** ANARCHY: the floor the falling Khela cost never drops below. */
    val coupCostFloor: Int = 3,
    /**
     * Variant flags (all false = classic behavior, byte-for-byte unchanged).
     * BAIL PE BAHAR — allow spending [bailCost] coins to restore one face-up influence to face-down.
     */
    val bailEnabled: Boolean = false,
    val bailCost: Int = 9,
    /** BALI KHEL (Sabotage) — allow voluntarily flipping a face-down card up to gain [sabotageGain] coins.
     *  Cannot be used on last influence — suicide is not legal. */
    val sabotageEnabled: Boolean = false,
    val sabotageGain: Int = 3,
    /** HAWALA — allow gifting up to [hawalaMaxGift] coins directly to any alive opponent. */
    val hawalaEnabled: Boolean = false,
    val hawalaMaxGift: Int = 5,
    /** ADHYADESH (Emergency) — when a player's lifetime coin total reaches [emergencyThreshold], they may
     *  declare Emergency: pay all current coins (min 7) to force all alive opponents to lose one influence.
     *  Unchallengeable, unblockable. Extremely rare — requires deliberate farming without spending. */
    val emergencyEnabled: Boolean = false,
    val emergencyThreshold: Int = 25,
    /** KHAZANA RAJ — alternate win condition: first player to accumulate [khazanaTarget] lifetime coins wins.
     *  Elimination continues (a player with no influence can no longer earn coins, so they are effectively
     *  out), but games run longer and favor economic strategies over pure aggression. */
    val khazanaEnabled: Boolean = false,
    val khazanaTarget: Int = 25,
    /** MEHENGAI (Inflation) — all coin COSTS increase by 1 every [inflationInterval] turns, representing
     *  runaway corruption. Income and gifts are unaffected. Cannot combine with ANARCHY (conflicting direction). */
    val inflationEnabled: Boolean = false,
    val inflationInterval: Int = 4,
    /** TANGI (Scarcity) — the total coin pool is capped at [scarcitySupply] + startingCoins × seatCount
     *  instead of the default 50+10×N. Every coin matters; hoarding and coin-denial strategies dominate. */
    val scarcityEnabled: Boolean = false,
    val scarcitySupply: Int = 20,
) {
    val roleCount: Int get() = activeRoles.size
    val deckSize: Int get() = roleCount * copiesPerRole

    /** True when this game is the TEAMS variant (last-team-standing). False = classic free-for-all. */
    val isTeamGame: Boolean get() = teams != null

    /** TANGI (Scarcity) — effective coin pool, capped when scarcityEnabled. Classic = default coinSupply. */
    val effectiveCoinSupply: Int get() =
        if (scarcityEnabled) scarcitySupply + startingCoins * seatCount else coinSupply

    /**
     * The Khela (Coup) cost in effect on [turnNumber]. Classic = constant [coupCost].
     * ANARCHY: cost falls 1 per full round after a grace period, never below [coupCostFloor].
     * MEHENGAI (Inflation): cost rises 1 every [inflationInterval] turns. Mutually exclusive with ANARCHY.
     */
    fun effectiveCoupCost(turnNumber: Int): Int =
        when {
            anarchy -> {
                val grace = seatCount * 2
                val roundsPast = maxOf(0, turnNumber - grace) / seatCount
                (coupCost - roundsPast).coerceAtLeast(coupCostFloor)
            }
            inflationEnabled -> coupCost + (turnNumber / inflationInterval)
            else -> coupCost
        }

    /** Assassinate cost in effect on [turnNumber]. Rises under MEHENGAI (Inflation). */
    fun effectiveAssassinateCost(turnNumber: Int): Int =
        if (inflationEnabled) assassinateCost + (turnNumber / inflationInterval) else assassinateCost

    /** Team id of [seat], or null in free-for-all. Caller-checked against [isTeamGame] for team logic. */
    fun teamOfSeat(seat: Int): Int? = teams?.get(seat)

    /** The distinct team ids present, in ascending order. Empty in free-for-all. */
    val teamIds: List<Int> get() = teams?.values?.distinct()?.sorted() ?: emptyList()

    /** §1 court-deck buffer floor: Setting draws 2 + a same-turn challenge redraw, scaled mildly with table size. */
    val bufferFloor: Int get() = maxOf(3, (seatCount + 2) / 3)

    init {
        require(seatCount in MIN_SEATS..MAX_SEATS) { "MVP supports $MIN_SEATS..$MAX_SEATS players, was $seatCount" }
        require(copiesPerRole in MIN_COPIES_PER_ROLE..MAX_COPIES_PER_ROLE) {
            "copiesPerRole must be $MIN_COPIES_PER_ROLE..$MAX_COPIES_PER_ROLE, was $copiesPerRole"
        }
        require(activeRoles.isNotEmpty() && activeRoles.toSet().size == activeRoles.size) { "activeRoles must be distinct non-empty" }
        // DRAFT variant relaxes the "all five base roles" rule: any DISTINCT subset of >= MIN_ACTIVE_ROLES
        // roles is a legal deck (a claim for an absent role is simply a guaranteed bluff). The no-starvation
        // buffer check below still guards deck viability, so an unplayable draft is rejected at construction.
        require(activeRoles.size >= MIN_ACTIVE_ROLES) { "a deck needs at least $MIN_ACTIVE_ROLES distinct roles, was ${activeRoles.size}" }
        // No-starvation: the court deck left after dealing must cover the Setting/redraw buffer.
        require(deckSize - seatCount * influencePerPlayer >= bufferFloor) {
            "deck ($deckSize) must leave a court buffer of $bufferFloor after dealing ${seatCount * influencePerPlayer}"
        }
        require(coinSupply >= startingCoins * seatCount) { "coin supply too small" }
        // TEAMS validation (only when the flag is set — free-for-all skips this entirely).
        teams?.let { t ->
            require(t.keys == (0 until seatCount).toSet()) { "teams must map every seat 0 until $seatCount exactly, was ${t.keys}" }
            require(t.values.toSet().size >= 2) { "teams must describe at least 2 distinct teams, was ${t.values.toSet()}" }
        }
    }

    companion object {
        /** Big tables cap copies so they are not "challenge-dead" (too many of each role alive → challenges -EV). §2(a). */
        const val MAX_COPIES_PER_ROLE = 5

        /** Table-size bounds. `canonical` covers the small band, `bigTable` the large one. */
        const val MIN_SEATS = 2
        const val MAX_SEATS = 10
        const val MAX_CANONICAL_SEATS = 6
        const val MIN_BIG_TABLE_SEATS = 7

        /** A deck needs at least this many copies of each role to be playable at all. */
        const val MIN_COPIES_PER_ROLE = 3

        /** Treasury: a flat float plus a per-seat allowance, so coins never starve a big table. */
        const val COIN_SUPPLY_BASE = 50
        const val COIN_SUPPLY_PER_SEAT = 10

        /** The 6th role (PATRAKAAR) ENTERS the deck at this seat count. §1/§2: add a role rather than push copies past 5. */
        const val PATRAKAAR_MIN_PLAYERS = 9

        /** DRAFT variant: the fewest distinct roles a hand-picked deck may contain. */
        const val MIN_ACTIVE_ROLES = 4

        /**
         * DRAFT variant ("Nilaami") — build a config for a hand-picked role set, recomputing the uniform
         * copies/role for the chosen deck exactly as [forPlayers] does. Validation (distinctness, the
         * no-starvation buffer) runs in `init`; an unviable draft throws, so callers fall back to [forPlayers].
         */
        fun drafted(
            n: Int,
            roles: List<Role>,
        ): GameConfig {
            val r = roles.distinct()
            val copies = (n * 2 + RESERVE_HINT + (r.size - 1)) / r.size
            return GameConfig(
                seatCount = n,
                copiesPerRole = copies.coerceIn(MIN_COPIES_PER_ROLE, MAX_COPIES_PER_ROLE),
                activeRoles = r,
            )
        }

        /**
         * Deck-scaling ladder (single source of truth = ScalingGoldenTest). Levers, in order:
         *   1. uniform copies/role, copies = clamp(ceil((2N+RESERVE_HINT)/roleCount), 3..MAX_COPIES_PER_ROLE)
         *   2. a 6th role (PATRAKAAR) once N >= PATRAKAAR_MIN_PLAYERS, keeping copies <= 5 so big tables stay live.
         * Roles stay equiprobable (uniform copies across all activeRoles).
         */
        fun forPlayers(n: Int): GameConfig {
            val roles = if (n >= PATRAKAAR_MIN_PLAYERS) baseRoles + Role.PATRAKAAR else baseRoles
            val copies = (n * 2 + RESERVE_HINT + (roles.size - 1)) / roles.size // ceil((2N+hint)/roles)
            return GameConfig(
                seatCount = n,
                copiesPerRole = copies.coerceIn(MIN_COPIES_PER_ROLE, MAX_COPIES_PER_ROLE),
                activeRoles = roles,
            )
        }

        /** Tuning hint that biased the un-capped copy count toward the 0.38–0.41 bluff prior; now clamped by MAX_COPIES_PER_ROLE. */
        private const val RESERVE_HINT = 15

        fun canonical(n: Int): GameConfig {
            require(n in MIN_SEATS..MAX_CANONICAL_SEATS)
            return forPlayers(n)
        }

        fun bigTable(n: Int): GameConfig {
            require(n in MIN_BIG_TABLE_SEATS..MAX_SEATS)
            return forPlayers(n)
        }
    }
}

// ─────────────────────────── Reaction / phase model ───────────────────────────

enum class ReactionStep { CHALLENGE_ACTION, BLOCK, CHALLENGE_BLOCK }

sealed interface Reaction {
    data object Pass : Reaction

    data object Challenge : Reaction

    data class Block(
        val role: Role,
    ) : Reaction
}

data class PendingAction(
    val actor: PlayerId,
    val action: Action,
    val claimedRole: Role?,
)

data class BlockClaim(
    val blocker: PlayerId,
    val role: Role,
)

data class ReactionCtx(
    val pending: PendingAction,
    val step: ReactionStep,
    val eligible: List<PlayerId>, // seat-ordered starting from actor's left
    val responded: Map<PlayerId, Reaction> = emptyMap(),
    val block: BlockClaim? = null,
)

enum class LossReason {
    LOST_CHALLENGE,
    LOST_BLOCK_CHALLENGE,
    ASSASSINATED,
    COUPED,

    /** BALI KHEL — voluntary sacrifice in exchange for [GameConfig.sabotageGain] coins. */
    SABOTAGED,

    /** ADHYADESH — forced loss from an Emergency declaration (mass-Coup). */
    EMERGENCY_COUPED,
}

/** Continuation for after a forced influence loss resolves — keeps `applyIntent` flat instead of nesting phases. */
sealed interface AfterLoss {
    /** The turn ends; [actor] is the player whose turn it was (used to advance to the next seat). */
    data class EndTurn(
        val actor: PlayerId,
    ) : AfterLoss

    /**
     * A proven action-challenge: the challenger lost their card FIRST (spec A.5 step 3 order). Now the
     * prover ([pending.actor]) reveal-replaces the [provenCard] (shuffle back + draw), after which the
     * surviving action may be blocked or resolved.
     */
    data class AfterActionSurvived(
        val pending: PendingAction,
        val provenCard: CardId,
    ) : AfterLoss

    /**
     * A proven block-challenge: the challenger lost their card FIRST (spec A.5 step 3 order). Now the
     * [blocker] reveal-replaces the proven [provenCard], the action is negated, and the turn ends.
     */
    data class BlockProven(
        val pending: PendingAction,
        val blocker: PlayerId,
        val provenCard: CardId,
    ) : AfterLoss

    /** A failed block-challenge (blocker bluffed): the block dies and the action effect resolves. */
    data class ResolveEffect(
        val pending: PendingAction,
    ) : AfterLoss

    /**
     * ADHYADESH (Emergency) mass-Coup chain: after the current target loses influence, force the next
     * player in [remaining] to lose one too. Skips already-eliminated players. Turn ends when empty.
     */
    data class EmergencyNext(
        val actor: PlayerId,
        val remaining: List<PlayerId>,
    ) : AfterLoss
}

sealed interface Phase {
    data class AwaitingAction(
        val actorSeat: Int,
    ) : Phase

    data class AwaitingReactions(
        val ctx: ReactionCtx,
    ) : Phase

    data class AwaitingInfluenceLoss(
        val loser: PlayerId,
        val reason: LossReason,
        val after: AfterLoss,
    ) : Phase

    data class AwaitingExchange(
        val actor: PlayerId,
        val drawn: List<CardId>,
    ) : Phase

    /**
     * Jaanch resolved truthfully: [examiner] has privately examined [peeked] (one of [target]'s face-down
     * cards) and now decides whether to force [target] to shuffle it back and redraw.
     * SECRECY: [peeked]'s identity is surfaced ONLY to [examiner] in [redact]; no other viewer ever learns it.
     */
    data class AwaitingInvestigatePeek(
        val examiner: PlayerId,
        val target: PlayerId,
        val peeked: CardId,
    ) : Phase

    data class GameOver(
        val winner: PlayerId,
    ) : Phase
}

// ─────────────────────────── Intents & events ───────────────────────────

sealed interface Intent {
    val actor: PlayerId

    data class DeclareAction(
        override val actor: PlayerId,
        val action: Action,
    ) : Intent

    data class Challenge(
        override val actor: PlayerId,
    ) : Intent

    data class Block(
        override val actor: PlayerId,
        val role: Role,
    ) : Intent

    data class Pass(
        override val actor: PlayerId,
    ) : Intent

    data class ChooseInfluenceToLose(
        override val actor: PlayerId,
        val card: CardId,
    ) : Intent

    data class ChooseExchange(
        override val actor: PlayerId,
        val keep: List<CardId>,
    ) : Intent

    /** Jaanch follow-up: after privately examining the target's card, the examiner decides whether to force a redraw. */
    data class ResolveInvestigate(
        override val actor: PlayerId,
        val forceRedraw: Boolean,
    ) : Intent
}

/** Descriptive, append-only facts for UI / history / golden snapshots. Not (yet) the authoritative reduce input. */
sealed interface GameEvent {
    data class ActionDeclared(
        val actor: PlayerId,
        val action: Action,
        val claimedRole: Role?,
    ) : GameEvent

    data class Challenged(
        val challenger: PlayerId,
        val target: PlayerId,
        val claimedRole: Role,
    ) : GameEvent

    data class ChallengeRevealed(
        val player: PlayerId,
        val card: CardId,
        val role: Role,
        val hadRole: Boolean,
    ) : GameEvent

    data class CardReplaced(
        val player: PlayerId,
        val returned: CardId,
        val drawn: CardId,
    ) : GameEvent

    data class Blocked(
        val blocker: PlayerId,
        val role: Role,
        val action: Action,
    ) : GameEvent

    data class ActionResolved(
        val actor: PlayerId,
        val action: Action,
    ) : GameEvent

    data class ActionNegated(
        val actor: PlayerId,
        val action: Action,
    ) : GameEvent

    data class CoinsChanged(
        val player: PlayerId,
        val delta: Int,
    ) : GameEvent

    data class CoinsTransferred(
        val from: PlayerId,
        val to: PlayerId,
        val amount: Int,
    ) : GameEvent

    data class InfluenceLost(
        val player: PlayerId,
        val card: CardId,
        val role: Role,
        val reason: LossReason,
    ) : GameEvent

    data class PlayerEliminated(
        val player: PlayerId,
    ) : GameEvent

    data class Exchanged(
        val actor: PlayerId,
        val kept: List<CardId>,
        val returned: List<CardId>,
    ) : GameEvent

    /**
     * Jaanch resolved: [examiner] privately examined one of [target]'s cards. SECRECY: this public fact
     * deliberately carries NO role/CardId — only the examiner learns the card (via the peek phase / redact).
     */
    data class Investigated(
        val examiner: PlayerId,
        val target: PlayerId,
    ) : GameEvent

    /** The examiner forced [target] to shuffle the examined card back and redraw. No role is leaked publicly. */
    data class InvestigateRedraw(
        val target: PlayerId,
    ) : GameEvent

    data class TurnAdvanced(
        val toSeat: Int,
        val turnNumber: Int,
    ) : GameEvent

    data class GameEnded(
        val winner: PlayerId,
    ) : GameEvent

    // ── Variant events ───────────────────────────────────────────────────────────────────────────────────

    /** BAIL PE BAHAR — [player]'s [card] ([role]) was restored from face-up back to face-down. */
    data class InfluenceRestored(
        val player: PlayerId,
        val card: CardId,
        val role: Role,
    ) : GameEvent

    /** HAWALA — [from] gifted [amount] coins directly to [to] (bypassing treasury). */
    data class CoinsGifted(
        val from: PlayerId,
        val to: PlayerId,
        val amount: Int,
    ) : GameEvent

    /** ADHYADESH — [actor] declared Emergency (paid all coins, mass-Coup begins). */
    data class EmergencyDeclared(
        val actor: PlayerId,
    ) : GameEvent

    /** KHAZANA RAJ win — [winner] reached [lifetimeCoins] total coins earned, winning by coin milestone. */
    data class KhazanaWon(
        val winner: PlayerId,
        val lifetimeCoins: Int,
    ) : GameEvent

    /** KHAZANA RAJ milestone — [player] crossed Darja [level] (1=Mukhiya,2=Sahib,3=Mantri,4=Sarkar). */
    data class DarjaReached(
        val player: PlayerId,
        val level: Int,
        val lifetimeCoins: Int,
    ) : GameEvent
}

// ─────────────────────────── Player & game state ───────────────────────────

data class PlayerState(
    val id: PlayerId,
    val seatIndex: Int,
    val coins: Int,
)

data class GameState(
    val config: GameConfig,
    val cards: Map<CardId, Role>, // immutable role assignment
    val locations: Map<CardId, CardLocation>, // THE source of truth for hands/deck/exchange
    val players: List<PlayerState>, // indexed by seat
    val treasury: Int,
    val phase: Phase,
    val rng: RngState,
    val turnNumber: Int,
    /** Cumulative coins earned per player (never decremented by spending). Tracks KhazanaRaj progress
     *  and Emergency unlock. Empty map in classic mode — only populated when khazanaEnabled or emergencyEnabled. */
    val lifetimeCoins: Map<PlayerId, Int> = emptyMap(),
) {
    fun rngEngine(): Rng = rngFrom(rng)

    fun player(id: PlayerId): PlayerState = players.first { it.id == id }

    fun seatOf(id: PlayerId): Int = player(id).seatIndex

    fun playerAtSeat(seat: Int): PlayerState = players.first { it.seatIndex == seat }

    fun faceDownInfluence(id: PlayerId): List<CardId> =
        locations.entries
            .filter {
                val l = it.value
                l is CardLocation.Hand && l.player == id && !l.faceUp
            }.map { it.key }
            .sortedBy { it.raw }

    fun faceUpCards(id: PlayerId): List<CardId> =
        locations.entries
            .filter {
                val l = it.value
                l is CardLocation.Hand && l.player == id && l.faceUp
            }.map { it.key }
            .sortedBy { it.raw }

    fun isAlive(id: PlayerId): Boolean = faceDownInfluence(id).isNotEmpty()

    fun influenceCount(id: PlayerId): Int = faceDownInfluence(id).size

    fun alivePlayers(): List<PlayerState> = players.filter { isAlive(it.id) }

    /** Team id of [id], or null in free-for-all. */
    fun teamOf(id: PlayerId): Int? = config.teamOfSeat(seatOf(id))

    /**
     * Other players on [id]'s team (excluding [id] itself). EMPTY in free-for-all (where every player is
     * their own faction). Used by team-aware bots to avoid targeting allies; the engine itself never
     * role-gates a teammate-targeted action (a human is free to defect — only the win condition is team-aware).
     */
    fun teammatesOf(id: PlayerId): List<PlayerId> {
        val team = teamOf(id) ?: return emptyList()
        return players.filter { it.id != id && config.teamOfSeat(it.seatIndex) == team }.map { it.id }
    }

    /** Distinct team ids with at least one surviving (alive) member. Free-for-all returns empty. */
    fun aliveTeams(): Set<Int> =
        if (!config.isTeamGame) {
            emptySet()
        } else {
            alivePlayers().mapNotNull { config.teamOfSeat(it.seatIndex) }.toSet()
        }

    val deckCards: List<CardId> get() =
        locations.entries
            .filter { it.value == CardLocation.Deck }
            .map { it.key }
            .sortedBy { it.raw }

    /**
     * Canonical clockwise-from-seat ordering: alive players in clockwise seat order, starting
     * immediately to the left of [fromSeat]. The wrap-around lands [fromSeat]'s own occupant LAST.
     * [exclude] removes specific players (by id) from the result — pass the seat's own occupant to
     * get the strictly-exclusive "everyone but me, clockwise" order; pass a blocker to keep the
     * seat occupant (e.g. the actor challenging a block) while dropping the blocker.
     *
     * This is the single seat-ordering primitive used by every reaction-window eligibility query.
     */
    fun aliveFromLeftOf(
        fromSeat: Int,
        exclude: Set<PlayerId> = emptySet(),
    ): List<PlayerId> {
        val n = config.seatCount
        return (1..n)
            .map { playerAtSeat((fromSeat + it) % n) }
            .filter { isAlive(it.id) && it.id !in exclude }
            .map { it.id }
    }
}

// ─────────────────────────── Apply outcome ───────────────────────────

sealed interface ApplyOutcome {
    data class Rejected(
        val reason: String,
    ) : ApplyOutcome

    data class Accepted(
        val state: GameState,
        val events: List<GameEvent>,
    ) : ApplyOutcome
}
