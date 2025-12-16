package com.goofyapps.gymnotes

import android.util.Log
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
        position = Position(x = 0.0f, y = 0.0f, z = 0f)
        lookAt(Position(x = 0.0f, y = 0.0f, z = 0.0f))
    }

    var bodyNode by remember { mutableStateOf<ModelNode?>(null) }
    var morphCache by remember { mutableStateOf<MorphCache?>(null) }

    LaunchedEffect(Unit) {
        val instance = modelLoader.createModelInstance("models/body_morph17.glb")
        val node = ModelNode(modelInstance = instance).apply {
            position = Position(x = 0.0f, y = -0.0f, z = -0.0f)
            rotation = Rotation(x = 0.0f, y = 0.0f, z = 0.0f)
            scale = Scale(1f)
        }
        bodyNode = node
        morphCache = buildMorphCache(node).also { logMorphInfoOnce(it) }
        // Log the morph target names to confirm what's in the GLB
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
                onViewCreated = { /* no-op */ },
                onFrame = {
                    val cache = morphCache ?: return@Scene
                    applyFatOnly(cache, bodyFatPercent)
                }
            )
        }
    }
}

private object MorphNames {
    const val FAT_GLOBAL = "fat_global"
}

private data class MorphCache(val renderables: List<RenderableMorphInfo>)

private data class RenderableMorphInfo(
    val renderable: ModelNode.RenderableNode,
    val nameToIndex: Map<String, Int>,
    val targetCount: Int,
    val names: List<String>
)

private fun buildMorphCache(modelNode: ModelNode): MorphCache {
    val infos = modelNode.renderableNodes.map { renderable ->
        val names = renderable.morphTargetNames
        val map = names.mapIndexed { idx, n -> n to idx }.toMap()
        RenderableMorphInfo(renderable, map, names.size, names)
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

private fun applyFatOnly(
    cache: MorphCache,
    bodyFatPercent: Float
) {
    val fat01 = clamp01(bodyFatPercent / 50f)

    for (info in cache.renderables) {
        if (info.targetCount <= 0) continue

        val weights = FloatArray(info.targetCount) { 0f }

        val fatIdx = info.nameToIndex[MorphNames.FAT_GLOBAL]
        if (fatIdx != null) {
            weights[fatIdx] = fat01
        } else {
            // If this triggers, you’re not applying to the mesh that has fat_global.
            Log.d("MORPH", "fat_global NOT FOUND in this renderable")
        }

        // Apply every frame (no smoothing, no mixing)
        info.renderable.setMorphWeights(weights, offset = 0)
    }
}

private fun clamp01(v: Float): Float = max(0f, min(1f, v))
