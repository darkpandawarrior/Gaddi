package com.kursi.feature.game.narrative

import com.kursi.ai.social.CharacterFlaw

/** A seat + its display name — used to build player-facing suggestion chips. */
data class SeatRef(
    val seat: Int,
    val name: String,
)

/**
 * A semantic effect an arc beat applies to the table's social fabric. The [SocialDirector] is the
 * single place that maps these onto a `SocialState` / real bot grudges — keeping arcs pure data so
 * they are trivially unit-testable.
 *
 * `observer == ALL` (`-1`) on [Suspicion]/[Trust] means "the whole table", applied to every living seat.
 */
sealed interface SocialOp {
    data class Ally(
        val a: Int,
        val b: Int,
    ) : SocialOp

    data class Betray(
        val seat: Int,
    ) : SocialOp

    data class Threat(
        val seat: Int,
        val delta: Float,
    ) : SocialOp

    data class Suspicion(
        val observer: Int,
        val target: Int,
        val delta: Float,
    ) : SocialOp

    data class Trust(
        val observer: Int,
        val target: Int,
        val delta: Float,
    ) : SocialOp

    data class Agitate(
        val seat: Int,
        val flaw: CharacterFlaw,
        val delta: Float,
    ) : SocialOp

    /** Bump a real [com.kursi.ai.persona.PersonaPolicy] grudge — the engine-facing teeth of Badla. */
    data class Grudge(
        val holder: Int,
        val target: Int,
        val weight: Int,
    ) : SocialOp

    companion object {
        const val ALL = -1
    }
}

/** A line an arc wants spoken, named semantically; the director renders it via [ChatVoice]. */
data class ArcBeat(
    val speakerSeat: Int,
    val beatKey: String,
    val tone: MessageTone,
    val arc: ArcId,
    val targetSeat: Int? = null,
    val fromPlayer: Boolean = false,
)

/** The result of starting or advancing an arc: effects to apply, lines to speak, follow-ups, next state. */
data class ArcStep(
    val ops: List<SocialOp> = emptyList(),
    val beats: List<ArcBeat> = emptyList(),
    val nextState: ArcState? = null,
    val suggestions: List<ChatSuggestion> = emptyList(),
)

/** Live state of one in-flight arc. */
data class ArcState(
    val arc: ArcId,
    val stage: Int = 0,
    val instigator: Int = 0,
    val target: Int? = null,
    val ally: Int? = null,
    val ended: Boolean = false,
)

/**
 * The four arcs as pure state machines. Given the player's chosen [HumanChatInput] (and the table
 * context), each produces an [ArcStep]. No randomness, no engine access, no UI — the director owns
 * those. The player is always [instigator] = seat 0 in narrative mode.
 */
object StoryArcs {
    /** ARC_START chips offered for [arc] given the living opponents [opponents]. */
    fun openingSuggestions(
        arc: ArcId,
        opponents: List<SeatRef>,
    ): List<ChatSuggestion> =
        when (arc) {
            ArcId.GATHBANDHAN ->
                opponents.map {
                    ChatSuggestion(
                        id = "start.gathbandhan.${it.seat}",
                        label = "Gathbandhan: ${it.name}",
                        kind = ChatActionKind.ARC_START,
                        arc = arc,
                        targetSeat = it.seat,
                        targetName = it.name,
                        consequence = "Secret pact — coordinate, then betray",
                    )
                }
            ArcId.AFWAAH ->
                opponents.map {
                    ChatSuggestion(
                        id = "start.afwaah.${it.seat}",
                        label = "Afwaah pe: ${it.name}",
                        kind = ChatActionKind.ARC_START,
                        arc = arc,
                        targetSeat = it.seat,
                        targetName = it.name,
                        consequence = "Plant a rumour — turn the table on them",
                    )
                }
            ArcId.STING ->
                opponents.map {
                    ChatSuggestion(
                        id = "start.sting.${it.seat}",
                        label = "Phasaao: ${it.name}",
                        kind = ChatActionKind.ARC_START,
                        arc = arc,
                        targetSeat = it.seat,
                        targetName = it.name,
                        consequence = "Flatter them into an overreach",
                    )
                }
            ArcId.BADLA ->
                opponents.map {
                    ChatSuggestion(
                        id = "start.badla.${it.seat}",
                        label = "Badla via: ${it.name}",
                        kind = ChatActionKind.ARC_START,
                        arc = arc,
                        targetSeat = it.seat,
                        targetName = it.name,
                        consequence = "Point their grudge at a rival",
                    )
                }
        }

