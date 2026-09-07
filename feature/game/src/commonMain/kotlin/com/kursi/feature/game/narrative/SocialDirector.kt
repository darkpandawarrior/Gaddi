package com.kursi.feature.game.narrative

import com.kursi.ai.persona.PersonaRoster
import com.kursi.ai.persona.PersonalityProfile
import com.kursi.ai.social.CharacterFlaw
import com.kursi.ai.social.FlawModel
import com.kursi.ai.social.SocialState
import com.kursi.engine.Action
import com.kursi.engine.GameEvent
import com.kursi.engine.Intent
import com.kursi.engine.LossReason
import com.kursi.engine.PlayerView
import com.kursi.engine.Rng
import com.kursi.engine.Rules
import com.kursi.feature.game.Language

/** Static per-seat identity the director needs: name, persona id/profile, human-or-bot. */
data class SeatInfo(
    val seat: Int,
    val name: String,
    val personaId: String?,
    val profile: PersonalityProfile?,
    val isHuman: Boolean,
)

/**
 * The DARBAR conductor — the brain of the chat / narrative / manipulation layer.
 *
 * Sits ENTIRELY outside the deterministic engine. It (1) evolves a [SocialState] from the public
 * event stream, (2) lets bots speak freely (proactive table-talk + reactions), (3) runs the four
 * player-initiable [StoryArcs], and (4) biases bot *targeting among already-legal intents* so the
 * table can genuinely conspire against the player and flawed bots can be baited into blunders.
 *
 * ## Determinism + resume
 * The director carries its own [Rng], seeded off the game seed, advanced ONLY inside [nudgeBotIntent]
 * and the proactive-chatter path. Because the host [com.kursi.feature.game.session.GameSession] calls
 * [observe] / [nudgeBotIntent] / [onHumanChat] in the exact same order on a fresh run and on a replay
 * (the player's chat inputs are logged just like game intents), the social fabric — and therefore the
 * bot moves it nudges — reconstruct bit-for-bit. [reset] returns the director to its opening state so
 * a replay can re-drive it.
 *
 * Nothing here can leak hidden information: bots only ever speak from PUBLIC facts, and a nudge only
 * ever swaps to another *legal* intent the engine already offered.
 *
 * @param onGrudge bridge to the real bot grudge map (`PersonaPolicy.notifyHit`) — the engine-facing
 *                 teeth of Badla and conspiracy pressure. `(holderSeat, targetSeat, weight)`.
 */
