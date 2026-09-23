package com.kursi.feature.game

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.kursi.engine.Action
import com.kursi.engine.Role as GaddiRole

/*
 * A11y (M3 §1) — accessibility semantics for the in-game surfaces.
 *
 * Compose merges a tappable Box's own visuals into a single node but does NOT synthesize a useful
 * spoken label from decorative Text + emoji glyphs. These helpers stamp a clear [contentDescription],
 * a [Role], and (where relevant) a [stateDescription] / disabled flag so TalkBack/VoiceOver announce
 * each control as an actionable, self-describing element instead of a pile of glyph fragments.
 */

/** Semantics for an action-dock button: spoken name, cost, claimed role, recommended + enabled state. */
fun Modifier.actionChipSemantics(
    name: String,
    cost: String,
    action: Action?,
    enabled: Boolean,
    recommended: Boolean,
): Modifier =
    semantics(mergeDescendants = true) {
        role = Role.Button
        if (!enabled) disabled()
        val claim =
            action
                ?.let {
                    com.kursi.engine.Rules
                        .claimedRole(it)
                }?.let { ", claims ${roleLabelA11y(it)}" } ?: ""
        val state =
            buildString {
                if (recommended) append("Coach recommends. ")
                if (!enabled) append("Unavailable. ")
            }
        stateDescription = state.trim().ifEmpty { "Available" }
        contentDescription = "$name, $cost$claim"
    }

/** Semantics for a reaction chip (challenge / block / pass). */
fun Modifier.reactionChipSemantics(
    label: String,
    recommended: Boolean,
): Modifier =
    semantics(mergeDescendants = true) {
        role = Role.Button
        stateDescription = if (recommended) "Coach recommends" else "Available"
        contentDescription = label
    }

/** Semantics for an exchange keep-option. */
fun Modifier.keepOptionSemantics(
    roleNames: List<String>,
    recommended: Boolean,
): Modifier =
    semantics(mergeDescendants = true) {
        role = Role.Button
        stateDescription = if (recommended) "Coach recommends" else "Available"
        contentDescription =
            if (roleNames.isEmpty()) {
                "Keep nothing"
            } else {
                "Keep " + roleNames.joinToString(" and ")
            }
    }

/** Semantics for one of the human's own influence cards during a lose-influence choice. */
fun Modifier.loseInfluenceCardSemantics(role: GaddiRole): Modifier =
    semantics(mergeDescendants = true) {
        this.role = Role.Button
        contentDescription = "Reveal and lose your ${roleLabelA11y(role)}"
    }

/** Semantics for an inspectable own card (long-press identity), not a loss choice. */
fun Modifier.handCardSemantics(
    role: GaddiRole,
    faceUp: Boolean,
): Modifier =
    semantics(mergeDescendants = true) {
        contentDescription = if (faceUp) "Revealed ${roleLabelA11y(role)}" else "Your hidden ${roleLabelA11y(role)}"
    }

/**
 * Semantics for an opponent plate — name, coins, influence remaining, standing claim, suspicion.
 * Merged into one node so a screen reader reads the whole rival dossier as a unit.
 */
fun Modifier.opponentPlateSemantics(
    name: String,
    coins: Int,
    influenceAlive: Int,
    influenceLost: Int,
    claim: String?,
    eliminated: Boolean,
    isValidTarget: Boolean,
): Modifier =
    semantics(mergeDescendants = true) {
        role = Role.Button
        if (eliminated) disabled()
        val parts =
            buildString {
                append(name)
                if (eliminated) {
                    append(", eliminated")
                } else {
                    append(", $coins coins, $influenceAlive influence remaining")
                    if (influenceLost > 0) append(", $influenceLost revealed")
                    if (claim != null) append(", $claim")
                }
            }
        if (isValidTarget) stateDescription = "Valid target"
        contentDescription = parts
    }

/** Plain-language role name for spoken output (avoids the glyph soup of the visual label). */
fun roleLabelA11y(role: GaddiRole): String =
    when (role) {
        GaddiRole.NETA -> "Neta"
        GaddiRole.BHAI -> "Bhai"
        GaddiRole.BABU -> "Babu"
        GaddiRole.JUGAADU -> "Jugaadu"
        GaddiRole.VAKIL -> "Vakil"
        GaddiRole.PATRAKAAR -> "Patrakaar"
    }
