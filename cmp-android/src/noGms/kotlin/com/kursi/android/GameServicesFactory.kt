package com.kursi.android

import androidx.activity.ComponentActivity
import com.kursi.gameservices.GameServices

/**
 * F-Droid (noGms) flavor: Play Games Services is a non-free Google dependency, so this build has
 * no game services at all.
 *
 * Null, not an empty implementation. A stub that answers every call with `false` still lets the UI
 * render a leaderboard entry that quietly does nothing; null makes "there is no such surface here"
 * a fact the caller has to handle. Same reasoning as the module having no jvm/wasm factory.
 */
object GameServicesFactory {
    // Both detekt findings here are the intended design, not defects: returning a constant IS the
    // point (there is no game-services surface in this flavor), and `activity` is kept so the
    // signature stays identical to the gms factory, which lets the call site be flavor-agnostic.
    @Suppress("FunctionOnlyReturningConstant", "UnusedParameter")
    fun create(activity: ComponentActivity): GameServices? = null
}