class SocialDirector(
    private val seed: Long,
    seats: List<SeatInfo>,
    private val voice: ChatVoice = ChatVoice(),
    private val humanSeat: Int = 0,
    private val onGrudge: (Int, Int, Int) -> Unit = { _, _, _ -> },
    private val language: Language = Language.HINGLISH,
    /** Optional (online, opted-in) restyling — see [ChatEmbellisher]'s HARD CONTRACT. */
    private val embellisher: ChatEmbellisher = NoopEmbellisher,
) {
    private val info: Map<Int, SeatInfo> = seats.associateBy { it.seat }
    private val opening: SocialState =
        run {
            // Narrative instinct: the table is mildly wary of the lone human from the start, so an
            // aggressive player draws a real conspiracy — which the arcs then let them redistribute.
            var s = SocialState().withThreat(humanSeat, 0.25f)
            s
        }

    private var social: SocialState = opening

    // Two independent streams. [nudgeRng] is advanced ONLY by nudgeBotIntent — in strict bot-step
    // order, identical on a live run and a replay — so the GAME-AFFECTING randomness never drifts no
    // matter when cosmetic chat is interleaved. [rng] drives only cosmetic chatter (no game impact).
    private var nudgeRng: Rng = Rng(seed xor NUDGE_SALT)
    private var rng: Rng = Rng(seed xor SOCIAL_SALT)
    private val arcs: MutableMap<ArcId, ArcState> = mutableMapOf()
    private val chat: MutableList<ChatMessage> = mutableListOf()
    private val log: MutableList<Pair<Int, HumanChatInput>> = mutableListOf()
    private var pendingSuggestions: List<ChatSuggestion> = emptyList()
    private var nextId: Long = 1L
    private var greeted = false

    // Causality trackers (mirror GameSession.routeGrudges) so a hit can be attributed to an aggressor.
    private var lastActor: Int? = null
    private var lastChallenger: Int? = null

    // ── public reads ─────────────────────────────────────────────────────────────

    fun feed(): List<ChatMessage> = chat.toList()

    fun activeArcs(): List<ArcId> = arcs.values.filter { !it.ended }.map { it.arc }

    fun chatLog(): List<Pair<Int, HumanChatInput>> = log.toList()

    fun socialSnapshot(): SocialState = social

    /** True when [seat] is the seat the table currently most wants gone (the live conspiracy target). */
    fun isConspiracyTarget(seat: Int): Boolean = social.threatOf(seat) >= 0.6f && social.threat.maxByOrNull { it.value }?.key == seat

    /**
     * Restyles the already-emitted message [id] through [embellisher], replacing its template body
     * in place on success. Never called from the deterministic [emit] path itself (that stays
     * synchronous so the template line always renders first, per [ChatEmbellisher]'s HARD
     * CONTRACT) — a caller with its own [kotlinx.coroutines.CoroutineScope] (the ViewModel layer)
     * fires this separately, well after the beat, so a slow or offline provider never blocks one.
     * A no-op if [id] fell out of the feed window ([MAX_FEED]), its speaker has no known persona,
     * or [embellisher] declines (null/blank — the template line stands, unchanged).
     */
    suspend fun embellish(id: Long) {
        val message = chat.firstOrNull { it.id == id } ?: return
        val persona = info[message.senderSeat]?.personaId?.let { pid -> PersonaRoster.ALL.firstOrNull { it.id == pid } } ?: return
        val request =
            EmbellishRequest(
                personaId = persona.id,
                personaName = persona.name,
                personaTitle = persona.title,
                archetype = persona.archetype,
                baseLine = message.body,
                tone = message.tone,
                arc = message.arc,
                targetName = message.targetSeat?.let { info[it]?.name },
                language = language,
            )
        val restyled = embellisher.embellish(request)?.takeIf { it.isNotBlank() } ?: return
        val idx = chat.indexOfFirst { it.id == id }
        if (idx >= 0) chat[idx] = chat[idx].copy(body = restyled)
    }

    // ── lifecycle ────────────────────────────────────────────────────────────────

    /** Reset to the opening state so a deterministic replay can re-drive the director. */
    fun reset() {
        social = opening
        nudgeRng = Rng(seed xor NUDGE_SALT)
        rng = Rng(seed xor SOCIAL_SALT)
        arcs.clear()
        chat.clear()
        log.clear()
        pendingSuggestions = emptyList()
        nextId = 1L
        greeted = false
        lastActor = null
        lastChallenger = null
    }

    /** Emit the opening table greetings once, at game start. Safe to call repeatedly. */
    fun greetTable(turn: Int) {
        if (greeted) return
        greeted = true
        // One bot sets the tone — pick deterministically by seed.
        val bots = info.values.filter { !it.isHuman && it.personaId != null }
        if (bots.isEmpty()) return
        val (idx, r) = rng.nextInt(bots.size)
        rng = r
        val b = bots[idx]
        emit(b.seat, voice.greet(b.personaId!!), MessageTone.NEUTRAL, ChatKind.TABLE, turn = turn)
    }

    // ── event observation → social evolution + proactive chatter ──────────────────

    /**
     * Fold a batch of applied [events] into the social fabric and let bots react. Called once per
     * applied intent (bot AND human), live and during replay — so it is fully deterministic.
     */
    fun observe(
        events: List<GameEvent>,
        turn: Int,
    ) {
        for (e in events) {
            when (e) {
                is GameEvent.ActionDeclared -> {
                    lastActor = e.actor.raw
                    maybeTauntOnAttack(e, turn)
                }
                is GameEvent.Challenged -> lastChallenger = e.challenger.raw
                is GameEvent.InfluenceLost -> {
                    val victim = e.player.raw
                    val aggressor =
                        when (e.reason) {
                            LossReason.ASSASSINATED, LossReason.COUPED,
                            LossReason.EMERGENCY_COUPED,
                            -> lastActor
                            LossReason.LOST_CHALLENGE, LossReason.LOST_BLOCK_CHALLENGE -> lastChallenger
                            LossReason.SABOTAGED -> null // voluntary sacrifice — no social credit to an aggressor
                        }
                    if (aggressor != null && aggressor != victim) {
                        social = social.afterHit(aggressor, victim, weight = 1.5f)
                    }
                }
                is GameEvent.CoinsTransferred -> {
                    if (e.from.raw != e.to.raw) social = social.afterHit(e.to.raw, e.from.raw, weight = 0.7f)
                }
                is GameEvent.PlayerEliminated -> {
                    maybeChatterOnElimination(e.player.raw, turn)
                    social = social.forget(e.player.raw)
                    arcs.values
                        .filter { it.target == e.player.raw || it.ally == e.player.raw }
                        .forEach { arcs[it.arc] = it.copy(ended = true) }
                }
                is GameEvent.ChallengeRevealed -> if (e.hadRole) maybeGloat(e.player.raw, turn)
                is GameEvent.TurnAdvanced -> {
                    social = social.decay()
                    lastActor = null
                    lastChallenger = null
                }
                else -> {}
            }
        }
    }

    // ── player chat → arc machinery ───────────────────────────────────────────────

    /**
     * Apply the player's chat action. Logged (with [afterHumanMoves] = the human-move index at send
     * time) so a replay reconstructs the fabric. Returns nothing — read [feed]/[suggestions] after.
     */
    fun onHumanChat(
        input: HumanChatInput,
        afterHumanMoves: Int,
        turn: Int,
        view: PlayerView?,
    ) {
        log.add(afterHumanMoves to input)
        when (input.kind) {
            ChatActionKind.ARC_START -> startArc(input, turn)
            ChatActionKind.ARC_REPLY, ChatActionKind.DEFLECT, ChatActionKind.ALLY_PING -> replyArc(input, turn)
            ChatActionKind.TAUNT -> {
                val t = input.targetSeat ?: return
                emit(humanSeat, voice.arcBeat("afwaah.plant", "Aap", info[t]?.name), MessageTone.HOSTILE, ChatKind.TABLE, t, turn, fromPlayer = true)
                social = social.withThreat(t, 0.2f).withStance(t, humanSeat) { it.adjust(suspicion = 0.15f) }
            }
            ChatActionKind.PLACATE -> {
                val t = input.targetSeat ?: return
                social = social.withStance(t, humanSeat) { it.adjust(trust = 0.3f, suspicion = -0.2f) }.withThreat(humanSeat, -0.15f)
            }
        }
        if (view != null) recomputeSuggestions(view)
    }

    private fun startArc(
        input: HumanChatInput,
        turn: Int,
    ) {
        val arc = input.arc ?: return
        val target = input.targetSeat ?: return
        val targetName = info[target]?.name ?: "unhe"
        val accepted = arc != ArcId.GATHBANDHAN || botAcceptsPact(target)
        val step = StoryArcs.begin(arc, target, targetName, accepted)
        applyStep(step, turn)
        // Badla needs a rival pick — surface "point at <rival>" chips for each living rival.
        if (arc == ArcId.BADLA && step.nextState?.ended == false) {
            pendingSuggestions =
                livingOpponents().filter { it.seat != target }.map { rival ->
                    ChatSuggestion(
                        "badla.point.$target.${rival.seat}",
                        "→ ${rival.name}",
                        ChatActionKind.ARC_REPLY,
                        arc,
                        rival.seat,
                        rival.name,
                        "Sic $targetName on ${rival.name}",
                    )
                }
        }
    }

    private fun replyArc(
        input: HumanChatInput,
        turn: Int,
    ) {
        val arc = input.arc ?: return
        val state = arcs[arc] ?: return
        val rivalName = input.targetSeat?.let { info[it]?.name }
        val step = StoryArcs.reply(state, input, rivalName)
        applyStep(step, turn)
    }

    private fun applyStep(
        step: ArcStep,
        turn: Int,
    ) {
        step.ops.forEach { applyOp(it) }
        step.beats.forEach { beat ->
            val speakerName = if (beat.fromPlayer) "Aap" else info[beat.speakerSeat]?.name ?: ""
            val targetName = beat.targetSeat?.let { info[it]?.name }
            val text =
                when {
                    beat.speakerSeat < 0 -> voice.arcBeat(beat.beatKey, "Sutradhar", targetName)
                    beat.fromPlayer || beat.speakerSeat == humanSeat -> voice.arcBeat(beat.beatKey, speakerName, targetName)
                    else -> {
                        val pid = info[beat.speakerSeat]?.personaId
                        if (pid != null) voice.botArcBeat(beat.beatKey, pid, targetName) else voice.arcBeat(beat.beatKey, speakerName, targetName)
                    }
                }
            val kind = if (beat.speakerSeat < 0) ChatKind.SYSTEM else ChatKind.ARC
            emit(beat.speakerSeat, text, beat.tone, kind, beat.targetSeat, turn, beat.arc, beat.fromPlayer)
        }
        step.nextState?.let { ns -> arcs[ns.arc] = ns }
        if (step.suggestions.isNotEmpty()) pendingSuggestions = step.suggestions
    }

    private fun applyOp(op: SocialOp) {
        when (op) {
            is SocialOp.Ally -> social = social.withAlliance(op.a, op.b)
            is SocialOp.Betray -> social = social.breakAlliance(op.seat)
            is SocialOp.Threat -> social = social.withThreat(op.seat, op.delta)
            is SocialOp.Suspicion ->
                if (op.observer == SocialOp.ALL) {
                    info.keys.filter { it != op.target }.forEach { o -> social = social.withStance(o, op.target) { it.adjust(suspicion = op.delta) } }
                    social = social.withThreat(op.target, op.delta * 0.5f)
                } else {
                    social = social.withStance(op.observer, op.target) { it.adjust(suspicion = op.delta) }
                }
            is SocialOp.Trust -> social = social.withStance(op.observer, op.target) { it.adjust(trust = op.delta) }
            is SocialOp.Agitate -> social = social.withAgitation(op.seat, op.delta * flawWeight(op.seat, op.flaw))
            is SocialOp.Grudge -> {
                onGrudge(op.holder, op.target, op.weight)
                social = social.withThreat(op.target, 0.2f)
            }
        }
    }

    // ── targeting nudge — the engine-facing teeth ──────────────────────────────────

    /**
     * Bias a bot's already-chosen [chosen] intent toward the table's social pressure, choosing only
     * among the legal team-safe [choices] the engine offered. Returns [chosen] unchanged when no
     * pressure applies (so a calm table plays exactly as the tuned AI would). Deterministic.
     */
    fun nudgeBotIntent(
        seat: Int,
        chosen: Intent,
        choices: List<Intent>,
        view: PlayerView,
    ): Intent {
        val declared = chosen as? Intent.DeclareAction
        val opponents = view.players.filter { !it.eliminated && it.id != view.viewer }.map { it.id.raw }
        if (opponents.isEmpty()) return chosen

        // 1. Where does this bot want to point, socially?
        val desired = desiredTarget(seat, opponents) ?: return chosen
        if (desired == seat) return chosen

        // 2. How strongly? Conspiracy pull + flaw agitation, scaled by impulsiveness.
        val pull =
            (
                social.threatOf(desired) * 0.35f + social.agitationOf(seat) * 0.5f +
                    impulse(seat) * 0.2f
            ).coerceIn(0f, 0.92f)
        val (roll, r) = nudgeRng.nextInt(100)
        nudgeRng = r
        if (roll >= (pull * 100f).toInt()) return chosen

        // 3a. If already attacking, re-target the SAME attack onto the desired seat.
        if (declared != null && Rules.targetOf(declared.action) != null) {
            attackTo(choices, desired, sameTypeAs = declared.action)?.let { return it }
            attackTo(choices, desired, sameTypeAs = null)?.let { return it } // any attack onto desired
            return chosen
        }
        // 3b. Bot was playing it safe but is agitated/pressured → upgrade into an attack on desired.
        if (social.agitationOf(seat) >= 0.4f || social.threatOf(desired) >= 0.7f) {
            attackTo(choices, desired, sameTypeAs = null)?.let { return it }
        }
        return chosen
    }

    /** The seat this bot is socially primed to hit: vendetta/suspicion target, else the conspiracy target. */
    private fun desiredTarget(
        seat: Int,
        opponents: List<Int>,
    ): Int? {
        val flaw = dominantFlaw(seat)
        // Vengeance/Paranoia steer toward whom this bot personally resents/suspects most.
        if (flaw == CharacterFlaw.VENGEANCE || flaw == CharacterFlaw.PARANOIA) {
            val personal = opponents.maxByOrNull { social.stance(seat, it).suspicion - social.stance(seat, it).trust }
            if (personal != null && social.stance(seat, personal).suspicion >= 0.3f) return personal
        }
        // Allies are spared; otherwise follow the table's conspiracy target.
        val ally = social.allyOf(seat)
        return social.topThreat(opponents.filter { it != ally }, minimum = 0.4f)
    }

    private fun attackTo(
        choices: List<Intent>,
        targetRaw: Int,
        sameTypeAs: Action?,
    ): Intent? =
        choices.filterIsInstance<Intent.DeclareAction>().firstOrNull { i ->
            val tgt = Rules.targetOf(i.action) ?: return@firstOrNull false
            tgt.raw == targetRaw && (sameTypeAs == null || sameType(i.action, sameTypeAs))
        }

    // ── proactive chatter helpers ─────────────────────────────────────────────────

    private fun maybeTauntOnAttack(
        e: GameEvent.ActionDeclared,
        turn: Int,
    ) {
        val a = e.action
        val target = Rules.targetOf(a)?.raw ?: return
        val sp = info[e.actor.raw] ?: return
        if (sp.isHuman || sp.personaId == null) return
        if (!chance(38)) return
        emit(sp.seat, voice.taunt(sp.personaId, info[target]?.name ?: "unhe"), MessageTone.HOSTILE, ChatKind.TABLE, target, turn)
    }

    private fun maybeChatterOnElimination(
        victim: Int,
        turn: Int,
    ) {
        // The conspiracy target going down → an ally or piler-on crows; a flaw victim laments.
        val sp = info.values.firstOrNull { !it.isHuman && it.personaId != null && it.seat != victim && chance(40) } ?: return
        emit(sp.seat, voice.taunt(sp.personaId!!, info[victim]?.name ?: "woh"), MessageTone.BOAST, ChatKind.TABLE, victim, turn)
    }

    private fun maybeGloat(
        seat: Int,
        turn: Int,
    ) {
        val sp = info[seat] ?: return
        if (sp.isHuman || sp.personaId == null || !chance(45)) return
        emit(sp.seat, voice.gloat(sp.personaId), MessageTone.BOAST, ChatKind.TABLE, turn = turn)
    }

    /** Once per turn, the current conspiracy target may voice its paranoia, or a piler-on may rally. */
    fun maybeTurnChatter(
        view: PlayerView,
        turn: Int,
    ) {
        val opp = view.players.filter { !it.eliminated }.map { it.id.raw }
        val tgt = social.topThreat(opp, minimum = 0.7f) ?: return
        if (tgt == humanSeat) {
            // Bots openly rally against the player.
            val rallier = info.values.firstOrNull { !it.isHuman && it.personaId != null && chance(35) } ?: return
            emit(rallier.seat, voice.pileOn(rallier.personaId!!, info[humanSeat]?.name ?: "khiladi"), MessageTone.HOSTILE, ChatKind.TABLE, humanSeat, turn)
        } else {
            val ti = info[tgt] ?: return
            if (!ti.isHuman && ti.personaId != null && chance(35)) {
                emit(ti.seat, voice.threatened(ti.personaId), MessageTone.PANICKED, ChatKind.TABLE, turn = turn)
            }
        }
    }

    // ── suggestions for the player ─────────────────────────────────────────────────

    fun suggestions(view: PlayerView): List<ChatSuggestion> {
        recomputeSuggestions(view)
        return pendingSuggestions
    }

    private fun recomputeSuggestions(view: PlayerView) {
        val opps = livingOpponentsFrom(view)
        if (opps.isEmpty()) {
            pendingSuggestions = emptyList()
            return
        }
        // Mid-arc replies take priority if still valid.
        val activeReplies =
            pendingSuggestions
                .filter { it.kind == ChatActionKind.ARC_REPLY || it.kind == ChatActionKind.DEFLECT }
                .filter { s -> s.targetSeat == null || opps.any { it.seat == s.targetSeat } }
        val starters =
            buildList {
                if (ArcId.GATHBANDHAN !in activeArcs()) bestForGathbandhan(view, opps)?.let { add(it) }
                if (ArcId.AFWAAH !in activeArcs()) {
                    leader(view, opps)?.let {
                        add(
                            ChatSuggestion(
                                "start.afwaah.${it.seat}",
                                "Afwaah pe: ${it.name}",
                                ChatActionKind.ARC_START,
                                ArcId.AFWAAH,
                                it.seat,
                                it.name,
                                "Plant a rumour — turn the table on them",
                            ),
                        )
                    }
                }
                if (ArcId.STING !in activeArcs()) {
                    bestFlaw(opps, CharacterFlaw.EGO)?.let {
                        add(
                            ChatSuggestion(
                                "start.sting.${it.seat}",
                                "Phasaao: ${it.name}",
                                ChatActionKind.ARC_START,
                                ArcId.STING,
                                it.seat,
                                it.name,
                                "Flatter them into an overreach",
                            ),
                        )
                    }
                }
                if (ArcId.BADLA !in activeArcs()) {
                    bestFlaw(opps, CharacterFlaw.VENGEANCE)?.let {
                        add(
                            ChatSuggestion(
                                "start.badla.${it.seat}",
                                "Badla via: ${it.name}",
                                ChatActionKind.ARC_START,
                                ArcId.BADLA,
                                it.seat,
                                it.name,
                                "Point their grudge at a rival",
                            ),
                        )
                    }
                }
            }
        val tableTalk =
            leader(view, opps)?.let {
                listOf(ChatSuggestion("talk.taunt.${it.seat}", "Taunt ${it.name}", ChatActionKind.TAUNT, null, it.seat, it.name, "Heat them up"))
            } ?: emptyList()
        pendingSuggestions = (activeReplies + starters + tableTalk).distinctBy { it.id }.take(6)
    }

    private fun bestForGathbandhan(
        view: PlayerView,
        opps: List<SeatRef>,
    ): ChatSuggestion? {
        val s = opps.filter { social.allyOf(0) != it.seat }.maxByOrNull { strength(view, it.seat) } ?: return null
        return ChatSuggestion(
            "start.gathbandhan.${s.seat}",
            "Gathbandhan: ${s.name}",
            ChatActionKind.ARC_START,
            ArcId.GATHBANDHAN,
            s.seat,
            s.name,
            "Secret pact — coordinate, then betray",
        )
    }

    // ── small helpers ──────────────────────────────────────────────────────────────

    /** @return the new message's [ChatMessage.id], so a caller can later [embellish] it. */
    private fun emit(
        sender: Int,
        body: String,
        tone: MessageTone,
        kind: ChatKind,
        target: Int? = null,
        turn: Int = 0,
        arc: ArcId? = null,
        fromPlayer: Boolean = false,
    ): Long {
        val id = nextId++
        chat.add(ChatMessage(id, sender, target, body, tone, kind, arc, turn, fromPlayer || sender == humanSeat))
        while (chat.size > MAX_FEED) chat.removeAt(0)
        return id
    }

    private fun chance(pct: Int): Boolean {
        val (r, n) = rng.nextInt(100)
        rng = n
        return r < pct
    }

    /**
     * Whether [seat] accepts the player's pact. PURE (no rng) so it is a deterministic function of the
     * reconstructed social fabric — a bot the player hasn't wronged plays along; one it has hit or
     * lied to refuses. Keeping this rng-free is what lets a chat sent mid-bot-round replay exactly.
     */
    private fun botAcceptsPact(seat: Int): Boolean {
        val st = social.stance(seat, humanSeat)
        return (st.trust - st.suspicion * 0.8f) >= -0.12f
    }

    private fun dominantFlaw(seat: Int): CharacterFlaw = info[seat]?.profile?.let { FlawModel.dominantFlaw(it) } ?: CharacterFlaw.IMPULSE

    private fun flawWeight(
        seat: Int,
        flaw: CharacterFlaw,
    ): Float = info[seat]?.profile?.let { FlawModel.susceptibility(it, flaw) } ?: 0.5f

    private fun impulse(seat: Int): Float = info[seat]?.profile?.let { 1f - it.predictability } ?: 0.3f

    private fun bestFlaw(
        opps: List<SeatRef>,
        flaw: CharacterFlaw,
    ): SeatRef? = opps.maxByOrNull { flawWeight(it.seat, flaw) }?.takeIf { flawWeight(it.seat, flaw) >= 0.4f }

    private fun livingOpponents(): List<SeatRef> = info.values.filter { !it.isHuman }.map { SeatRef(it.seat, it.name) }

    private fun livingOpponentsFrom(view: PlayerView): List<SeatRef> =
        view.players.filter { !it.eliminated && it.id.raw != humanSeat }.map { SeatRef(it.id.raw, info[it.id.raw]?.name ?: "Seat ${it.id.raw}") }

    private fun leader(
        view: PlayerView,
        opps: List<SeatRef>,
    ): SeatRef? = opps.maxByOrNull { strength(view, it.seat) }

    private fun strength(
        view: PlayerView,
        seat: Int,
    ): Int = view.players.firstOrNull { it.id.raw == seat }?.let { it.faceDownCount * 10 + it.coins } ?: 0

    private fun sameType(
        a: Action,
        b: Action,
    ): Boolean =
        when {
            a is Action.Coup && b is Action.Coup -> true
            a is Action.Assassinate && b is Action.Assassinate -> true
            a is Action.Steal && b is Action.Steal -> true
            a is Action.Investigate && b is Action.Investigate -> true
            else -> false
        }

    companion object {
        private const val SOCIAL_SALT = 0x44415242_41522121L // "DARBAR!!" — cosmetic chatter stream
        private const val NUDGE_SALT = 0x4E55_44_4745_5221L // "NUDGER!"  — game-affecting nudge stream
        const val MAX_FEED = 80
    }
}
