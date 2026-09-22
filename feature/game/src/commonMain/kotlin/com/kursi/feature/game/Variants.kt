package com.kursi.feature.game

import com.kursi.engine.Role
import com.kursi.engine.baseRoles

/**
 * A DRAFT ("Nilaami") deck preset — a hand-picked role set for the table, following the design doc's
 * "host picks the roles / pick a preset" spec. Carried through Setup → Route → NewGame as a stable
 * [code] string (nav-serializable) and decoded back to the engine [roles] in the ViewModel.
 */
data class DraftPreset(
    val code: String,
    val title: String,
    val subtitle: String,
    val roles: List<Role>,
)

object DraftPresets {
    val CLASSIC = DraftPreset("CLASSIC", "Classic Cabinet", "The five-role standard", baseRoles)
    val PRESS = DraftPreset("PRESS", "Press Gallery", "Bring the Patrakaar in early", baseRoles + Role.PATRAKAAR)
    val NO_VAKIL =
        DraftPreset(
            "NO_VAKIL",
            "No Lawyers",
            "Drop the Vakil — Supari can't be blocked truthfully",
            listOf(Role.NETA, Role.BHAI, Role.BABU, Role.JUGAADU),
        )
    val KNIVES =
        DraftPreset(
            "KNIVES",
            "Knives Out",
            "Drop the Babu — no honest Vasooli block",
            listOf(Role.NETA, Role.BHAI, Role.JUGAADU, Role.VAKIL),
        )

    /** All presets, classic first. */
    val ALL: List<DraftPreset> = listOf(CLASSIC, PRESS, NO_VAKIL, KNIVES)

    /** The engine role set for a preset [code], or null for "no draft" (classic scaling). */
    fun rolesOf(code: String?): List<Role>? =
        if (code.isNullOrBlank() ||
            code == CLASSIC.code
        ) {
            null
        } else {
            ALL.firstOrNull { it.code == code }?.roles
        }
}
