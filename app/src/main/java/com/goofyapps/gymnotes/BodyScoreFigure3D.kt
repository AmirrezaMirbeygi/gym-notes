package com.goofyapps.gymnotes

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.sceneview.Scene
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.node.CameraNode
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberModelLoader
import kotlin.math.max
import kotlin.math.min
import kotlin.math.abs

@Composable
fun BodyScoreFigure3D(
    scores: Map<String, Int>,
    bodyFatPercent: Float = 15f // 5..30
) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)

    // IMPORTANT: do NOT use rememberCameraNode() -> it can crash on dispose (NPE in destroy()).
    val cameraNode = remember(engine) { 
        CameraNode(engine).apply {
            // Set initial camera position - adjust these values
            position = Position(x = 0.0f, y = 1.5f, z = 0.0f)
            lookAt(Position(x = 0.0f, y = 0.0f, z = 0.0f))
        }
    }

    var bodyNode by remember { mutableStateOf<ModelNode?>(null) }
    var morphCache by remember { mutableStateOf<MorphCache?>(null) }

    // Used to avoid doing work every frame if value didn't change (fat only)
    var lastFat01 by remember { mutableFloatStateOf(-1f) }

    LaunchedEffect(Unit) {
        val instance = modelLoader.createModelInstance("models/body_morph20.glb")
        val node = ModelNode(modelInstance = instance).apply {
            position = Position(x = 0.0f, y = -1.50f, z = -2.2f)
            rotation = Rotation(x = 0.0f, y = 0.0f, z = 0.0f)
            scale = Scale(1f)
        }
        bodyNode = node
        morphCache = buildMorphCache(node).also { logMorphInfoOnce(it) }

        node.renderableNodes.forEachIndexed { index, renderable ->
            Log.d("MORPH", "Renderable[$index] morph targets: ${renderable.morphTargetNames.joinToString()}")
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(240.dp)
        ) {
            Scene(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                engine = engine,
                modelLoader = modelLoader,
                environmentLoader = environmentLoader,
                cameraNode = cameraNode,
                childNodes = listOfNotNull(bodyNode),
                onFrame = {
                    val cache = morphCache ?: return@Scene
                    val fatWeight = mapBodyFatToMorphWeight(bodyFatPercent)
                    val muscleTargets = computeMuscleMorphTargets(scores)

                    // Only skip if *only* fat changed a tiny amount and no muscles are active
                    if (abs(fatWeight - lastFat01) < 0.002f && muscleTargets.isEmpty()) return@Scene
                    lastFat01 = fatWeight

                    applyBodyMorphs(cache, fatWeight, muscleTargets)
                }
            )
        }
    }
}

private object MorphNames {
    const val FAT_GLOBAL = "fat_global"

    // Per‑group muscle morphs – must match the GLB shape key names
    const val MUSCLE_GLOBAL = "muscle_global"
    const val MUSCLE_CHEST = "muscle_chest"
    const val MUSCLE_BACK = "muscle_back"
    const val MUSCLE_SHOULDERS = "muscle_shoulders"
    const val MUSCLE_ARMS = "muscle_arms"
    const val MUSCLE_LEGS = "muscle_legs"
    const val MUSCLE_CORE = "muscle_core"
}

private data class MorphCache(val renderables: List<RenderableMorphInfo>)

private data class RenderableMorphInfo(
    val renderable: ModelNode.RenderableNode,
    val nameToIndex: Map<String, Int>,
    val targetCount: Int,
    val names: List<String>,
    val weights: FloatArray // cached to avoid allocations every frame
)

private fun buildMorphCache(modelNode: ModelNode): MorphCache {
    val infos = modelNode.renderableNodes.map { renderable ->
        val names = renderable.morphTargetNames
        val map = names.mapIndexed { idx, n -> n to idx }.toMap()
        RenderableMorphInfo(
            renderable = renderable,
            nameToIndex = map,
            targetCount = names.size,
            names = names,
            weights = FloatArray(names.size) { 0f }
        )
    }
    return MorphCache(infos)
}