    /**
     * Begin an arc. [accepted] is the director's read of whether the bot plays along (computed from
     * its stance/flaw) — used only by Gathbandhan (a pact can be refused). [secondName]/[secondSeat]
     * carry the "rival" choice for arcs that need a second party (set in a follow-up reply normally,
     * but begin can pre-seed for one-tap arcs).
     */
    fun begin(
        arc: ArcId,
        target: Int,
        targetName: String,
        accepted: Boolean,
    ): ArcStep =
        when (arc) {
            ArcId.GATHBANDHAN -> {
                val ops =
                    if (accepted) {
                        listOf(SocialOp.Ally(0, target), SocialOp.Trust(target, 0, delta = 0.5f))
                    } else {
                        listOf(SocialOp.Suspicion(target, 0, delta = 0.25f), SocialOp.Threat(0, delta = 0.15f))
                    }
                val beats =
                    listOf(
                        ArcBeat(0, "gathbandhan.offer", MessageTone.SLY, arc, target, fromPlayer = true),
                        ArcBeat(
                            target,
                            if (accepted) "gathbandhan.accept" else "gathbandhan.decline",
                            if (accepted) MessageTone.FRIENDLY else MessageTone.HOSTILE,
                            arc,
                            0,
                        ),
                    )
                val next = if (accepted) ArcState(arc, stage = 1, target = target, ally = target) else ArcState(arc, ended = true)
                val sugg =
                    if (accepted) {
                        listOf(
                            ChatSuggestion(
                                "badla.gathbandhan.knife.$target",
                                "Knife $targetName first",
                                ChatActionKind.DEFLECT,
                                arc,
                                target,
                                targetName,
                                "Betray the pact before they do",
                            ),
                        )
                    } else {
                        emptyList()
                    }
                ArcStep(ops, beats, next, sugg)
            }
            ArcId.AFWAAH ->
                ArcStep(
                    ops =
                        listOf(
                            SocialOp.Suspicion(SocialOp.ALL, target, delta = 0.30f),
                            SocialOp.Threat(target, delta = 0.40f),
                            SocialOp.Agitate(target, CharacterFlaw.PARANOIA, delta = 0.5f),
                        ),
                    beats =
                        listOf(
                            ArcBeat(0, "afwaah.plant", MessageTone.SLY, arc, target, fromPlayer = true),
                            ArcBeat(-1, "afwaah.spreads", MessageTone.SYSTEM, arc, target),
                        ),
                    nextState = ArcState(arc, stage = 1, target = target),
                    suggestions =
                        listOf(
                            ChatSuggestion(
                                "afwaah.fuel.$target",
                                "Aur hawa do (fuel it)",
                                ChatActionKind.ARC_REPLY,
                                arc,
                                target,
                                targetName,
                                "Twist the knife — more heat on $targetName",
                            ),
                        ),
                )
            ArcId.STING ->
                ArcStep(
                    ops =
                        listOf(
                            SocialOp.Agitate(target, CharacterFlaw.EGO, delta = 0.6f),
                            SocialOp.Agitate(target, CharacterFlaw.GREED, delta = 0.5f),
                            SocialOp.Trust(target, 0, delta = 0.3f),
                        ),
                    beats =
                        listOf(
                            ArcBeat(0, "sting.flatter", MessageTone.FRIENDLY, arc, target, fromPlayer = true),
                            ArcBeat(target, "sting.swallow", MessageTone.BOAST, arc, 0),
                        ),
                    nextState = ArcState(arc, stage = 1, target = target),
                    suggestions =
                        listOf(
                            ChatSuggestion(
                                "sting.dare.$target",
                                "Dare them to prove it",
                                ChatActionKind.ARC_REPLY,
                                arc,
                                target,
                                targetName,
                                "Push $targetName into the blunder",
                            ),
                        ),
                )
            ArcId.BADLA ->
                ArcStep(
                    // Badla needs a rival; begin pre-seeds the *vengeful* bot, the reply picks the rival.
                    ops = listOf(SocialOp.Trust(target, 0, delta = 0.35f)),
                    beats =
                        listOf(
                            ArcBeat(0, "badla.approach", MessageTone.SLY, arc, target, fromPlayer = true),
                            ArcBeat(target, "badla.listen", MessageTone.HOSTILE, arc, 0),
                        ),
                    nextState = ArcState(arc, stage = 1, target = target),
                    suggestions = emptyList(), // director fills "point at <rival>" chips for living rivals
                )
        }

