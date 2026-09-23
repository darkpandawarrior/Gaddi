package com.kursi.designsystem

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

/**
 * Named motion vocabulary (spec §7). Tracks reference these tokens, never raw literals. Call sites
 * are responsible for collapsing to a static end-state under reducedMotion (see MomentStaticFrames).
 */
object GaddiMotion {
    /** Crisp UI response (chip press, toggle). */
    fun <T> snap(): AnimationSpec<T> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)

    /** A beat landing on the table (card slide, token move). */
    fun <T> settle(): AnimationSpec<T> = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)

    /** Weighty dramatic beat (stamp slam, elimination). */
    fun <T> dramatic(): AnimationSpec<T> = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)

    /** Focus-pull camera push duration (ms). */
    const val FOCUS_PULL_MS: Int = 420

    /** Card deal/flip duration (ms). */
    const val DEAL_MS: Int = 300
}
