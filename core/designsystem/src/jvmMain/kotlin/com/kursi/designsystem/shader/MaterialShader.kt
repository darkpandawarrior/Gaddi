package com.kursi.designsystem.shader

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

// identical to iosMain/wasmJsMain (~20 lines) — all three are Skia/RuntimeEffect
// backed and the multiplatform source-set hierarchy has no shared "skiko" intermediate for
// jvm+ios+wasmJs, so this is duplicated per-target rather than built for one leaf source set.

// Compiled RuntimeEffect is cached by source string — RuntimeEffect.makeForShader parses+compiles
// SkSL and this block re-runs every frame (it captures the animated `time` uniform), so without
// caching every frame paid a full shader recompile.
private val effectCache = mutableMapOf<String, RuntimeEffect>()

/**
 * SkSL via `org.jetbrains.skia.RuntimeEffect`, applied as a Compose [RenderEffect] on the layer.
 * A shader that fails to compile (or any other RuntimeEffect hiccup) is swallowed via
 * [runCatching] so a bad shader never crashes the layer; it just renders without the effect.
 */
actual fun Modifier.materialShader(
    sksl: String,
    uniforms: Map<String, FloatArray>,
    time: Float,
): Modifier =
    graphicsLayer {
        renderEffect =
            runCatching {
                val effect = effectCache.getOrPut(sksl) { RuntimeEffect.makeForShader(sksl) }
                val builder = RuntimeShaderBuilder(effect)
                builder.uniform("time", time)
                for ((name, value) in uniforms) builder.uniform(name, value)
                ImageFilter.makeRuntimeShader(builder, "content", null)
            }.getOrNull()?.asComposeRenderEffect()
    }
