package com.goofyapps.gymnotes

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
    bodyFatPercent: Float = 15f, // 5..50
    aiImageUri: Uri? = null, // AI generated image to show instead of 3D
    overlayContent: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    var aiBitmap by remember(aiImageUri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    
    // Load AI image if provided
    LaunchedEffect(aiImageUri) {
        aiBitmap = aiImageUri?.let { uri ->
            runCatching {
                context.contentResolver.openInputStream(uri)
                    ?.use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
        }
    }
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)

    // IMPORTANT: do NOT use rememberCameraNode() -> it can crash on dispose (NPE in destroy()).
    val cameraNode = remember(engine) { 
        CameraNode(engine).apply {
            // Set initial camera position - adjust these values to move camera
            position = Position(x = 0.0f, y = 0.0f, z = 0.0f)
            lookAt(Position(x = 0.0f, y = 0.0f, z = 0.0f))
        }
    }

    var bodyNode by remember { mutableStateOf<ModelNode?>(null) }
    var morphCache by remember { mutableStateOf<MorphCache?>(null) }

    // Used to avoid doing work every frame if value didn't change (fat only)
    var lastFat01 by remember { mutableFloatStateOf(-1f) }

    LaunchedEffect(Unit) {
        try {
            val instance = modelLoader.createModelInstance("models/body_morph25.glb")
            val node = ModelNode(modelInstance = instance).apply {
                position = Position(x = 0.0f, y = 0.0f, z = 0.0f)
                rotation = Rotation(x = 0.0f, y = 0.0f, z = 0.0f)
                scale = Scale(0.018f)
            }
            
            bodyNode = node
            morphCache = buildMorphCache(node).also { logMorphInfoOnce(it) }

            node.renderableNodes.forEachIndexed { index, renderable ->
                Log.d("MORPH", "Renderable[$index] morph targets: ${renderable.morphTargetNames.joinToString()}")
            }
        } catch (e: Exception) {
            Log.e("BodyScoreFigure3D", "Failed to load 3D model", e)
        }
    }

    // Create a nested scroll connection that blocks scroll events from propagating to parent
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Consume all scroll events to prevent parent scrolling when touching the 3D figure
                return available
            }
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
                .nestedScroll(nestedScrollConnection)
        ) {
            // Show AI image if provided and available, otherwise show 3D model
            if (aiImageUri != null && aiBitmap != null) {
                Image(
                    bitmap = aiBitmap!!.asImageBitmap(),
                    contentDescription = "AI generated muscle mass visualization",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
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

                        applyBodyMorphs(cache, fatWeight, muscleTargets, bodyFatPercent)
                    }
                )
            }
            
            // Overlay content (e.g., toggle and Goal button) in bottom right
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .padding(8.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                overlayContent()
            }
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
 * Map body fat percentage to morph weight with custom curve:
 * 10% -> 0.0, 20% -> 0.15, 30% -> 0.3, 40% -> 0.6, 50% -> 1.0
 */
private fun mapBodyFatToMorphWeight(bodyFatPercent: Float): Float {
    val clamped = bodyFatPercent.coerceIn(10f, 50f)
    
    return when {
        clamped <= 10f -> 0.0f
        clamped <= 20f -> {
            // Linear interpolation: 10% -> 0.0, 20% -> 0.15
            val t = (clamped - 10f) / 10f // 0..1
            0.0f + t * 0.15f
        }
        clamped <= 30f -> {
            // Linear interpolation: 20% -> 0.15, 30% -> 0.3
            val t = (clamped - 20f) / 10f // 0..1
            0.15f + t * (0.3f - 0.15f)
        }
        clamped <= 40f -> {
            // Linear interpolation: 30% -> 0.3, 40% -> 0.6
            val t = (clamped - 30f) / 10f // 0..1
            0.3f + t * (0.6f - 0.3f)
        }
        else -> {
            // Linear interpolation: 40% -> 0.6, 50% -> 1.0
            val t = (clamped - 40f) / 10f // 0..1
            0.6f + t * (1.0f - 0.6f)
        }
    }
}

/**
 * Convert app-level group scores (0–100) into morph target weights.
 * Moderate exaggeration to make changes more visible.
 * The keys here are the names of morph targets in the GLB file.
 */
private fun computeMuscleMorphTargets(scores: Map<String, Int>): Map<String, Float> {
    if (scores.isEmpty()) return emptyMap()

    fun scoreWeight(key: String, scale: Float = 1.5f): Float {
        val raw = (scores[key] ?: 0).coerceIn(0, 100) / 100f // 0..1
        val curved = raw * raw                               // squared curve
        return curved * scale
    }

    val chest = scoreWeight(MuscleGroup.CHEST.id)
    val back = scoreWeight(MuscleGroup.BACK.id)
    val core = scoreWeight(MuscleGroup.CORE.id)
    val shoulders = scoreWeight(MuscleGroup.SHOULDERS.id, scale = 1.0f) // More subtle for shoulders
    val arms = scoreWeight(MuscleGroup.ARMS.id, scale = 1.0f)             // More subtle for arms
    val legs = scoreWeight(MuscleGroup.LEGS.id)

    // Global muscle morph hardcoded to 0 (disabled)
    val global = 0f

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

/**
 * Calculate muscle reduction factor based on body fat percentage.
 * When body fat > 23%, muscles start reducing.
 * At max fat (50%), muscles are reduced to 25% of their original value.
 */
private fun calculateMuscleReductionFactor(bodyFatPercent: Float): Float {
    if (bodyFatPercent <= 23f) {
        return 1.0f // No reduction when body fat <= 23%
    }
    // Linear interpolation from 1.0 at 23% to 0.25 at 50%
    // Factor = 1.0 - (bodyFat - 23) / (50 - 23) * 0.75
    // Factor = 1.0 - (bodyFat - 23) / 27 * 0.75
    val reductionRange = 50f - 23f // 27
    val reductionAmount = (bodyFatPercent - 23f) / reductionRange * 0.75f // 0 to 0.75
    return 1.0f - reductionAmount // 1.0 to 0.25
}

private fun applyBodyMorphs(
    cache: MorphCache,
    fat01: Float,
    muscleTargets: Map<String, Float>,
    bodyFatPercent: Float
) {
    try {
        // Calculate muscle reduction factor based on body fat percentage
        val muscleReductionFactor = calculateMuscleReductionFactor(bodyFatPercent)
        
        for (info in cache.renderables) {
            if (info.targetCount <= 0) continue

            // reset cached weights (cheap loop, no allocations)
            val w = info.weights
            for (i in w.indices) w[i] = 0f

            // Fat morph
            info.nameToIndex[MorphNames.FAT_GLOBAL]?.let { idx ->
                if (idx < w.size) {
                    w[idx] = fat01
                }
            } ?: Log.d("MORPH", "fat_global NOT FOUND in this renderable")

            // Per‑group muscle morphs - apply reduction factor when fat > 0.5
            for ((name, value) in muscleTargets) {
                info.nameToIndex[name]?.let { idx ->
                    if (idx < w.size) {
                        w[idx] = value * muscleReductionFactor
                    }
                }
            }

            info.renderable.setMorphWeights(w, offset = 0)
        }
    } catch (e: Exception) {
        Log.e("BodyScoreFigure3D", "Error applying morphs", e)
    }
}

private fun clamp01(v: Float): Float = max(0f, min(1f, v))
