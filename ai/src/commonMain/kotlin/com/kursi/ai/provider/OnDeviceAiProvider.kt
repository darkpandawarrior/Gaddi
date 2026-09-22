package com.kursi.ai.provider

import com.siddharth.kmp.llmchat.AiMessage
import com.siddharth.kmp.llmchat.AiProvider

/**
 * Builds the platform on-device [AiProvider]. Named like a constructor on purpose: every call site
 * reads `OnDeviceAiProvider()` exactly as it did when this was an `expect class`.
 *
 * An `expect fun` returning the shared [AiProvider] interface rather than an `expect class`:
 * expect/actual CLASSIFIERS are still Beta (KT-61573) and warn on every compile of every source
 * set, and the only ways to silence that are the blanket `-Xexpect-actual-classes` flag or a
 * `@Suppress` on each of the five files. It also deletes the member-by-member redeclaration this
 * used to need — an `expect class` must restate everything it inherits, which is why the previous
 * version carried a note about tracking [AiProvider.complete]'s exact return type by hand.
 * Expect/actual FUNCTIONS are stable, and nothing at a call site changes.
 *
 * ktlint:standard:function-naming — a constructor-like factory. Kotlin's own convention
 * allows PascalCase here; ktlint only recognises the pattern when the function name equals
 * its return TYPE name, which it cannot be when the factory returns an interface.
 */
@Suppress("ktlint:standard:function-naming")
expect fun OnDeviceAiProvider(): AiProvider

/**
 * Flattens a chat-shaped [AiMessage] list into the single text prompt toolkit `:ai`'s [com.siddharth.kmp.ai.OnDeviceLlm]
 * expects (that seam is one-shot text-in/text-out, not multi-turn) — shared by every platform actual
 * that routes through it.
 */
internal fun List<AiMessage>.toOnDevicePrompt(): String = joinToString("\n") { "${it.role}: ${it.content}" }
