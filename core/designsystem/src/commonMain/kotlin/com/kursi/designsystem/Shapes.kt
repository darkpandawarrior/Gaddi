package com.kursi.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ─────────────────────────── Squircle ───────────────────────────
// spec §8: "Render as a superellipse Shape where the target supports it;
// fall back to RoundedCornerShape."
//
// GenericShape + Path with a superellipse (n≈4) requires platform-specific Path ops
// that are not available uniformly across all KMP targets (wasmJs / iOS) in the
// compose.ui 1.11.x commonMain API surface.  We therefore use RoundedCornerShape
// as the cross-target fallback as permitted by the spec.  The visual difference
// between n=4 superellipse and a large rounded rect is subtle; the token names and
// radii are spec-correct.  A native-platform override (GenericShape + Path) can be
// layered in as an expect/actual in a future pass once compose.ui exposes a stable
// commonMain Path.addSuperellipse API.

/**
 * Returns a squircle [Shape] with continuous-curvature corners at [radius].
 * Currently implemented as [RoundedCornerShape] (cross-target fallback per spec §8).
 *
 * ktlint:standard:function-naming — a constructor-like Shape factory; PascalCase matches
 * RoundedCornerShape/CircleShape, which is what every call site reads it next to.
 */
@Suppress("ktlint:standard:function-naming")
fun Squircle(radius: Dp): Shape = RoundedCornerShape(radius)

// ─────────────────────────── KursiRadii token object ───────────────────────────
// spec §8 token table — exact dp values, do not change.

object KursiRadii {
    /** 6 dp — influence-pip backplates, small tags. */
    val xs: Dp = 6.dp

    /** 11 dp — claim chips, coin pills, log-row icons. */
    val sm: Dp = 11.dp

    /** 14 dp — avatars, card-backs. */
    val md: Dp = 14.dp

    /** 20 dp — opponent chips, dock surface, status spine. */
    val lg: Dp = 20.dp

    /** 22 dp — influence cards, action/reaction buttons, confirm strip. */
    val xl: Dp = 22.dp

    /** 28 dp — felt play area, reaction theatre, bottom sheets. */
    val xxl: Dp = 28.dp
}