private var logged = false
private fun logMorphInfoOnce(cache: MorphCache) {
    if (logged) return
    logged = true
    Log.d("MORPH", "===== MORPH DEBUG =====")
    Log.d("MORPH", "renderables=${cache.renderables.size}")
    cache.renderables.forEachIndexed { i, info ->
        Log.d("MORPH", "Renderable[$i] targetCount=${info.targetCount}")
        Log.d("MORPH", "names=${info.names.joinToString()}")
    }
    Log.d("MORPH", "=======================")
}

/**
 * Map 5–30% body fat into a morph weight.
 * We deliberately allow values >> 1 here to strongly exaggerate visual difference.
 */
private fun mapBodyFatToMorphWeight(bodyFatPercent: Float): Float {
    val clamped = bodyFatPercent.coerceIn(5f, 30f)
    val n = clamp01((clamped - 5f) / 25f)          // 0..1 (5% -> 0, 30% -> 1)
    val curved = n * n * n                         // strongly emphasize higher values
    val base = -0.2f                               // allow a bit below 0 at very low fat
    val scale = 10.0f                              // up to ~10x morph weight at max fat
    return base + curved * scale
}

/**
 * Convert app-level group scores (0–100) into morph target weights.
 * We deliberately allow weights > 1 to exaggerate the effect vs. Blender.
 * The keys here are the names of morph targets in the GLB file.
 */
private fun computeMuscleMorphTargets(scores: Map<String, Int>): Map<String, Float> {
    if (scores.isEmpty()) return emptyMap()

    fun scoreWeight(key: String): Float {
        val raw = (scores[key] ?: 0).coerceIn(0, 100) / 100f // 0..1
        val curved = raw * raw                               // push differences to high end
        val scale = 2.5f                                     // up to ~2.5x morph weight
        return curved * scale
    }

    val chest = scoreWeight(MuscleGroup.CHEST.id)
    val back = scoreWeight(MuscleGroup.BACK.id)
    val core = scoreWeight(MuscleGroup.CORE.id)
    val shoulders = scoreWeight(MuscleGroup.SHOULDERS.id)
    val arms = scoreWeight(MuscleGroup.ARMS.id)
    val legs = scoreWeight(MuscleGroup.LEGS.id)

    // Global muscle morph as a simple average of all groups
    val global =
        listOf(chest, back, core, shoulders, arms, legs).average().toFloat()

    return mapOf(
        MorphNames.MUSCLE_GLOBAL to global,
        MorphNames.MUSCLE_CHEST to chest,
        MorphNames.MUSCLE_BACK to back,
        MorphNames.MUSCLE_CORE to core,
        MorphNames.MUSCLE_SHOULDERS to shoulders,
        MorphNames.MUSCLE_ARMS to arms,
        MorphNames.MUSCLE_LEGS to legs
    )
}

private fun applyBodyMorphs(
    cache: MorphCache,
    fat01: Float,
    muscleTargets: Map<String, Float>
) {
    for (info in cache.renderables) {
        if (info.targetCount <= 0) continue

        // reset cached weights (cheap loop, no allocations)
        val w = info.weights
        for (i in w.indices) w[i] = 0f

        // Fat morph
        info.nameToIndex[MorphNames.FAT_GLOBAL]?.let { idx ->
            w[idx] = fat01
        } ?: Log.d("MORPH", "fat_global NOT FOUND in this renderable")

        // Per‑group muscle morphs (can exceed 1.0 to exaggerate)
        for ((name, value) in muscleTargets) {
            info.nameToIndex[name]?.let { idx ->
                w[idx] = value
            }
        }

        info.renderable.setMorphWeights(w, offset = 0)
    }
}

private fun clamp01(v: Float): Float = max(0f, min(1f, v))
