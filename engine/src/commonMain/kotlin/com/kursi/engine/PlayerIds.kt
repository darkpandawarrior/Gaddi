package com.kursi.engine

import kotlin.jvm.JvmInline

/**
 * Type-safe wrapper for a player seat index.
 *
 * Declared once in commonMain. This used to be an `expect value class` with four `actual`s
 * (jvmMain, androidMain, nativeMain, wasmJsMain) on the premise that "@JvmInline lives in
 * kotlin.jvm which is JVM-only". That premise is wrong: `kotlin.jvm.JvmInline` is declared in
 * the COMMON stdlib and resolves on every target — it simply has no effect off the JVM, which
 * is exactly the behaviour those four files were hand-rolling. Collapsing it also clears the
 * EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING the split produced on every compile
 * (KT-61573), without reaching for -Xexpect-actual-classes.
 */
@JvmInline
value class PlayerId(
    val raw: Int,
)

/**
 * Type-safe wrapper for a card identity. Same reasoning as [PlayerId].
 */
@JvmInline
value class CardId(
    val raw: Int,
)
