package com.kursi.ai.social

/**
 * One seat's *directed* feeling toward another seat. All fields are plain scalars so the whole
 * social fabric is cheap, value-typed and (re)constructible from a replayed event+chat stream — it
 * is never serialized; resume rebuilds it deterministically (see `SocialDirector.reset`).
 *
 * @property trust      −1 (hostile) … +1 (allied). Drives whether a bot will gang up WITH or AGAINST.
 * @property fear       0 … 1. How dangerous the observer thinks the target is.
 * @property suspicion  0 … 1. The observer's read that the target is bluffing / scheming.
 */
data class SocialStance(
    val trust: Float = 0f,
    val fear: Float = 0f,
    val suspicion: Float = 0f,
) {
    fun adjust(
        trust: Float = 0f,
        fear: Float = 0f,
        suspicion: Float = 0f,
    ): SocialStance =
        SocialStance(
            trust = (this.trust + trust).coerceIn(-1f, 1f),
            fear = (this.fear + fear).coerceIn(0f, 1f),
            suspicion = (this.suspicion + suspicion).coerceIn(0f, 1f),
        )

    fun decay(factor: Float): SocialStance =
        SocialStance(
            trust = trust * factor,
            fear = fear * factor,
            suspicion = suspicion * factor,
        )

    val isAllied: Boolean get() = trust >= ALLY_TRUST
    val isHostile: Boolean get() = trust <= -ALLY_TRUST

    companion object {
        const val ALLY_TRUST = 0.45f
    }
}

/**
 * The whole table's social state — the emergent "who's plotting against whom" fabric that sits
 * ENTIRELY outside the deterministic engine (the engine never sees it). It biases bot *targeting*
 * among already-legal moves and feeds the chat/narrative UI. Immutable; every mutation returns a
 * fresh copy so the director can keep a clean history if it ever needs one.
 *
 * Seats are raw ints (PlayerId.raw) so map keys stay value-clean.
 *
 * @property stances    directed (observer → target) feelings.
 * @property threat     table-wide "this seat needs to go" pressure per seat — the conspiracy field.
 *                      Whoever carries the most threat is who the table wants to gang up on.
 * @property alliances  seat → its current pact partner (symmetric; null = unaligned). At most one each.
 * @property agitation  seat → flaw "heat" `[0,1]`: how primed the bot is to act on its flaw (blunder).
 */