    /** Advance an arc on a player reply chip. */
    fun reply(
        state: ArcState,
        input: HumanChatInput,
    ): ArcStep =
        when (state.arc) {
            ArcId.GATHBANDHAN ->
                when (input.kind) {
                    ChatActionKind.DEFLECT ->
                        ArcStep( // knife the ally first
                            ops = listOf(SocialOp.Betray(0), SocialOp.Grudge(state.ally ?: -1, 0, weight = 0)),
                            beats =
                                listOf(
                                    ArcBeat(0, "gathbandhan.knife", MessageTone.HOSTILE, state.arc, state.ally, fromPlayer = true),
                                    ArcBeat(state.ally ?: 0, "gathbandhan.scorned", MessageTone.HOSTILE, state.arc, 0),
                                ),
                            nextState = state.copy(ended = true),
                        )
                    else -> ArcStep(nextState = state)
                }
            ArcId.AFWAAH ->
                ArcStep(
                    ops =
                        listOf(
                            SocialOp.Suspicion(SocialOp.ALL, state.target ?: 0, delta = 0.25f),
                            SocialOp.Threat(state.target ?: 0, delta = 0.35f),
                        ),
                    beats = listOf(ArcBeat(0, "afwaah.fuel", MessageTone.SLY, state.arc, state.target, fromPlayer = true)),
                    nextState = state.copy(stage = state.stage + 1),
                )
            ArcId.STING ->
                ArcStep(
                    ops =
                        listOf(
                            SocialOp.Agitate(state.target ?: 0, CharacterFlaw.EGO, delta = 0.4f),
                            SocialOp.Agitate(state.target ?: 0, CharacterFlaw.GREED, delta = 0.4f),
                        ),
                    beats =
                        listOf(
                            ArcBeat(0, "sting.dare", MessageTone.SLY, state.arc, state.target, fromPlayer = true),
                            ArcBeat(state.target ?: 0, "sting.boast", MessageTone.BOAST, state.arc, 0),
                        ),
                    nextState = state.copy(stage = state.stage + 1),
                )
            ArcId.BADLA -> { // input.targetSeat is the RIVAL to point the grudge at
                val vengeful = state.target ?: 0
                val rival = input.targetSeat ?: return ArcStep(nextState = state)
                ArcStep(
                    ops =
                        listOf(
                            SocialOp.Grudge(vengeful, rival, weight = 3),
                            SocialOp.Threat(rival, delta = 0.4f),
                            SocialOp.Suspicion(vengeful, rival, delta = 0.4f),
                        ),
                    beats =
                        listOf(
                            ArcBeat(0, "badla.point", MessageTone.SLY, state.arc, rival, fromPlayer = true),
                            ArcBeat(vengeful, "badla.accept", MessageTone.HOSTILE, state.arc, rival),
                        ),
                    nextState = state.copy(stage = 2, ended = true),
                )
            }
        }
}
