package com.goofyapps.gymnotes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.sceneview.Scene
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberModelLoader
import kotlin.math.max
import kotlin.math.min

@Composable
fun BodyScoreFigure3D(
    scores: Map<String, Int>,
    bodyFatPercent: Float = 20f // 0..50 (0->0, 50->1)
) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)

    val cameraNode = rememberCameraNode(engine).apply {
        position = Position(x = 0.0f, y = 1.05f, z = 2.35f)
        lookAt(Position(x = 0.0f, y = 1.0f, z = 0.0f))
    }

    var bodyNode by remember { mutableStateOf<ModelNode?>(null) }
    var morphCache by remember { mutableStateOf<MorphCache?>(null) }
    val smoothedWeights = remember { mutableMapOf<String, Float>() }

    LaunchedEffect(Unit) {
        val instance = modelLoader.createModelInstance("models/body_morph.glb")
        val node = ModelNode(modelInstance = instance).apply {
            position = Position(x = 0.0f, y = -0.35f, z = 0.0f)
            rotation = Rotation(x = 0.0f, y = 0.0f, z = 0.0f)
            scale = Scale(0.0042f)
        }
        bodyNode = node
        morphCache = buildMorphCache(node)
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

                // Keep this empty to avoid K2 crash from invalid casts.
                onViewCreated = { /* no-op */ },

                onFrame = {
                    val cache = morphCache ?: return@Scene
                    applyMorphs(
                        cache = cache,
                        scores = scores,
                        bodyFatPercent = bodyFatPercent,
                        smoothedWeights = smoothedWeights
                    )
                }
            )
        }
    }
}

/** Blender morph names (must match exactly) */
private object MorphNames {
    const val FAT_GLOBAL = "fat_global"
    const val MUSCLE_GLOBAL = "muscle_global"
    const val MUSCLE_CHEST = "muscle_chest"
    const val MUSCLE_BACK = "muscle_back"
    const val MUSCLE_SHOULDERS = "muscle_shoulders"
    const val MUSCLE_ARMS = "muscle_arms"
    const val MUSCLE_LEGS = "muscle_legs"
    const val MUSCLE_CORE = "muscle_core"
}

private val MORPH_NAME_BY_GROUP: Map<String, String> = mapOf(
    "chest" to MorphNames.MUSCLE_CHEST,
    "back" to MorphNames.MUSCLE_BACK,
    "shoulders" to MorphNames.MUSCLE_SHOULDERS,
    "arms" to MorphNames.MUSCLE_ARMS,
    "legs" to MorphNames.MUSCLE_LEGS,
    "core" to MorphNames.MUSCLE_CORE
)

private data class MorphCache(val renderables: List<RenderableMorphInfo>)

private data class RenderableMorphInfo(
    val renderable: ModelNode.RenderableNode,
    val nameToIndex: Map<String, Int>,
    val targetCount: Int
)

private fun buildMorphCache(modelNode: ModelNode): MorphCache {
    val infos = modelNode.renderableNodes.map { renderable ->
        val names = renderable.morphTargetNames
        val map = names.mapIndexed { idx, n -> n to idx }.toMap()
        RenderableMorphInfo(renderable, map, names.size)
    }
    return MorphCache(infos)
}

private fun applyMorphs(
    cache: MorphCache,
    scores: Map<String, Int>,
    bodyFatPercent: Float, // 0..50
    smoothedWeights: MutableMap<String, Float>
) {
    val desired = mutableMapOf<String, Float>()

    // per-muscle groups
    for ((groupId, morphName) in MORPH_NAME_BY_GROUP) {
        desired[morphName] = clamp01((scores[groupId] ?: 0) / 100f)
    }

    // muscle_global = avg training score
    val muscleAvg =
        if (scores.isEmpty()) 0f
        else scores.values.sum().toFloat() / (scores.size * 100f)
    desired[MorphNames.MUSCLE_GLOBAL] = clamp01(muscleAvg)

    // fat_global = body fat % mapping: 0%->0.0, 50%->1.0
    desired[MorphNames.FAT_GLOBAL] = clamp01(bodyFatPercent / 50f)

    // smooth
    val blend = 0.18f
    for ((morphName, target) in desired) {
        val current = smoothedWeights[morphName] ?: 0f
        smoothedWeights[morphName] = lerp(current, target, blend)
    }

    // apply
    for (info in cache.renderables) {
        if (info.targetCount <= 0) continue
        val weights = FloatArray(info.targetCount)
        for ((morphName, w) in smoothedWeights) {
            val idx = info.nameToIndex[morphName] ?: continue
            weights[idx] = clamp01(w)
        }
        info.renderable.setMorphWeights(weights, offset = 0)
    }
}

private fun clamp01(v: Float): Float = max(0f, min(1f, v))
private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * clamp01(t)