data class SocialState(
    val stances: Map<Long, SocialStance> = emptyMap(),
    val threat: Map<Int, Float> = emptyMap(),
    val alliances: Map<Int, Int> = emptyMap(),
    val agitation: Map<Int, Float> = emptyMap(),
) {
    // ── reads ─────────────────────────────────────────────────────────────────

    fun stance(
        observer: Int,
        target: Int,
    ): SocialStance = stances[key(observer, target)] ?: SocialStance()

    fun threatOf(seat: Int): Float = threat[seat] ?: 0f

    fun agitationOf(seat: Int): Float = agitation[seat] ?: 0f

    fun allyOf(seat: Int): Int? = alliances[seat]

    fun areAllied(
        a: Int,
        b: Int,
    ): Boolean = alliances[a] == b && alliances[b] == a

    /** The seat the table most wants gone among [candidates], or null if no one stands out. */
    fun topThreat(
        candidates: Collection<Int>,
        minimum: Float = 0.15f,
    ): Int? = candidates.maxByOrNull { threatOf(it) }?.takeIf { threatOf(it) >= minimum }

    // ── pure mutations ──────────────────────────────────────────────────────────

    fun withStance(
        observer: Int,
        target: Int,
        transform: (SocialStance) -> SocialStance,
    ): SocialState {
        if (observer == target) return this
        val k = key(observer, target)
        return copy(stances = stances + (k to transform(stance(observer, target))))
    }

    fun withThreat(
        seat: Int,
        delta: Float,
    ): SocialState = copy(threat = threat + (seat to (threatOf(seat) + delta).coerceIn(0f, THREAT_CAP)))

    fun withAgitation(
        seat: Int,
        delta: Float,
    ): SocialState = copy(agitation = agitation + (seat to (agitationOf(seat) + delta).coerceIn(0f, 1f)))

    /** Forge a (symmetric) pact between [a] and [b], dropping any prior partners on either side. */
    fun withAlliance(
        a: Int,
        b: Int,
    ): SocialState {
        if (a == b) return this
        val cleared = alliances.filterValues { it != a && it != b }.toMutableMap()
        cleared.keys.toList().forEach { if (cleared[it] == a || cleared[it] == b) cleared.remove(it) }
        cleared[a] = b
        cleared[b] = a
        return copy(alliances = cleared)
            .withStance(a, b) { it.adjust(trust = 0.7f) }
            .withStance(b, a) { it.adjust(trust = 0.7f) }
    }

    fun breakAlliance(seat: Int): SocialState {
        val partner = alliances[seat] ?: return this
        return copy(alliances = alliances - seat - partner)
            .withStance(seat, partner) { it.adjust(trust = -1.2f, suspicion = 0.4f) }
            .withStance(partner, seat) { it.adjust(trust = -1.2f, suspicion = 0.4f) }
    }

    /** Remove a seat from the fabric once it is eliminated (keeps the maps from growing stale). */
    fun forget(seat: Int): SocialState =
        copy(
            stances = stances.filterKeys { observerOf(it) != seat && targetOf(it) != seat },
            threat = threat - seat,
            alliances = (alliances - seat).filterValues { it != seat },
            agitation = agitation - seat,
        )

    /**
     * One coherent step for "[aggressor] just hit [victim]". The victim resents + fears + suspects
     * the aggressor more; the rest of the table reads the aggressor as a little more dangerous, so
     * its threat ticks up — this is how an over-aggressive player (or bot) organically draws a
     * conspiracy onto themselves.
     */
    fun afterHit(
        aggressor: Int,
        victim: Int,
        weight: Float = 1f,
    ): SocialState =
        withStance(victim, aggressor) { it.adjust(trust = -0.30f * weight, fear = 0.18f * weight, suspicion = 0.10f * weight) }
            .withThreat(aggressor, HitThreatGain * weight)

    /** Per-turn fade so recent events dominate (mirrors the bot grudge decay cadence). */
    fun decay(
        stanceFactor: Float = 0.92f,
        threatFactor: Float = 0.90f,
        agitationFactor: Float = 0.85f,
    ): SocialState =
        copy(
            stances = stances.mapValues { it.value.decay(stanceFactor) }.filterValues { !it.isNeutral() },
            threat = threat.mapValues { it.value * threatFactor }.filterValues { it > NeutralEpsilon },
            agitation = agitation.mapValues { it.value * agitationFactor }.filterValues { it > NeutralEpsilon },
        )

    companion object {
        const val THREAT_CAP = 3f

        /** Directed seat-pair key layout: observer in the high half, target in the low 16 bits. */
        private const val SeatKeyShift = 16
        private const val SeatKeyMask = 0xFFFFL

        /** Pack a directed (observer,target) seat pair into one Long key (each seat < 2^16). */
        fun key(
            observer: Int,
            target: Int,
        ): Long = (observer.toLong() shl SeatKeyShift) or (target.toLong() and SeatKeyMask)

        private fun observerOf(k: Long): Int = (k shr SeatKeyShift).toInt()

        private fun targetOf(k: Long): Int = (k and SeatKeyMask).toInt()
    }
}

/**
 * Below this magnitude a stance, threat or agitation reading is indistinguishable from "no
 * opinion", and carrying it just grows the maps forever as [SocialState.decay] shrinks values
 * asymptotically toward zero. It is the prune threshold, not a tuning knob.
 */
internal const val NeutralEpsilon = 0.02f

/** How much the whole table's read of an aggressor rises per hit they land, at weight 1. */
private const val HitThreatGain = 0.12f

private fun SocialStance.isNeutral(): Boolean =
    kotlin.math.abs(trust) < NeutralEpsilon && fear < NeutralEpsilon && suspicion < NeutralEpsilon
