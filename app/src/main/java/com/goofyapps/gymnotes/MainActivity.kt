@file:OptIn(ExperimentalMaterial3Api::class)

package com.goofyapps.gymnotes

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Logout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.goofyapps.gymnotes.ui.theme.GymNotesTheme
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorder
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.runtime.rememberCoroutineScope
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

// -------------------- MODELS --------------------

data class WeightEntry(
    val t: Long,
    val w: Float
)

data class WorkoutDay(
    val id: Long = System.currentTimeMillis(),
    val groupId: String? = null,            // preset group id OR null if custom
    val groupCustomName: String? = null,    // used if groupId is null
    val exercises: MutableList<ExerciseCard> = mutableListOf()
)

data class ExerciseCard(
    val id: Long = System.currentTimeMillis(),
    val equipmentName: String,
    val videoUri: String?,
    val sets: Int,
    val reps: Int,
    val weight: Float?,
    val weightUnit: String = "kg",
    val primaryMuscleId: String? = null,          // preset muscle id OR null if custom
    val primaryMuscleCustomName: String? = null,  // used if primaryMuscleId is null
    val weightHistory: MutableList<WeightEntry> = mutableListOf(),
    val notes: String = ""
)

data class Profile(
    val weightKg: Float? = null,
    val heightCm: Float? = null,
    val bodyFatPct: Float? = null,
    val sex: String = "Unspecified"
) {
    fun bmi(): Float? {
        val w = weightKg ?: return null
        val hCm = heightCm ?: return null
        val hM = hCm / 100f
        if (hM <= 0f) return null
        return w / (hM * hM)
    }
}

// Schedule item can be an exercise card ID or "rest"
sealed class ScheduleItem {
    data class ExerciseCard(val cardId: Long) : ScheduleItem()
    data object Rest : ScheduleItem()
}

sealed class Screen {
    data object Workout : Screen()
    data object Progress : Screen()
    data class ProgressGroupDetail(val groupId: String) : Screen()
    data class DayDetail(val dayIndex: Int) : Screen()
    data class ExerciseDetail(val dayIndex: Int, val exerciseIndex: Int) : Screen()

    // NEW
    data object GoalMode : Screen()
    data object Gymini : Screen()
    data object Settings : Screen()
}

// -------------------- ACTIVITY --------------------

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { GymNotesTheme { AppRoot() } }
    }
}

// -------------------- PERSISTENCE --------------------

private data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

private fun serializeChatHistory(messages: List<ChatMessage>): String {
    val jsonArray = JSONArray()
    for (msg in messages) {
        val obj = JSONObject()
        obj.put("text", msg.text)
        obj.put("isUser", msg.isUser)
        obj.put("timestamp", msg.timestamp)
        jsonArray.put(obj)
    }
    return jsonArray.toString()
}

private fun parseChatHistory(json: String): List<ChatMessage> {
    return try {
        val jsonArray = JSONArray(json)
        val messages = mutableListOf<ChatMessage>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            messages.add(
                ChatMessage(
                    text = obj.getString("text"),
                    isUser = obj.getBoolean("isUser"),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                )
            )
        }
        messages
    } catch (e: Exception) {
        emptyList()
    }
}

private fun loadChatHistory(context: Context): List<ChatMessage> {
    val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_CHAT_HISTORY, null) ?: return emptyList()
    return parseChatHistory(json)
}

private fun saveChatHistory(context: Context, messages: List<ChatMessage>) {
    // Use commit() for critical user data to ensure it's saved synchronously
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_CHAT_HISTORY, serializeChatHistory(messages))
        .commit()
}

private const val PREFS_NAME = "gym_notes_prefs"
private const val KEY_DATA = "workout_data"
private const val KEY_PROFILE = "profile_json"
private const val KEY_GOALS = "goals_json"
private const val KEY_UNIT_SYSTEM = "unit_system"
private const val KEY_AI_FRONT_PHOTO = "ai_front_photo_uri"
private const val KEY_AI_BACK_PHOTO = "ai_back_photo_uri"
private const val KEY_AI_GENERATED_IMAGE = "ai_generated_image_uri"
private const val KEY_SHOW_AI_IMAGE = "show_ai_image_toggle"
private const val KEY_CHAT_HISTORY = "chat_history"
private const val KEY_ANALYSIS_RESULT = "analysis_result"
private const val KEY_SCHEDULE = "schedule"
private const val KEY_SCHEDULE_SUGGESTIONS = "schedule_suggestions"
private const val KEY_LAST_CLOUD_BACKUP = "last_cloud_backup_timestamp"

private enum class UnitSystem { METRIC, IMPERIAL }

private fun serializeDays(days: List<WorkoutDay>): String {
    val daysArray = JSONArray()
    for (day in days) {
        val dayObj = JSONObject().apply {
            put("id", day.id)
            put("groupId", day.groupId)
            put("groupCustomName", day.groupCustomName)

            val exArr = JSONArray()
            for (ex in day.exercises) {
                val exObj = JSONObject().apply {
                    put("id", ex.id)
                    put("equipmentName", ex.equipmentName)
                    put("videoUri", ex.videoUri)
                    put("sets", ex.sets)
                    put("reps", ex.reps)
                    put("weight", ex.weight?.toDouble() ?: JSONObject.NULL)
                    put("weightUnit", ex.weightUnit)
                    put("primaryMuscleId", ex.primaryMuscleId)
                    put("primaryMuscleCustomName", ex.primaryMuscleCustomName)
                    put("notes", ex.notes)

                    val histArr = JSONArray()
                    ex.weightHistory.forEach { e ->
                        histArr.put(
                            JSONObject().apply {
                                put("t", e.t)
                                put("w", e.w.toDouble())
                            }
                        )
                    }
                    put("weightHistory", histArr)
                }
                exArr.put(exObj)
            }
            put("exercises", exArr)
        }
        daysArray.put(dayObj)
    }
    return daysArray.toString()
}

private fun parseDaysFromJson(json: String): MutableList<WorkoutDay> {
    return try {
    val daysArray = JSONArray(json)
    val result = mutableListOf<WorkoutDay>()

    for (i in 0 until daysArray.length()) {
        val dayObj = daysArray.getJSONObject(i)
        val id = dayObj.optLong("id")

        val groupId = dayObj.optString("groupId").takeIf { it.isNotBlank() }
        val groupCustomName = dayObj.optString("groupCustomName").takeIf { it.isNotBlank() }

        val exArr = dayObj.optJSONArray("exercises") ?: JSONArray()
        val exercises = mutableListOf<ExerciseCard>()

        for (j in 0 until exArr.length()) {
            val exObj = exArr.getJSONObject(j)

            val primaryMuscleId = exObj.optString("primaryMuscleId").takeIf { it.isNotBlank() }
            val primaryMuscleCustomName = exObj.optString("primaryMuscleCustomName").takeIf { it.isNotBlank() }

            val histArr = exObj.optJSONArray("weightHistory")
            val histList = mutableListOf<WeightEntry>()
            if (histArr != null) {
                for (k in 0 until histArr.length()) {
                    val o = histArr.optJSONObject(k) ?: continue
                    val t = o.optLong("t", 0L)
                    val w = o.optDouble("w", Double.NaN)
                    if (t > 0L && w.isFinite()) histList.add(WeightEntry(t, w.toFloat()))
                }
            }

            val weight = if (exObj.isNull("weight")) null else exObj.getDouble("weight").toFloat()

            // Backward compat: seed history if missing but we have current weight
            if (histList.isEmpty() && weight != null) {
                histList.add(WeightEntry(System.currentTimeMillis(), weight))
            }

            exercises.add(
                ExerciseCard(
                    id = exObj.optLong("id"),
                    equipmentName = exObj.optString("equipmentName", ""),
                    videoUri = if (exObj.isNull("videoUri")) null else exObj.getString("videoUri"),
                    sets = exObj.optInt("sets", 0),
                    reps = exObj.optInt("reps", 0),
                    weight = weight,
                    weightUnit = exObj.optString("weightUnit", "kg"),
                    primaryMuscleId = primaryMuscleId,
                    primaryMuscleCustomName = primaryMuscleCustomName,
                    weightHistory = histList,
                    notes = exObj.optString("notes", "")
                )
            )
        }

        result.add(
            WorkoutDay(
                id = id,
                groupId = groupId,
                groupCustomName = groupCustomName,
                exercises = exercises
            )
        )
    }

        result
    } catch (e: Exception) {
        android.util.Log.e("MainActivity", "Error parsing workout days JSON", e)
        mutableListOf() // Return empty list on error
    }
}

private fun saveDays(context: Context, days: List<WorkoutDay>) {
    // Use commit() for critical user data to ensure it's saved synchronously
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_DATA, serializeDays(days))
        .commit()
}

private fun loadDays(context: Context): MutableList<WorkoutDay> {
    val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_DATA, null) ?: return mutableListOf()
    return parseDaysFromJson(json)
}

private fun serializeProfile(p: Profile): String =
    JSONObject().apply {
        put("weightKg", p.weightKg?.toDouble() ?: JSONObject.NULL)
        put("heightCm", p.heightCm?.toDouble() ?: JSONObject.NULL)
        put("bodyFatPct", p.bodyFatPct?.toDouble() ?: JSONObject.NULL)
        put("sex", p.sex)
    }.toString()

private fun parseProfile(json: String): Profile {
    return try {
    val o = JSONObject(json)
    val w = if (o.isNull("weightKg")) null else o.optDouble("weightKg").toFloat()
    val h = if (o.isNull("heightCm")) null else o.optDouble("heightCm").toFloat()
    val bf = if (o.isNull("bodyFatPct")) null else o.optDouble("bodyFatPct").toFloat()
    val sex = o.optString("sex", "Unspecified")
        Profile(weightKg = w, heightCm = h, bodyFatPct = bf, sex = sex)
    } catch (e: Exception) {
        android.util.Log.e("MainActivity", "Error parsing profile JSON", e)
        Profile() // Return default profile on error
    }
}

private fun loadProfile(context: Context): Profile {
    val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_PROFILE, null) ?: return Profile()
    return runCatching { parseProfile(json) }.getOrElse { Profile() }
}

private fun saveProfile(context: Context, p: Profile) {
    // Use commit() for critical user data to ensure it's saved synchronously
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_PROFILE, serializeProfile(p))
        .commit()
}

// -------------------- SETTINGS: UNITS --------------------

private fun loadUnitSystem(context: Context): UnitSystem {
    val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_UNIT_SYSTEM, "metric")
    return when (raw) {
        "imperial" -> UnitSystem.IMPERIAL
        else -> UnitSystem.METRIC
    }
}

private fun saveUnitSystem(context: Context, unitSystem: UnitSystem) {
    val value = when (unitSystem) {
        UnitSystem.METRIC -> "metric"
        UnitSystem.IMPERIAL -> "imperial"
    }
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_UNIT_SYSTEM, value)
        .apply()
}

// -------------------- GOALS --------------------

internal data class Goals(
    val bodyFatPercent: Float,
    val groupScores: Map<String, Int>
)

private fun serializeGoals(g: Goals): String =
    JSONObject().apply {
        put("bodyFatPercent", g.bodyFatPercent.toDouble())
        val groupsObj = JSONObject()
        g.groupScores.forEach { (k, v) -> groupsObj.put(k, v) }
        put("groups", groupsObj)
    }.toString()

private fun parseGoals(json: String): Goals {
    return try {
        val o = JSONObject(json)
        val bf = o.optDouble("bodyFatPercent", 20.0).toFloat()
        val groupsObj = o.optJSONObject("groups") ?: JSONObject()
        val map = mutableMapOf<String, Int>()
        val keys = groupsObj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            map[k] = groupsObj.optInt(k, 0)
        }
        Goals(bodyFatPercent = bf.coerceIn(5f, 50f), groupScores = map)
    } catch (e: Exception) {
        android.util.Log.e("MainActivity", "Error parsing goals JSON", e)
        Goals(bodyFatPercent = 20f, groupScores = emptyMap()) // Return default goals on error
    }
}

private fun loadGoals(context: Context): Goals? {
    val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_GOALS, null) ?: return null
    return runCatching { parseGoals(json) }.getOrNull()
}

private fun saveGoals(context: Context, goals: Goals) {
    // Use commit() for critical user data to ensure it's saved synchronously
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_GOALS, serializeGoals(goals))
        .commit()
}

// -------------------- SCHEDULE --------------------

// Schedule: Map of day index (0=Monday, 6=Sunday) to list of ScheduleItems
private typealias Schedule = Map<Int, List<ScheduleItem>>

private fun serializeSchedule(schedule: Schedule): String {
    val obj = JSONObject()
    schedule.forEach { (dayIndex, items) ->
        val itemsArray = JSONArray()
        items.forEach { item ->
            when (item) {
                is ScheduleItem.ExerciseCard -> {
                    itemsArray.put(JSONObject().apply {
                        put("type", "exercise")
                        put("cardId", item.cardId)
                    })
                }
                is ScheduleItem.Rest -> {
                    itemsArray.put(JSONObject().apply {
                        put("type", "rest")
                    })
                }
            }
        }
        obj.put(dayIndex.toString(), itemsArray)
    }
    return obj.toString()
}

private fun parseSchedule(json: String): Schedule {
    return try {
        val obj = JSONObject(json)
        val result = mutableMapOf<Int, List<ScheduleItem>>()
        for (i in 0..6) {
            val itemsArray = obj.optJSONArray(i.toString()) ?: JSONArray()
            val items = mutableListOf<ScheduleItem>()
            for (j in 0 until itemsArray.length()) {
                val itemObj = itemsArray.getJSONObject(j)
                when (itemObj.getString("type")) {
                    "exercise" -> items.add(ScheduleItem.ExerciseCard(itemObj.getLong("cardId")))
                    "rest" -> items.add(ScheduleItem.Rest)
                }
            }
            result[i] = items
        }
        result
    } catch (e: Exception) {
        emptyMap()
    }
}

private fun loadSchedule(context: Context): Schedule {
    val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_SCHEDULE, null) ?: return emptyMap()
    return parseSchedule(json)
}

private fun saveSchedule(context: Context, schedule: Schedule) {
    // Use commit() for critical user data to ensure it's saved synchronously
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_SCHEDULE, serializeSchedule(schedule))
        .commit()
}

// -------------------- COMPREHENSIVE EXPORT/IMPORT (STRING FORMAT) --------------------

/**
 * Export all user data to a string format including:
 * - Profile (weight, height, body fat, sex)
 * - Current muscle scores
 * - All workout days (with preset/custom flags)
 * - All exercise cards (with preset/custom flags)
 * - Schedule
 * - Email address
 */
private fun exportAllDataToString(
    days: List<WorkoutDay>,
    profile: Profile,
    schedule: Schedule,
    goals: Goals?,
    email: String?
): String {
    val root = JSONObject()
    
    // Version for future compatibility
    root.put("version", 1)
    root.put("exportTimestamp", System.currentTimeMillis())
    
    // Email
    root.put("email", email ?: "")
    
    // Profile
    root.put("profile", JSONObject().apply {
        put("weightKg", profile.weightKg?.toDouble() ?: JSONObject.NULL)
        put("heightCm", profile.heightCm?.toDouble() ?: JSONObject.NULL)
        put("bodyFatPct", profile.bodyFatPct?.toDouble() ?: JSONObject.NULL)
        put("sex", profile.sex)
    })
    
    // Current scores (computed from days and profile)
    val muscleScores = computeMuscleScores(days, profile)
    val groupScores = computeGroupScoresFromMuscles(muscleScores)
    root.put("groupScores", JSONObject().apply {
        groupScores.forEach { (k, v) -> put(k, v) }
    })
    root.put("muscleScores", JSONObject().apply {
        muscleScores.forEach { (k, v) -> put(k, v) }
    })
    
    // Goals
    if (goals != null) {
        root.put("goals", JSONObject().apply {
            put("bodyFatPercent", goals.bodyFatPercent.toDouble())
            val groupsObj = JSONObject()
            goals.groupScores.forEach { (k, v) -> groupsObj.put(k, v) }
            put("groups", groupsObj)
        })
    }
    
    // Workout days with preset/custom flags
    // IMPORTANT: This exports ALL exercise cards from ALL workout days
    val daysArray = JSONArray()
    val allCardsExported = mutableSetOf<Long>() // Track exported card IDs to ensure completeness
    for (day in days) {
        val dayObj = JSONObject().apply {
            put("id", day.id)
            // Flag: isPresetGroup = true if groupId is not null (preset), false if custom
            put("isPresetGroup", day.groupId != null)
            put("groupId", day.groupId ?: JSONObject.NULL)
            put("groupCustomName", day.groupCustomName ?: JSONObject.NULL)
            
            val exArr = JSONArray()
            // Export ALL exercise cards from this day
            for (ex in day.exercises) {
                allCardsExported.add(ex.id)
                val exObj = JSONObject().apply {
                    put("id", ex.id)
                    put("equipmentName", ex.equipmentName)
                    put("videoUri", ex.videoUri ?: JSONObject.NULL)
                    put("sets", ex.sets)
                    put("reps", ex.reps)
                    put("weight", ex.weight?.toDouble() ?: JSONObject.NULL)
                    put("weightUnit", ex.weightUnit)
                    // Flag: isPresetMuscle = true if primaryMuscleId is not null (preset), false if custom
                    put("isPresetMuscle", ex.primaryMuscleId != null)
                    put("primaryMuscleId", ex.primaryMuscleId ?: JSONObject.NULL)
                    put("primaryMuscleCustomName", ex.primaryMuscleCustomName ?: JSONObject.NULL)
                    put("notes", ex.notes)
                    
                    val histArr = JSONArray()
                    ex.weightHistory.forEach { e ->
                        histArr.put(JSONObject().apply {
                            put("t", e.t)
                            put("w", e.w.toDouble())
                        })
                    }
                    put("weightHistory", histArr)
                }
                exArr.put(exObj)
            }
            put("exercises", exArr)
        }
        daysArray.put(dayObj)
    }
    root.put("days", daysArray)
    // Log total cards exported for verification - ensures ALL cards from ALL days are included
    android.util.Log.d("Export", "Exported ${allCardsExported.size} unique exercise cards across ${days.size} workout days")
    
    // Schedule
    root.put("schedule", JSONObject().apply {
        schedule.forEach { (dayIndex, items) ->
            val itemsArray = JSONArray()
            items.forEach { item ->
                when (item) {
                    is ScheduleItem.ExerciseCard -> {
                        itemsArray.put(JSONObject().apply {
                            put("type", "exercise")
                            put("cardId", item.cardId)
                        })
                    }
                    is ScheduleItem.Rest -> {
                        itemsArray.put(JSONObject().apply {
                            put("type", "rest")
                        })
                    }
                }
            }
            put(dayIndex.toString(), itemsArray)
        }
    })
    
    return root.toString()
}

/**
 * Helper: Try to match a custom muscle group name to a preset
 */
private fun matchGroupNameToPreset(customName: String?): String? {
    if (customName.isNullOrBlank()) return null
    val normalized = customName.trim().lowercase()
    return MuscleGroup.entries.firstOrNull { 
        it.displayName.lowercase() == normalized || 
        it.id.lowercase() == normalized 
    }?.id
}

/**
 * Helper: Try to match a custom muscle name to a preset
 */
private fun matchMuscleNameToPreset(customName: String?): String? {
    if (customName.isNullOrBlank()) return null
    val normalized = customName.trim().lowercase()
    return Muscle.entries.firstOrNull { 
        it.displayName.lowercase() == normalized || 
        it.id.lowercase() == normalized 
    }?.id
}

/**
 * Import all user data from string format with preset recognition.
 * Returns ImportResult with imported data and any errors.
 */
private data class ImportResult(
    val days: MutableList<WorkoutDay>?,
    val profile: Profile?,
    val schedule: Schedule?,
    val goals: Goals?,
    val email: String?,
    val success: Boolean,
    val error: String? = null
)

private fun importAllDataFromString(jsonString: String): ImportResult {
    return try {
        val root = JSONObject(jsonString)
        val version = root.optInt("version", 1)
        
        // Email
        val email = root.optString("email").takeIf { it.isNotBlank() }
        
        // Profile
        val profileObj = root.optJSONObject("profile")
        val profile = if (profileObj != null) {
            val w = if (profileObj.isNull("weightKg")) null else profileObj.optDouble("weightKg").toFloat()
            val h = if (profileObj.isNull("heightCm")) null else profileObj.optDouble("heightCm").toFloat()
            val bf = if (profileObj.isNull("bodyFatPct")) null else profileObj.optDouble("bodyFatPct").toFloat()
            val sex = profileObj.optString("sex", "Unspecified")
            Profile(weightKg = w, heightCm = h, bodyFatPct = bf, sex = sex)
        } else {
            Profile()
        }
        
        // Goals
        val goalsObj = root.optJSONObject("goals")
        val goals = if (goalsObj != null) {
            val bf = goalsObj.optDouble("bodyFatPercent", 20.0).toFloat()
            val groupsObj = goalsObj.optJSONObject("groups") ?: JSONObject()
            val map = mutableMapOf<String, Int>()
            val keys = groupsObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = groupsObj.optInt(k, 0)
            }
            Goals(bodyFatPercent = bf.coerceIn(5f, 50f), groupScores = map)
        } else {
            null
        }
        
        // Workout days with preset recognition
        val daysArray = root.optJSONArray("days") ?: JSONArray()
        val days = mutableListOf<WorkoutDay>()
        
        for (i in 0 until daysArray.length()) {
            val dayObj = daysArray.getJSONObject(i)
            val id = dayObj.optLong("id")
            
            // Preset recognition for muscle groups
            val isPresetGroup = dayObj.optBoolean("isPresetGroup", true)
            val groupIdFromJson = dayObj.optString("groupId").takeIf { it.isNotBlank() }
            val groupCustomNameFromJson = dayObj.optString("groupCustomName").takeIf { it.isNotBlank() }
            
            val (finalGroupId, finalGroupCustomName) = when {
                // If marked as preset and has groupId, use it
                isPresetGroup && groupIdFromJson != null -> groupIdFromJson to null
                // If marked as custom, try to match to preset first
                groupCustomNameFromJson != null -> {
                    val matchedPreset = matchGroupNameToPreset(groupCustomNameFromJson)
                    if (matchedPreset != null) {
                        // Found a match - use preset
                        matchedPreset to null
                    } else {
                        // No match - keep as custom
                        null to groupCustomNameFromJson
                    }
                }
                // Fallback: try to match groupId if present
                groupIdFromJson != null -> {
                    val matched = matchGroupNameToPreset(groupIdFromJson)
                    matched to null
                }
                else -> null to null
            }
            
            val exArr = dayObj.optJSONArray("exercises") ?: JSONArray()
            val exercises = mutableListOf<ExerciseCard>()
            
            for (j in 0 until exArr.length()) {
                val exObj = exArr.getJSONObject(j)
                
                // Preset recognition for muscles
                val isPresetMuscle = exObj.optBoolean("isPresetMuscle", true)
                val muscleIdFromJson = exObj.optString("primaryMuscleId").takeIf { it.isNotBlank() }
                val muscleCustomNameFromJson = exObj.optString("primaryMuscleCustomName").takeIf { it.isNotBlank() }
                
                val (finalMuscleId, finalMuscleCustomName) = when {
                    // If marked as preset and has muscleId, use it
                    isPresetMuscle && muscleIdFromJson != null -> muscleIdFromJson to null
                    // If marked as custom, try to match to preset first
                    muscleCustomNameFromJson != null -> {
                        val matchedPreset = matchMuscleNameToPreset(muscleCustomNameFromJson)
                        if (matchedPreset != null) {
                            // Found a match - use preset
                            matchedPreset to null
                        } else {
                            // No match - keep as custom
                            null to muscleCustomNameFromJson
                        }
                    }
                    // Fallback: try to match muscleId if present
                    muscleIdFromJson != null -> {
                        val matched = matchMuscleNameToPreset(muscleIdFromJson)
                        matched to null
                    }
                    else -> null to null
                }
                
                val histArr = exObj.optJSONArray("weightHistory")
                val histList = mutableListOf<WeightEntry>()
                if (histArr != null) {
                    for (k in 0 until histArr.length()) {
                        val o = histArr.optJSONObject(k) ?: continue
                        val t = o.optLong("t", 0L)
                        val w = o.optDouble("w", Double.NaN)
                        if (t > 0L && w.isFinite()) histList.add(WeightEntry(t, w.toFloat()))
                    }
                }
                
                val weight = if (exObj.isNull("weight")) null else exObj.getDouble("weight").toFloat()
                
                // Backward compat: seed history if missing but we have current weight
                if (histList.isEmpty() && weight != null) {
                    histList.add(WeightEntry(System.currentTimeMillis(), weight))
                }
                
                exercises.add(
                    ExerciseCard(
                        id = exObj.optLong("id"),
                        equipmentName = exObj.optString("equipmentName", ""),
                        videoUri = if (exObj.isNull("videoUri")) null else exObj.getString("videoUri"),
                        sets = exObj.optInt("sets", 0),
                        reps = exObj.optInt("reps", 0),
                        weight = weight,
                        weightUnit = exObj.optString("weightUnit", "kg"),
                        primaryMuscleId = finalMuscleId,
                        primaryMuscleCustomName = finalMuscleCustomName,
                        weightHistory = histList,
                        notes = exObj.optString("notes", "")
                    )
                )
            }
            
            days.add(
                WorkoutDay(
                    id = id,
                    groupId = finalGroupId,
                    groupCustomName = finalGroupCustomName,
                    exercises = exercises
                )
            )
        }
        
        // Schedule
        val scheduleObj = root.optJSONObject("schedule")
        val schedule = if (scheduleObj != null) {
            val result = mutableMapOf<Int, List<ScheduleItem>>()
            for (i in 0..6) {
                val itemsArray = scheduleObj.optJSONArray(i.toString()) ?: JSONArray()
                val items = mutableListOf<ScheduleItem>()
                for (j in 0 until itemsArray.length()) {
                    val itemObj = itemsArray.getJSONObject(j)
                    when (itemObj.getString("type")) {
                        "exercise" -> items.add(ScheduleItem.ExerciseCard(itemObj.getLong("cardId")))
                        "rest" -> items.add(ScheduleItem.Rest)
                    }
                }
                result[i] = items
            }
            result
        } else {
            emptyMap()
        }
        
        ImportResult(
            days = days,
            profile = profile,
            schedule = schedule,
            goals = goals,
            email = email,
            success = true
        )
    } catch (e: Exception) {
        android.util.Log.e("MainActivity", "Error importing data", e)
        ImportResult(
            days = null,
            profile = null,
            schedule = null,
            goals = null,
            email = null,
            success = false,
            error = e.message ?: "Unknown error"
        )
    }
}

// -------------------- CLOUD BACKUP --------------------

private const val CLOUD_BACKUP_COLLECTION = "user_backups"
private const val MILLIS_PER_MONTH = 30L * 24L * 60L * 60L * 1000L // ~30 days

/**
 * Get last cloud backup timestamp
 */
private fun getLastCloudBackupTimestamp(context: Context): Long {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getLong(KEY_LAST_CLOUD_BACKUP, 0L)
}

/**
 * Save last cloud backup timestamp
 */
private fun saveLastCloudBackupTimestamp(context: Context, timestamp: Long) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putLong(KEY_LAST_CLOUD_BACKUP, timestamp)
        .apply()
}

/**
 * Check if monthly backup is due (30 days since last backup)
 */
private fun isMonthlyBackupDue(context: Context): Boolean {
    val lastBackup = getLastCloudBackupTimestamp(context)
    if (lastBackup == 0L) return true // Never backed up
    val now = System.currentTimeMillis()
    return (now - lastBackup) >= MILLIS_PER_MONTH
}

/**
 * Upload user data to Firestore cloud backup
 * Stores data under user's email, with monthly timestamp
 */
suspend fun uploadToCloudBackup(
    context: Context,
    email: String,
    dataString: String
): Result<String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    try {
        if (email.isBlank()) {
            return@withContext Result.failure(Exception("Email is required for cloud backup"))
        }
        
        android.util.Log.d("CloudBackup", "Starting upload for email: $email")
        android.util.Log.d("CloudBackup", "Data string length: ${dataString.length}")
        
        // Check if data is too large (Firestore has 1MB document limit)
        if (dataString.length > 900000) { // Leave some margin
            android.util.Log.w("CloudBackup", "Data size (${dataString.length} bytes) is very large, may cause issues")
        }
        
        val db = FirebaseFirestore.getInstance()
        
        // Verify Firebase Auth is working
        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
        val currentUser = auth.currentUser
        if (currentUser == null || currentUser.email != email) {
            android.util.Log.e("CloudBackup", "User not authenticated or email mismatch")
            return@withContext Result.failure(Exception("Please sign in with Google to use cloud backup"))
        }
        
        android.util.Log.d("CloudBackup", "User authenticated: ${currentUser.email}")
        
        // Test Firestore connectivity with a small write first (with short timeout)
        try {
            android.util.Log.d("CloudBackup", "Testing Firestore connectivity...")
            val testDoc = db.collection("_test").document("connectivity")
            kotlinx.coroutines.withTimeout(5000) { // 5 second test
                testDoc.set(hashMapOf("test" to System.currentTimeMillis())).await()
                testDoc.delete().await() // Clean up test document
            }
            android.util.Log.d("CloudBackup", "Firestore connectivity test passed")
        } catch (e: Exception) {
            android.util.Log.e("CloudBackup", "Firestore connectivity test failed", e)
            return@withContext Result.failure(Exception("Unable to connect to backup service. Please check your internet connection."))
        }
        
        val now = System.currentTimeMillis()
        val monthKey = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(java.util.Date(now))
        
        // Document path: user_backups/{email}/months/{yyyy-MM}
        val backupData = hashMapOf(
            "email" to email,
            "data" to dataString,
            "timestamp" to now,
            "month" to monthKey
        )
        
        // Use email as document ID (sanitized) and month as subcollection
        // Structure: user_backups/{email}/months/{yyyy-MM}
        val emailDocRef = db.collection(CLOUD_BACKUP_COLLECTION).document(email)
        val monthDocRef = emailDocRef.collection("months").document(monthKey)
        
        android.util.Log.d("CloudBackup", "Writing to Firestore: ${monthDocRef.path}")
        
        // Set with merge to update if exists - with longer timeout for actual data
        try {
            android.util.Log.d("CloudBackup", "Attempting Firestore write...")
            kotlinx.coroutines.withTimeout(25000) { // 25 second timeout for actual upload
                monthDocRef.set(backupData, SetOptions.merge()).await()
            }
            android.util.Log.d("CloudBackup", "Firestore write completed successfully")
        } catch (e: com.google.firebase.firestore.FirebaseFirestoreException) {
            android.util.Log.e("CloudBackup", "Firestore error: ${e.code} - ${e.message}")
            android.util.Log.e("CloudBackup", "Error details: ${e.cause?.message}")
            // Provide user-friendly error messages (no technical details)
            val userMessage = when (e.code) {
                com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED -> 
                    "Unable to upload backup. Please try again later."
                com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE -> 
                    "Backup service is temporarily unavailable. Please try again later."
                com.google.firebase.firestore.FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> 
                    "Upload timed out. Please check your internet connection and try again."
                else -> "Upload failed. Please try again later."
            }
            throw Exception(userMessage, e)
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            android.util.Log.e("CloudBackup", "Timeout waiting for Firestore response")
            throw Exception("Upload timed out. Please check your internet connection and try again.", e)
        } catch (e: java.util.concurrent.TimeoutException) {
            android.util.Log.e("CloudBackup", "Timeout waiting for Firestore response")
            throw Exception("Upload timed out. Please check your internet connection and try again.", e)
        } catch (e: Exception) {
            android.util.Log.e("CloudBackup", "Unexpected error during Firestore write", e)
            e.printStackTrace()
            throw e
        }
        
        // Update last backup timestamp
        saveLastCloudBackupTimestamp(context, now)
        
        android.util.Log.d("CloudBackup", "Backup completed successfully")
        Result.success("Backup uploaded successfully")
    } catch (e: Exception) {
        android.util.Log.e("CloudBackup", "Error uploading cloud backup", e)
        e.printStackTrace()
        Result.failure(e)
    }
}

/**
 * Retrieve latest cloud backup for user
 */
suspend fun retrieveLatestCloudBackup(email: String): Result<String> = 
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    try {
        if (email.isBlank()) {
            return@withContext Result.failure(Exception("Email is required"))
        }
        
        val db = FirebaseFirestore.getInstance()
        val emailDocRef = db.collection(CLOUD_BACKUP_COLLECTION).document(email)
        val monthsCollection = emailDocRef.collection("months")
        
        // Get all monthly backups, sorted by timestamp descending
        val querySnapshot = monthsCollection
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .await()
        
        if (querySnapshot.isEmpty) {
            return@withContext Result.failure(Exception("No backup found for this email"))
        }
        
        val doc = querySnapshot.documents[0]
        val data = doc.data ?: return@withContext Result.failure(Exception("Backup data is empty"))
        val dataString = data["data"] as? String ?: return@withContext Result.failure(Exception("Invalid backup format"))
        
        Result.success(dataString)
    } catch (e: Exception) {
        android.util.Log.e("MainActivity", "Error retrieving cloud backup", e)
        Result.failure(e)
    }
}

/**
 * Auto-upload backup if monthly backup is due
 */
internal suspend fun checkAndUploadMonthlyBackup(
    context: Context,
    days: List<WorkoutDay>,
    profile: Profile,
    schedule: Schedule,
    goals: Goals?,
    email: String?
) {
    if (email.isNullOrBlank()) {
        android.util.Log.d("MainActivity", "No email - skipping cloud backup")
        return
    }
    
    if (!isMonthlyBackupDue(context)) {
        android.util.Log.d("MainActivity", "Monthly backup not due yet")
        return
    }
    
    try {
        val dataString = exportAllDataToString(days, profile, schedule, goals, email)
        val result = uploadToCloudBackup(context, email, dataString)
        result.fold(
            onSuccess = { 
                android.util.Log.d("MainActivity", "Monthly backup uploaded: $it")
            },
            onFailure = { e ->
                android.util.Log.e("MainActivity", "Failed to upload monthly backup", e)
            }
        )
    } catch (e: Exception) {
        android.util.Log.e("MainActivity", "Error in monthly backup check", e)
    }
}

// -------------------- VIDEO --------------------

private fun loadVideoThumbnail(context: Context, uri: Uri): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        retriever.getFrameAtTime(0)
    } catch (_: Exception) {
        null
    } finally {
        retriever.release()
    }
}

private fun openVideo(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "video/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(intent)
}

@Composable
private fun VideoThumbnail(uriString: String, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var bitmap by remember(uriString) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(uriString) {
        bitmap = runCatching { loadVideoThumbnail(context, Uri.parse(uriString)) }.getOrNull()
    }

    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "Exercise video thumbnail",
            modifier = modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(12.dp))
        )
    }
}

// -------------------- ROOT --------------------

@Composable
private fun AppRoot() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val days = remember { mutableStateListOf<WorkoutDay>().apply { addAll(loadDays(context)) } }
    var profile by remember { mutableStateOf(loadProfile(context)) }
    var goals by remember { mutableStateOf(loadGoals(context)) }
    var unitSystem by remember { mutableStateOf(loadUnitSystem(context)) }

    var screen: Screen by remember { mutableStateOf(Screen.Workout) }
    var schedule by remember { mutableStateOf(loadSchedule(context)) }
    
    // Monthly backup check on app start
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        val auth = FirebaseAuth.getInstance()
        val userEmail = auth.currentUser?.email
        coroutineScope.launch {
            checkAndUploadMonthlyBackup(context, days, profile, schedule, goals, userEmail)
        }
    }
    
    // Update schedule when it changes
    fun updateSchedule(newSchedule: Schedule) {
        schedule = newSchedule
        saveSchedule(context, newSchedule)
    }

    var showProfile by remember { mutableStateOf(false) }
    var showUnits by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    fun topBar(title: String? = null, showBack: Boolean = false, onBack: (() -> Unit)? = null): @Composable () -> Unit = {
        TopAppBar(
            title = { if (title != null) Text(title) else Spacer(Modifier) },
            navigationIcon = {
                if (showBack && onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            },
            actions = {
                IconButton(onClick = { screen = Screen.Settings }) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                }
            }
        )
    }

    val bottomBar: @Composable () -> Unit = {
        NavigationBar {
            NavigationBarItem(
                selected = screen is Screen.Workout || screen is Screen.DayDetail || screen is Screen.ExerciseDetail,
                onClick = { screen = Screen.Workout },
                icon = { Icon(Icons.Filled.FitnessCenter, contentDescription = "Workout") },
                label = { Text("Workout") }
            )
            NavigationBarItem(
                selected = screen is Screen.Progress || screen is Screen.ProgressGroupDetail || screen is Screen.GoalMode,
                onClick = { screen = Screen.Progress },
                icon = { Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = "Progress") },
                label = { Text("Progress") }
            )
            NavigationBarItem(
                selected = screen is Screen.Gymini,
                onClick = { screen = Screen.Gymini },
                icon = { Icon(Icons.Filled.AutoAwesome, contentDescription = "Gymini") },
                label = { Text("Gymini") }
            )
            NavigationBarItem(
                selected = screen is Screen.Settings,
                onClick = { screen = Screen.Settings },
                icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings") },
                label = { Text("Settings") }
            )
        }
    }

    Scaffold(
        topBar = {
            when (val s = screen) {
                is Screen.Workout -> { /* No top bar for main pages */ }
                is Screen.Progress -> { /* No top bar for main pages */ }
                is Screen.Gymini -> { /* No top bar for main pages */ }
                is Screen.GoalMode -> topBar("Goal Mode", showBack = true) { screen = Screen.Progress }.invoke()
                is Screen.ProgressGroupDetail -> {
                    val name = MuscleGroup.entries.firstOrNull { it.id == s.groupId }?.displayName ?: "Group"
                    topBar(name, showBack = true) { screen = Screen.Progress }.invoke()
                }
                is Screen.DayDetail -> topBar(displayGroupName(days.getOrNull(s.dayIndex)), showBack = true) {
                    screen = Screen.Workout
                }.invoke()
                is Screen.ExerciseDetail -> topBar("Edit Exercise", showBack = true) {
                    screen = Screen.DayDetail(s.dayIndex)
                }.invoke()
                is Screen.Settings -> { /* No top bar for Settings - it's a main tab */ }
            }
        },
        bottomBar = bottomBar
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = screen) {
                is Screen.Workout -> WorkoutScreen(
                    days = days,
                    unitSystem = unitSystem,
                    onOpenDay = { idx -> screen = Screen.DayDetail(idx) },
                    onSave = { 
                        saveDays(context, days)
                        // Trigger monthly backup check after saving
                        coroutineScope.launch {
                            val auth = FirebaseAuth.getInstance()
                            val userEmail = auth.currentUser?.email
                            checkAndUploadMonthlyBackup(context, days, profile, schedule, goals, userEmail)
                        }
                    }
                )

                is Screen.Progress -> ProgressScreen(
                    days = days,
                    profile = profile,
                    goals = goals,
                    unitSystem = unitSystem,
                    onOpenGroup = { gid -> screen = Screen.ProgressGroupDetail(gid) },
                    onOpenGoalMode = { screen = Screen.GoalMode }
                )
                
                is Screen.Gymini -> AIScreen(
                    days = days,
                    profile = profile,
                    goals = goals,
                    onBack = { screen = Screen.Progress }
                )

                is Screen.GoalMode -> GoalModeScreen(
                    days = days,
                    profile = profile,
                    goals = goals,
                    onGoalsChanged = { g ->
                        goals = g
                        saveGoals(context, g)
                    }
                )

                is Screen.Settings -> {
                    val auth = FirebaseAuth.getInstance()
                    SettingsScreen(
                        profile = profile,
                        unitSystem = unitSystem,
                        auth = auth,
                        days = days,
                        schedule = schedule,
                        goals = goals,
                        onProfileClick = { showProfile = true },
                        onUnitsClick = { showUnits = true },
                        onClearData = { showClearConfirm = true },
                        onScheduleChanged = { newSchedule ->
                            updateSchedule(newSchedule)
                        },
                        onProfileChanged = { newProfile ->
                            profile = newProfile
                            saveProfile(context, newProfile)
                        },
                        onGoalsChanged = { newGoals ->
                            goals = newGoals
                            if (newGoals != null) {
                                saveGoals(context, newGoals)
                            }
                        }
                    )
                }

                is Screen.ProgressGroupDetail -> ProgressGroupDetailScreen(
                    groupId = s.groupId,
                    days = days,
                    profile = profile
                )

                is Screen.DayDetail -> {
                    val idx = s.dayIndex
                    if (idx !in days.indices) {
                        screen = Screen.Workout
                    } else {
                        DayDetailScreen(
                            day = days[idx],
                            unitSystem = unitSystem,
                            onAddExercise = { ex ->
                                days[idx] = days[idx].copy(exercises = (days[idx].exercises + ex).toMutableList())
                                saveDays(context, days)
                            },
                            onDeleteExercise = { exIndex ->
                                val list = days[idx].exercises.toMutableList()
                                if (exIndex in list.indices) list.removeAt(exIndex)
                                days[idx] = days[idx].copy(exercises = list)
                                saveDays(context, days)
                            },
                            onOpenExercise = { exIndex ->
                                screen = Screen.ExerciseDetail(idx, exIndex)
                            },
                            onReorderExercises = { fromIndex, toIndex ->
                                val list = days[idx].exercises.toMutableList()
                                val item = list.removeAt(fromIndex)
                                list.add(toIndex, item)
                                days[idx] = days[idx].copy(exercises = list)
                            },
                            onSaveOrder = { saveDays(context, days) }
                        )
                    }
                }

                is Screen.ExerciseDetail -> {
                    val d = s.dayIndex
                    val e = s.exerciseIndex
                    if (d !in days.indices || e !in days[d].exercises.indices) {
                        screen = Screen.Workout
                    } else {
                        ExerciseDetailScreen(
                            parentGroupId = days[d].groupId,
                            exercise = days[d].exercises[e],
                            unitSystem = unitSystem,
                            onSave = { updated ->
                                val list = days[d].exercises.toMutableList()
                                list[e] = updated
                                days[d] = days[d].copy(exercises = list)
                                saveDays(context, days)
                                screen = Screen.DayDetail(d)
                            },
                            onPlayVideo = { uriString ->
                                runCatching { openVideo(context, Uri.parse(uriString)) }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showProfile) {
        ProfileDialog(
            initial = profile,
            unitSystem = unitSystem,
            onDismiss = { showProfile = false },
            onSave = { p ->
                profile = p
                saveProfile(context, p)
                showProfile = false
            }
        )
    }

    if (showUnits) {
        UnitsDialog(
            current = unitSystem,
            onDismiss = { showUnits = false },
            onSelect = { selected ->
                unitSystem = selected
                saveUnitSystem(context, selected)
                showUnits = false
            }
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear all data?") },
            text = { Text("This will delete all days and exercises. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    days.clear()
                    saveDays(context, days)
                    showClearConfirm = false
                    screen = Screen.Workout
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") } }
        )
    }
}

// -------------------- PROFILE --------------------

@Composable
private fun ProfileDialog(
    initial: Profile,
    unitSystem: UnitSystem,
    onDismiss: () -> Unit,
    onSave: (Profile) -> Unit
) {
    val initialWeightDisplay = remember(initial, unitSystem) {
        val w = initial.weightKg
        if (w == null) "" else {
            when (unitSystem) {
                UnitSystem.METRIC -> String.format("%.1f", w)
                UnitSystem.IMPERIAL -> String.format("%.1f", w / LB_TO_KG)
            }
        }
    }
    val initialHeightDisplay = remember(initial, unitSystem) {
        val h = initial.heightCm
        if (h == null) "" else {
            when (unitSystem) {
                UnitSystem.METRIC -> String.format("%.1f", h)
                UnitSystem.IMPERIAL -> String.format("%.2f", h / 30.48f) // feet (decimal)
            }
        }
    }

    var weightText by remember { mutableStateOf(initialWeightDisplay) }
    var heightText by remember { mutableStateOf(initialHeightDisplay) }
    var bodyFatText by remember { mutableStateOf(initial.bodyFatPct?.toString() ?: "") }

    val sexOptions = listOf("Unspecified", "Male", "Female")
    var sexExpanded by remember { mutableStateOf(false) }
    var sex by remember { mutableStateOf(initial.sex.takeIf { it.isNotBlank() } ?: "Unspecified") }

        val wDisplay = weightText.toFloatOrNull()
        val hDisplay = heightText.toFloatOrNull()
        val w = when {
            wDisplay == null -> null
            unitSystem == UnitSystem.METRIC -> wDisplay
            else -> wDisplay * LB_TO_KG
        }
        val h = when {
            hDisplay == null -> null
            unitSystem == UnitSystem.METRIC -> hDisplay
            else -> hDisplay * 30.48f // feet -> cm
        }
    val bf = bodyFatText.toFloatOrNull()
    val bmi = Profile(weightKg = w, heightCm = h, bodyFatPct = bf, sex = sex).bmi()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val weightLabel = when (unitSystem) {
                    UnitSystem.METRIC -> "Bodyweight (kg)"
                    UnitSystem.IMPERIAL -> "Bodyweight (lb)"
                }
                OutlinedTextField(
                    value = weightText,
                    onValueChange = { v -> if (v.all { it.isDigit() || it == '.' }) weightText = v },
                    label = { Text(weightLabel) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                val heightLabel = when (unitSystem) {
                    UnitSystem.METRIC -> "Height (cm)"
                    UnitSystem.IMPERIAL -> "Height (ft)"
                }
                OutlinedTextField(
                    value = heightText,
                    onValueChange = { v -> if (v.all { it.isDigit() || it == '.' }) heightText = v },
                    label = { Text(heightLabel) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = bodyFatText,
                    onValueChange = { v -> if (v.all { it.isDigit() || it == '.' }) bodyFatText = v },
                    label = { Text("Body fat % (optional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedButton(onClick = { sexExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Sex: $sex")
                }
                DropdownMenu(expanded = sexExpanded, onDismissRequest = { sexExpanded = false }) {
                    sexOptions.forEach { opt ->
                        DropdownMenuItem(
                            text = { Text(opt) },
                            onClick = {
                                sex = opt
                                sexExpanded = false
                            }
                        )
                    }
                }

                Text(
                    text = "BMI: " + (bmi?.let { String.format("%.1f", it) } ?: "—"),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(Profile(weightKg = w, heightCm = h, bodyFatPct = bf, sex = sex))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun UnitsDialog(
    current: UnitSystem,
    onDismiss: () -> Unit,
    onSelect: (UnitSystem) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Units") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Choose how to enter and show bodyweight and exercise weights.")
            }
        },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { onSelect(UnitSystem.METRIC) }) {
                    Text("Metric (kg, cm)")
                }
                TextButton(onClick = { onSelect(UnitSystem.IMPERIAL) }) {
                    Text("Imperial (lb, ft)")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

// -------------------- WORKOUT HOME --------------------

@Composable
private fun WorkoutScreen(
    days: MutableList<WorkoutDay>,
    unitSystem: UnitSystem,
    onOpenDay: (Int) -> Unit,
    onSave: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Muscles") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Schedule") }
            )
        }
        
        when (selectedTab) {
            0 -> MusclesTab(
                days = days,
                unitSystem = unitSystem,
                onOpenDay = onOpenDay,
                onSave = onSave
            )
            1 -> ScheduleTab(
                days = days,
                onSave = onSave
            )
        }
    }
}

@Composable
private fun MusclesTab(
    days: MutableList<WorkoutDay>,
    unitSystem: UnitSystem,
    onOpenDay: (Int) -> Unit,
    onSave: () -> Unit
) {
    var showAddDay by remember { mutableStateOf(false) }
    var reorderDirty by remember { mutableStateOf(false) }

    val reorderState = rememberReorderableLazyListState(
        onMove = { from, to ->
            days.add(to.index, days.removeAt(from.index))
            reorderDirty = true
        },
        onDragEnd = { _, _ ->
            if (reorderDirty) {
                onSave()
                reorderDirty = false
            }
        }
    )

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDay = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add")
            }
        }
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (days.isEmpty()) {
                Text("No groups yet. Tap + to add one.", style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn(
                    state = reorderState.listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .reorderable(reorderState),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(days, key = { _, d -> d.id }) { idx, day ->
                        ReorderableItem(reorderState, key = day.id) { _ ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .detectReorder(reorderState)
                                    .clickable { onOpenDay(idx) },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(displayGroupName(day), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("Exercises: ${day.exercises.size}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    IconButton(onClick = {
                                        days.removeAt(idx)
                                        onSave()
                                    }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDay) {
        AddDayDialog(
            onDismiss = { showAddDay = false },
            onAdd = { groupId, custom ->
                days.add(WorkoutDay(groupId = groupId, groupCustomName = custom))
                onSave()
                showAddDay = false
            }
        )
    }
}

@Composable
private fun ScheduleTab(
    days: MutableList<WorkoutDay>,
    onSave: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var schedule by remember { mutableStateOf(loadSchedule(context)) }
    var selectedDayIndex by remember { mutableStateOf(0) } // 0=Monday, 6=Sunday
    var showAddDialog by remember { mutableStateOf(false) }
    var showMuscleGroups by remember { mutableStateOf(false) }
    var selectedMuscleGroup: MuscleGroup? by remember { mutableStateOf(null) }
    
    val dayNamesShort = listOf("M", "T", "W", "T", "F", "S", "S")
    val dayNamesFull = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    val selectedDayItems = schedule[selectedDayIndex] ?: emptyList()
    
    // Get all exercise cards from all workout days
    val allExerciseCards = remember(days) {
        days.flatMap { day -> day.exercises }
    }
    
    fun updateSchedule(newSchedule: Schedule) {
        schedule = newSchedule
        saveSchedule(context, newSchedule)
    }
    
    fun addItemToDay(item: ScheduleItem) {
        val currentItems = schedule[selectedDayIndex] ?: emptyList()
        val newItems = currentItems + item
        val newSchedule = schedule.toMutableMap().apply {
            put(selectedDayIndex, newItems)
        }
        updateSchedule(newSchedule)
        showAddDialog = false
        showMuscleGroups = false
        selectedMuscleGroup = null
    }
    
    fun removeItemFromDay(index: Int) {
        val currentItems = schedule[selectedDayIndex] ?: emptyList()
        val newItems = currentItems.toMutableList().apply { removeAt(index) }
        val newSchedule = schedule.toMutableMap().apply {
            put(selectedDayIndex, newItems)
        }
        updateSchedule(newSchedule)
    }
    
    // Get exercise card by ID
    fun getExerciseCard(cardId: Long): ExerciseCard? {
        return allExerciseCards.find { it.id == cardId }
    }
    
    Column(
        Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 7 days on top - selected day shows full name, others show first letter
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            dayNamesShort.forEachIndexed { index, shortName ->
                val isSelected = selectedDayIndex == index
                FilledTonalButton(
                    onClick = { 
                        selectedDayIndex = index
                    },
                    modifier = Modifier
                        .weight(if (isSelected) 2.5f else 1f)
                        .then(if (!isSelected) Modifier.widthIn(min = 32.dp) else Modifier),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (isSelected) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (isSelected)
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = if (isSelected) ButtonDefaults.ContentPadding else PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = if (isSelected) dayNamesFull[index] else shortName,
                        style = if (isSelected) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
        
        // Cards for selected day
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (selectedDayItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No workouts scheduled for ${dayNamesFull[selectedDayIndex]}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.alpha(0.7f)
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(selectedDayItems) { index, item ->
                        val exerciseCard = when (item) {
                            is ScheduleItem.ExerciseCard -> getExerciseCard(item.cardId)
                            is ScheduleItem.Rest -> null
                        }
                        
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    if (item is ScheduleItem.Rest) {
                                        Text(
                                            "Rest Day",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            "Take a break and recover",
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.alpha(0.7f)
                                        )
                                    } else {
                                        exerciseCard?.let { card ->
                                            Text(
                                                card.equipmentName,
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                            Text(
                                                "${card.sets} sets × ${card.reps} reps${if (card.weight != null) " @ ${card.weight}${card.weightUnit}" else ""}",
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.alpha(0.7f)
                                            )
                                        } ?: Text(
                                            "Exercise not found",
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.alpha(0.7f)
                                        )
                                    }
                                }
                                
                                IconButton(
                                    onClick = { removeItemFromDay(index) }
                                ) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "Delete"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Add button
        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier.align(Alignment.End)
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add")
        }
    }
    
    // Add dialog: muscle groups or rest
    if (showAddDialog && !showMuscleGroups) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add to Schedule") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Choose what to add to ${dayNamesFull[selectedDayIndex]}:")
                    OutlinedButton(
                        onClick = { 
                            showMuscleGroups = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Exercise Card")
                    }
                    OutlinedButton(
                        onClick = { 
                            addItemToDay(ScheduleItem.Rest)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Rest Day")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAddDialog = false }) { Text("Cancel") } }
        )
    }
    
    // Muscle groups selection
    if (showMuscleGroups && selectedMuscleGroup == null) {
        AlertDialog(
            onDismissRequest = { 
                showMuscleGroups = false
                showAddDialog = false
            },
            title = { Text("Select Muscle Group") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    MuscleGroup.entries.forEach { group ->
                        OutlinedButton(
                            onClick = { selectedMuscleGroup = group },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(group.displayName)
                        }
                    }
                }
            },
            confirmButton = { 
                TextButton(onClick = { 
                    showMuscleGroups = false
                    showAddDialog = false
                }) { Text("Cancel") } 
            }
        )
    }
    
    // Exercise cards in selected muscle group
    if (selectedMuscleGroup != null) {
        // Get all exercise cards that have this muscle group as primary muscle
        val selectedGroup = selectedMuscleGroup // Safe local copy
        val groupExerciseCards = if (selectedGroup != null) {
            allExerciseCards.filter { card ->
                card.primaryMuscleId?.let { muscleId ->
                    // Check if the muscle belongs to the selected group
                    Muscle.entries.firstOrNull { it.id == muscleId }?.groupId == selectedGroup.id
                } ?: false
            }
        } else {
            emptyList()
        }
        
        if (selectedGroup == null) return // Early return if null
        
        AlertDialog(
            onDismissRequest = { 
                selectedMuscleGroup = null
                showMuscleGroups = false
                showAddDialog = false
            },
            title = { Text("Select Exercise Card") },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (groupExerciseCards.isEmpty()) {
                        item {
                            Text("No exercise cards found for ${selectedGroup.displayName}")
                        }
                    } else {
                        items(groupExerciseCards) { card ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { 
                                        addItemToDay(ScheduleItem.ExerciseCard(card.id))
                                    },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        card.equipmentName,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        "${card.sets} sets × ${card.reps} reps${if (card.weight != null) " @ ${card.weight}${card.weightUnit}" else ""}",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.alpha(0.7f)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { 
                TextButton(onClick = { 
                    selectedMuscleGroup = null
                    showMuscleGroups = false
                    showAddDialog = false
                }) { Text("Cancel") } 
            }
        )
    }
}

@Composable
private fun AddDayDialog(
    onDismiss: () -> Unit,
    onAdd: (groupId: String?, customName: String?) -> Unit
) {
    var useCustom by remember { mutableStateOf(false) }
    var customName by remember { mutableStateOf("") }
    var groupExpanded by remember { mutableStateOf(false) }
    var selectedGroup: MuscleGroup? by remember { mutableStateOf(MuscleGroup.CHEST) }

    val isValid = if (useCustom) customName.isNotBlank() else selectedGroup != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Muscle Group") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { useCustom = false }, enabled = useCustom) { Text("Preset") }
                    FilledTonalButton(onClick = { useCustom = true }, enabled = !useCustom) { Text("Custom") }
                }

                if (!useCustom) {
                    OutlinedButton(onClick = { groupExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(selectedGroup?.displayName ?: "Pick group")
                    }
                    DropdownMenu(expanded = groupExpanded, onDismissRequest = { groupExpanded = false }) {
                        MuscleGroup.entries.forEach { g ->
                            DropdownMenuItem(
                                text = { Text(g.displayName) },
                                onClick = {
                                    selectedGroup = g
                                    groupExpanded = false
                                }
                            )
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = customName,
                        onValueChange = { customName = it },
                        label = { Text("Custom group name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = {
                    if (useCustom) onAdd(null, customName.trim())
                    else onAdd(selectedGroup?.id, null)
                }
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// -------------------- DAY DETAIL --------------------

@Composable
private fun DayDetailScreen(
    day: WorkoutDay,
    unitSystem: UnitSystem,
    onAddExercise: (ExerciseCard) -> Unit,
    onDeleteExercise: (Int) -> Unit,
    onOpenExercise: (Int) -> Unit,
    onReorderExercises: (fromIndex: Int, toIndex: Int) -> Unit,
    onSaveOrder: () -> Unit
) {
    var showAdd by remember { mutableStateOf(false) }
    var reorderDirty by remember { mutableStateOf(false) }

    val reorderState = rememberReorderableLazyListState(
        onMove = { from, to ->
            onReorderExercises(from.index, to.index)
            reorderDirty = true
        },
        onDragEnd = { _, _ ->
            if (reorderDirty) {
                onSaveOrder()
                reorderDirty = false
            }
        }
    )

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add")
            }
        }
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (day.exercises.isEmpty()) {
                Text("No exercises yet. Tap + to add a card.", style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn(
                    state = reorderState.listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .reorderable(reorderState),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(day.exercises, key = { _, ex -> ex.id }) { idx, ex ->
                        ReorderableItem(reorderState, key = ex.id) { _ ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .detectReorder(reorderState)
                                    .clickable { onOpenExercise(idx) },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(
                                        ex.equipmentName,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text("Muscle: ${displayPrimaryMuscle(ex)}", style = MaterialTheme.typography.bodySmall)
                                    Text("Sets x Reps: ${ex.sets} x ${ex.reps}", style = MaterialTheme.typography.bodySmall)
                                    ex.weight?.let { wKg ->
                                        val (display, unit) = when (unitSystem) {
                                            UnitSystem.METRIC -> wKg to "kg"
                                            UnitSystem.IMPERIAL -> (wKg / LB_TO_KG) to "lb"
                                        }
                                        Text(
                                            "Weight: ${String.format("%.1f", display)} $unit",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }

                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                        IconButton(onClick = { onDeleteExercise(idx) }) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddExerciseDialog(
            parentGroupId = day.groupId,
            unitSystem = unitSystem,
            onDismiss = { showAdd = false },
            onAdd = {
                onAddExercise(it)
                showAdd = false
            }
        )
    }
}

// -------------------- ADD EXERCISE --------------------

@Composable
private fun AddExerciseDialog(
    parentGroupId: String?,
    unitSystem: UnitSystem,
    onDismiss: () -> Unit,
    onAdd: (ExerciseCard) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    var equipmentName by remember { mutableStateOf("") }
    var setsText by remember { mutableStateOf("3") }
    var repsText by remember { mutableStateOf("10") }
    var weightText by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    var muscleExpanded by remember { mutableStateOf(false) }
    var selectedMuscle: Muscle? by remember { mutableStateOf(null) }
    var useCustomMuscle by remember { mutableStateOf(false) }
    var customMuscleName by remember { mutableStateOf("") }

    var videoUri by remember { mutableStateOf<Uri?>(null) }
    val videoPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                videoUri = uri
            }
        }

    val muscles = remember(parentGroupId) {
        if (parentGroupId.isNullOrBlank()) Muscle.entries
        else musclesForGroup(parentGroupId)
    }

    val isValid = remember(equipmentName, setsText, repsText, useCustomMuscle, selectedMuscle, customMuscleName) {
        val sets = setsText.toIntOrNull() ?: 0
        val reps = repsText.toIntOrNull() ?: 0
        val muscleOk = if (useCustomMuscle) customMuscleName.isNotBlank() else selectedMuscle != null
        equipmentName.isNotBlank() && sets > 0 && reps > 0 && muscleOk
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Exercise") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

                OutlinedTextField(
                    value = equipmentName,
                    onValueChange = { equipmentName = it },
                    label = { Text("Equipment Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        modifier = Modifier.weight(1f),
                        value = setsText,
                        onValueChange = { setsText = it.filter(Char::isDigit) },
                        label = { Text("Sets") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        modifier = Modifier.weight(1f),
                        value = repsText,
                        onValueChange = { repsText = it.filter(Char::isDigit) },
                        label = { Text("Reps") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                val weightLabel = when (unitSystem) {
                    UnitSystem.METRIC -> "Weight (kg)"
                    UnitSystem.IMPERIAL -> "Weight (lb)"
                }
                OutlinedTextField(
                    value = weightText,
                    onValueChange = { v -> if (v.all { it.isDigit() || it == '.' }) weightText = v },
                    label = { Text(weightLabel) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { useCustomMuscle = false }, enabled = useCustomMuscle) { Text("Pick muscle") }
                    FilledTonalButton(onClick = { useCustomMuscle = true }, enabled = !useCustomMuscle) { Text("Custom muscle") }
                }

                if (!useCustomMuscle) {
                    OutlinedButton(onClick = { muscleExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(selectedMuscle?.displayName ?: "Select primary muscle")
                    }
                    DropdownMenu(expanded = muscleExpanded, onDismissRequest = { muscleExpanded = false }) {
                        muscles.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m.displayName) },
                                onClick = {
                                    selectedMuscle = m
                                    muscleExpanded = false
                                }
                            )
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = customMuscleName,
                        onValueChange = { customMuscleName = it },
                        label = { Text("Primary muscle name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth()
                )

                Button(onClick = { videoPickerLauncher.launch(arrayOf("video/*")) }) {
                    Text(if (videoUri == null) "Pick Video" else "Change Video")
                }
                Text(if (videoUri != null) "Video selected ✔" else "No video selected", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = {
                    val sets = setsText.toIntOrNull() ?: 0
                    val reps = repsText.toIntOrNull() ?: 0
                    val weightDisplay = weightText.toFloatOrNull()
                    val weightKg = when {
                        weightDisplay == null -> null
                        unitSystem == UnitSystem.METRIC -> weightDisplay
                        else -> weightDisplay * LB_TO_KG
                    }

                    val hist = mutableListOf<WeightEntry>()
                    if (weightKg != null) {
                        hist.add(WeightEntry(System.currentTimeMillis(), weightKg))
                    }

                    onAdd(
                        ExerciseCard(
                            equipmentName = equipmentName.trim(),
                            videoUri = videoUri?.toString(),
                            sets = sets,
                            reps = reps,
                                weight = weightKg,
                                weightUnit = if (unitSystem == UnitSystem.METRIC) "kg" else "lb",
                            primaryMuscleId = if (!useCustomMuscle) selectedMuscle?.id else null,
                            primaryMuscleCustomName = if (useCustomMuscle) customMuscleName.trim() else null,
                            weightHistory = hist,
                            notes = notes.trim()
                        )
                    )
                }
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// -------------------- EXERCISE EDIT --------------------

@Composable
private fun ExerciseDetailScreen(
    parentGroupId: String?,
    exercise: ExerciseCard,
    unitSystem: UnitSystem,
    onSave: (ExerciseCard) -> Unit,
    onPlayVideo: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    var equipmentName by remember { mutableStateOf(exercise.equipmentName) }
    var setsText by remember { mutableStateOf(exercise.sets.toString()) }
    var repsText by remember { mutableStateOf(exercise.reps.toString()) }
    val initialWeightDisplay = remember(exercise, unitSystem) {
        val w = exercise.weight
        if (w == null) "" else {
            when (unitSystem) {
                UnitSystem.METRIC -> String.format("%.1f", w)
                UnitSystem.IMPERIAL -> String.format("%.1f", w / LB_TO_KG)
            }
        }
    }
    var weightText by remember { mutableStateOf(initialWeightDisplay) }
    var notes by remember { mutableStateOf(exercise.notes) }
    var videoUri by remember { mutableStateOf(exercise.videoUri) }

    val muscles = remember(parentGroupId) {
        if (parentGroupId.isNullOrBlank()) Muscle.entries else musclesForGroup(parentGroupId)
    }
    var useCustomMuscle by remember { mutableStateOf(exercise.primaryMuscleId == null) }
    var muscleExpanded by remember { mutableStateOf(false) }
    var selectedMuscle by remember { mutableStateOf(Muscle.entries.firstOrNull { it.id == exercise.primaryMuscleId }) }
    var customMuscleName by remember { mutableStateOf(exercise.primaryMuscleCustomName ?: "") }

    val videoPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                videoUri = uri.toString()
            }
        }

    val isValid = remember(equipmentName, setsText, repsText, useCustomMuscle, selectedMuscle, customMuscleName) {
        val sets = setsText.toIntOrNull() ?: 0
        val reps = repsText.toIntOrNull() ?: 0
        val muscleOk = if (useCustomMuscle) customMuscleName.isNotBlank() else selectedMuscle != null
        equipmentName.isNotBlank() && sets > 0 && reps > 0 && muscleOk
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        videoUri?.let { uri ->
            VideoThumbnail(
                uriString = uri,
                modifier = Modifier.clickable { onPlayVideo(uri) }
            )
            Spacer(Modifier.height(4.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { videoPickerLauncher.launch(arrayOf("video/*")) }) {
                Text(if (videoUri == null) "Add video" else "Change video")
            }
            if (videoUri != null) Text("Video linked", style = MaterialTheme.typography.bodySmall)
        }

        OutlinedTextField(
            value = equipmentName,
            onValueChange = { equipmentName = it },
            label = { Text("Equipment Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = setsText,
                onValueChange = { setsText = it.filter(Char::isDigit) },
                label = { Text("Sets") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = repsText,
                onValueChange = { repsText = it.filter(Char::isDigit) },
                label = { Text("Reps") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }

        val weightLabel = when (unitSystem) {
            UnitSystem.METRIC -> "Weight (kg)"
            UnitSystem.IMPERIAL -> "Weight (lb)"
        }
        OutlinedTextField(
            value = weightText,
            onValueChange = { v -> if (v.all { it.isDigit() || it == '.' }) weightText = v },
            label = { Text(weightLabel) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { useCustomMuscle = false }, enabled = useCustomMuscle) { Text("Pick muscle") }
            FilledTonalButton(onClick = { useCustomMuscle = true }, enabled = !useCustomMuscle) { Text("Custom muscle") }
        }

        if (!useCustomMuscle) {
            OutlinedButton(onClick = { muscleExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(selectedMuscle?.displayName ?: "Select primary muscle")
            }
            DropdownMenu(expanded = muscleExpanded, onDismissRequest = { muscleExpanded = false }) {
                muscles.forEach { m ->
                    DropdownMenuItem(
                        text = { Text(m.displayName) },
                        onClick = {
                            selectedMuscle = m
                            muscleExpanded = false
                        }
                    )
                }
            }
        } else {
            OutlinedTextField(
                value = customMuscleName,
                onValueChange = { customMuscleName = it },
                label = { Text("Primary muscle name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notes") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(6.dp))

        Button(
            enabled = isValid,
            onClick = {
                val sets = setsText.toIntOrNull() ?: 0
                val reps = repsText.toIntOrNull() ?: 0
                val newWeightDisplay = weightText.toFloatOrNull()
                val newWeight = when {
                    newWeightDisplay == null -> null
                    unitSystem == UnitSystem.METRIC -> newWeightDisplay
                    else -> newWeightDisplay * LB_TO_KG
                }

                val hist = exercise.weightHistory.toMutableList()
                val oldWeight = exercise.weight
                if (newWeight != null && (oldWeight == null || newWeight != oldWeight)) {
                    val last = hist.lastOrNull()
                    if (last == null || last.w != newWeight) {
                        hist.add(WeightEntry(System.currentTimeMillis(), newWeight))
                    }
                    if (hist.size > 200) hist.subList(0, hist.size - 200).clear()
                }

                onSave(
                    exercise.copy(
                        equipmentName = equipmentName.trim(),
                        sets = sets,
                        reps = reps,
                        weight = newWeight,
                        notes = notes.trim(),
                        videoUri = videoUri,
                        weightHistory = hist,
                        primaryMuscleId = if (!useCustomMuscle) selectedMuscle?.id else null,
                        primaryMuscleCustomName = if (useCustomMuscle) customMuscleName.trim() else null
                    )
                )
            }
        ) { Text("Save") }
    }
}

// -------------------- SCORING --------------------

private data class Calibration(
    val p10Kg: Float,
    val p90Kg: Float,
    val addBodyweight: Boolean = false
)

private const val LB_TO_KG = 0.45359237f
private fun mid(a: Float, b: Float): Float = (a + b) / 2f

private val CAL: Map<String, Calibration> = mapOf(
    // ---------------- Chest ----------------
    Muscle.CHEST_PEC.id      to Calibration(mid(135f, 185f) * LB_TO_KG, mid(315f, 365f) * LB_TO_KG),
    Muscle.CHEST_GENERAL.id  to Calibration(mid(135f, 185f) * LB_TO_KG, mid(315f, 365f) * LB_TO_KG),
    Muscle.UPPER_CHEST.id    to Calibration(mid(135f, 185f) * LB_TO_KG, mid(315f, 365f) * LB_TO_KG),
    Muscle.LOWER_CHEST.id    to Calibration(mid(135f, 185f) * LB_TO_KG, mid(315f, 365f) * LB_TO_KG),

    // ---------------- Back ----------------
    Muscle.UPPER_BACK.id           to Calibration(mid(115f, 135f) * LB_TO_KG, mid(400f, 450f) * LB_TO_KG),
    Muscle.LATS.id                 to Calibration(mid(100f, 120f) * LB_TO_KG, mid(260f, 290f) * LB_TO_KG),
    Muscle.LOWER_BACK_ERECTORS.id  to Calibration(mid(115f, 135f) * LB_TO_KG, mid(250f, 280f) * LB_TO_KG),

    // ---------------- Core ----------------
    Muscle.CORE_ABS_OBLIQUES.id to Calibration(mid(60f, 80f) * LB_TO_KG, mid(180f, 220f) * LB_TO_KG),
    Muscle.ABS.id               to Calibration(mid(60f, 80f) * LB_TO_KG, mid(180f, 220f) * LB_TO_KG),
    Muscle.OBLIQUES.id          to Calibration(mid(60f, 80f) * LB_TO_KG, mid(180f, 220f) * LB_TO_KG),

    // ---------------- Shoulders ----------------
    Muscle.SHOULDERS_DELTS.id to Calibration(mid(65f, 85f) * LB_TO_KG, mid(175f, 200f) * LB_TO_KG),
    Muscle.FRONT_DELTS.id     to Calibration(mid(65f, 85f) * LB_TO_KG, mid(175f, 200f) * LB_TO_KG),
    Muscle.SIDE_DELTS.id      to Calibration(mid(65f, 85f) * LB_TO_KG, mid(175f, 200f) * LB_TO_KG),
    Muscle.REAR_DELTS.id      to Calibration(mid(65f, 85f) * LB_TO_KG, mid(175f, 200f) * LB_TO_KG),

    // ---------------- Arms ----------------
    Muscle.TRICEPS.id   to Calibration(mid(135f, 155f) * LB_TO_KG, mid(275f, 300f) * LB_TO_KG),
    Muscle.BICEPS.id    to Calibration(mid(135f, 155f) * LB_TO_KG, mid(150f, 175f) * LB_TO_KG),
    Muscle.FOREARMS.id  to Calibration(mid(75f, 95f) * LB_TO_KG, mid(150f, 180f) * LB_TO_KG),

    // ---------------- Legs ----------------
    Muscle.QUADS.id       to Calibration(mid(135f, 185f) * LB_TO_KG, mid(400f, 450f) * LB_TO_KG),
    Muscle.GLUTES_HAMS.id to Calibration(mid(185f, 250f) * LB_TO_KG, mid(495f, 550f) * LB_TO_KG),
    Muscle.HAMS_ISO.id    to Calibration(mid(100f, 130f) * LB_TO_KG, mid(250f, 290f) * LB_TO_KG),
    Muscle.HAMSTRINGS.id  to Calibration(mid(100f, 130f) * LB_TO_KG, mid(250f, 290f) * LB_TO_KG),
    Muscle.GLUTES.id      to Calibration(mid(185f, 250f) * LB_TO_KG, mid(495f, 550f) * LB_TO_KG),
    Muscle.CALVES.id      to Calibration(mid(180f, 250f) * LB_TO_KG, mid(500f, 650f) * LB_TO_KG),
)

private fun cardEffectiveLoadKg(ex: ExerciseCard, profile: Profile): Float? {
    val w = ex.weight ?: return null
    val reps = ex.reps.coerceAtLeast(1)
    val sets = ex.sets.coerceAtLeast(1)

    val base = w * (1f + reps / 30f)

    val vol = sets * reps
    val volFactor = (ln(1f + vol.toFloat()) / ln(31f)).coerceIn(0.2f, 1.4f)
    var eff = base * volFactor

    val muscleId = ex.primaryMuscleId ?: return eff
    val cal = CAL[muscleId] ?: return eff
    if (cal.addBodyweight) {
        val bw = profile.weightKg ?: 80f
        eff = (bw + w) * (1f + reps / 30f) * volFactor
    }
    return eff
}

private fun scoreFromCalibration(loadKg: Float, cal: Calibration): Int {
    val x = loadKg.coerceAtLeast(0f)
    val p10 = cal.p10Kg
    val p90 = cal.p90Kg
    if (p10 <= 0f || p90 <= p10) return 0

    val s = when {
        x <= 0f -> 0f
        x < p10 -> (x / p10) * 10f
        x <= p90 -> 10f + ((x - p10) / (p90 - p10)) * 80f
        else -> {
            val cap = p90 * 1.20f
            val t = ((x - p90) / (cap - p90)).coerceIn(0f, 1f)
            90f + 10f * t
        }
    }

    return s.toInt().coerceIn(0, 100)
}

private fun computeMuscleScores(days: List<WorkoutDay>, profile: Profile): Map<String, Int> {
    val perMuscle = mutableMapOf<String, MutableList<Int>>()

    for (d in days) {
        for (ex in d.exercises) {
            val muscleId = ex.primaryMuscleId ?: continue
            val cal = CAL[muscleId] ?: continue
            val eff = cardEffectiveLoadKg(ex, profile) ?: continue

            val score = scoreFromCalibration(eff, cal)
            perMuscle.getOrPut(muscleId) { mutableListOf() }.add(score)
        }
    }

    return perMuscle.mapValues { (_, scores) ->
        if (scores.isEmpty()) 0 else (scores.sum().toFloat() / scores.size).toInt().coerceIn(0, 100)
    }
}

private fun computeGroupScoresFromMuscles(muscleScores: Map<String, Int>): Map<String, Int> {
    val out = mutableMapOf<String, Int>()
    for (g in MuscleGroup.entries) {
        val inGroup = Muscle.entries
            .filter { it.groupId == g.id }
            .mapNotNull { m -> muscleScores[m.id] }
        out[g.id] = if (inGroup.isEmpty()) 0 else (inGroup.sum().toFloat() / inGroup.size).toInt().coerceIn(0, 100)
    }
    return out
}

// -------------------- PROGRESS --------------------

@Composable
private fun ProgressScreen(
    days: List<WorkoutDay>,
    profile: Profile,
    goals: Goals?,
    unitSystem: UnitSystem,
    onOpenGroup: (String) -> Unit,
    onOpenGoalMode: () -> Unit
) {
    val muscleScores = remember(days, profile) { computeMuscleScores(days, profile) }
    val groupScores = remember(muscleScores) { computeGroupScoresFromMuscles(muscleScores) }

    // Current body fat from profile
    val bf = (profile.bodyFatPct ?: 15f).coerceIn(5f, 50f)

    // Goal overlays (if any)
    val goalBf = goals?.bodyFatPercent?.coerceIn(5f, 50f)
    val goalGroupScores = goals?.groupScores.orEmpty()

    val scroll = rememberScrollState()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val context = androidx.compose.ui.platform.LocalContext.current
        var showAIImage by remember { mutableStateOf(loadShowAIImage(context)) }
        var aiImageUri by remember { mutableStateOf<Uri?>(loadAIGeneratedImageUri(context)) }
        
        // Reload AI image URI when it might have changed (e.g., from Gymini tab)
        LaunchedEffect(Unit) {
            aiImageUri = loadAIGeneratedImageUri(context)
        }

        BodyScoreFigure3D(
            scores = groupScores,
            bodyFatPercent = bf,
            aiImageUri = if (showAIImage) aiImageUri else null,
            overlayContent = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Toggle button for AI image (only show if AI image exists)
                    if (aiImageUri != null) {
                        IconButton(
                            onClick = { 
                                val newValue = !showAIImage
                                showAIImage = newValue
                                saveShowAIImage(context, newValue)
                            }
                        ) {
                            Icon(
                                Icons.Filled.AutoAwesome,
                                contentDescription = if (showAIImage) "Show 3D" else "Show AI",
                                tint = if (showAIImage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                    // Goal button
                    Button(onClick = onOpenGoalMode) { Text("Goal") }
                }
            }
        )

        ScoreBarsWithBodyFat(
            groupScores = groupScores,
            bodyFatPercent = bf,
            goalGroupScores = goalGroupScores,
            goalBodyFatPercent = goalBf,
            onClickGroup = onOpenGroup
        )

        val hasCustom = remember(days) {
            days.any { it.exercises.any { ex -> ex.primaryMuscleId == null && !ex.primaryMuscleCustomName.isNullOrBlank() } }
        }
        if (hasCustom) {
            Text(
                "Note: Custom muscles are not scored (no calibration).",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.alpha(0.7f)
            )
        }
    }
}

@Composable
private fun ScoreBarsWithBodyFat(
    groupScores: Map<String, Int>,
    bodyFatPercent: Float,
    goalGroupScores: Map<String, Int> = emptyMap(),
    goalBodyFatPercent: Float? = null,
    onClickGroup: (String) -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

            // Body fat row (RED)
            val bf = bodyFatPercent.coerceIn(5f, 50f)
            val bf01 = (bf - 5f) / 45f // 5% -> 0, 50% -> 1
            val goalBf01 = goalBodyFatPercent?.coerceIn(5f, 50f)?.let { (it - 5f) / 45f }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Body Fat", color = Color.Red)
                Text("${bf.toInt()}%", color = Color.Red)
            }
            GoalProgressBar(
                current = bf01,
                goal = goalBf01,
                color = Color.Red,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(6.dp))

            MuscleGroup.entries.forEach { g ->
                val v = (groupScores[g.id] ?: 0).coerceIn(0, 100)
                val goalV = (goalGroupScores[g.id] ?: 0).coerceIn(0, 100)

                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onClickGroup(g.id) },
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(g.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(v.toString())
                }
                GoalProgressBar(
                    current = v / 100f,
                    goal = if (goalGroupScores.isNotEmpty()) goalV / 100f else null,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun GoalProgressBar(
    current: Float,
    goal: Float?,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val clampedCurrent = current.coerceIn(0f, 1f)
    val clampedGoal = goal?.coerceIn(0f, 1f)

    Canvas(
        modifier = modifier
            .height(10.dp)
    ) {
        val w = size.width
        val h = size.height

        // Background track
        drawRoundRect(
            color = color.copy(alpha = 0.15f),
            size = size,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2, h / 2)
        )

        // Current filled bar
        drawRoundRect(
            color = color,
            size = androidx.compose.ui.geometry.Size(width = w * clampedCurrent, height = h),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2, h / 2)
        )

        // Goal marker as a small circle (dot) at the goal position
        if (clampedGoal != null) {
            val x = w * clampedGoal
            val center = androidx.compose.ui.geometry.Offset(x, h / 2f)
            // Outer ring
            drawCircle(
                color = color.copy(alpha = 0.15f),
                radius = h / 2f,
                center = center
            )
            // Inner solid dot
            drawCircle(
                color = color,
                radius = h / 4f,
                center = center
            )
        }
    }
}

// -------------------- GOAL MODE --------------------

@Composable
private fun GoalModeScreen(
    days: List<WorkoutDay>,
    profile: Profile,
    goals: Goals?,
    onGoalsChanged: (Goals) -> Unit
) {
    val muscleScores = remember(days, profile) { computeMuscleScores(days, profile) }
    val baseGroupScores = remember(muscleScores) { computeGroupScoresFromMuscles(muscleScores) }

    var goalBodyFat by remember(goals, profile) {
        mutableStateOf(
            goals?.bodyFatPercent ?: (profile.bodyFatPct ?: 15f).coerceIn(5f, 50f)
        )
    }

    val goalScores = remember(goals, baseGroupScores) {
        mutableStateMapOf<String, Float>().apply {
            MuscleGroup.entries.forEach { g ->
                val existing = goals?.groupScores?.get(g.id)
                val base = baseGroupScores[g.id] ?: 0
                put(g.id, (existing ?: base).toFloat())
            }
        }
    }

    // Map goalScores into Ints for the 3D figure
    val goalScoresInt: Map<String, Int> = MuscleGroup.entries.associate { g ->
        g.id to (goalScores[g.id] ?: 0f).toInt().coerceIn(0, 100)
    }

    val scroll = rememberScrollState()

    val context = androidx.compose.ui.platform.LocalContext.current
    var showAIImage by remember { mutableStateOf(loadShowAIImage(context)) }
    var aiImageUri by remember { mutableStateOf<Uri?>(loadAIGeneratedImageUri(context)) }
    
    // Reload AI image URI when it might have changed (e.g., from Gymini tab)
    LaunchedEffect(Unit) {
        aiImageUri = loadAIGeneratedImageUri(context)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        BodyScoreFigure3D(
            scores = goalScoresInt,
            bodyFatPercent = goalBodyFat,
            aiImageUri = if (showAIImage) aiImageUri else null,
            overlayContent = {
                // Toggle button for AI image (only show if AI image exists)
                if (aiImageUri != null) {
                    IconButton(
                        onClick = { 
                            val newValue = !showAIImage
                            showAIImage = newValue
                            saveShowAIImage(context, newValue)
                        }
                    ) {
                        Icon(
                            Icons.Filled.AutoAwesome,
                            contentDescription = if (showAIImage) "Show 3D" else "Show AI",
                            tint = if (showAIImage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        )

        Card(Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Body fat slider (RED)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Body Fat", color = Color.Red)
                    Text("${goalBodyFat.toInt()}%", color = Color.Red)
                }
                Slider(
                    value = goalBodyFat,
                    onValueChange = { v ->
                        goalBodyFat = v.coerceIn(5f, 50f)
                        onGoalsChanged(
                            Goals(
                                bodyFatPercent = goalBodyFat,
                                groupScores = MuscleGroup.entries.associate { g ->
                                    g.id to (goalScores[g.id] ?: 0f).toInt().coerceIn(0, 100)
                                }
                            )
                        )
                    },
                    valueRange = 5f..50f
                )

                Spacer(Modifier.height(6.dp))

                // Group sliders
                MuscleGroup.entries.forEach { g ->
                    val v = (goalScores[g.id] ?: 0f).coerceIn(0f, 100f)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(g.displayName)
                        Text(v.toInt().toString())
                    }
                    Slider(
                        value = v,
                        onValueChange = { newV ->
                            val clamped = newV.coerceIn(0f, 100f)
                            goalScores[g.id] = clamped
                            onGoalsChanged(
                                Goals(
                                    bodyFatPercent = goalBodyFat,
                                    groupScores = MuscleGroup.entries.associate { gg ->
                                        gg.id to (goalScores[gg.id] ?: 0f).toInt().coerceIn(0, 100)
                                    }
                                )
                            )
                        },
                        valueRange = 0f..100f
                    )
                }
            }
        }
    }
}

// -------------------- PROGRESS GROUP DETAIL --------------------

@Composable
private fun ProgressGroupDetailScreen(
    groupId: String,
    days: List<WorkoutDay>,
    profile: Profile
) {
    val muscles = remember(groupId) { musclesForGroup(groupId) }
    val muscleScores = remember(days, profile) { computeMuscleScores(days, profile) }

    Column(
        Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Muscles", style = MaterialTheme.typography.titleMedium)

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(muscles, key = { it.id }) { m ->
                val entries = remember(days, m.id) { collectWeightHistoryForMuscle(days, m.id) }
                val score = muscleScores[m.id]

                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(m.displayName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text(text = (score?.toString() ?: "—"), style = MaterialTheme.typography.titleMedium)
                        }

                        if (entries.isEmpty()) {
                            Text("No history yet.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.alpha(0.7f))
                        } else {
                            WeightHistoryChart(entries = entries, modifier = Modifier.fillMaxWidth().height(110.dp))
                            val last = entries.last()
                            Text("Last: ${last.w} kg", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

private fun collectWeightHistoryForMuscle(days: List<WorkoutDay>, muscleId: String): List<WeightEntry> {
    val all = mutableListOf<WeightEntry>()
    for (d in days) {
        for (ex in d.exercises) {
            if (ex.primaryMuscleId == muscleId) all.addAll(ex.weightHistory)
        }
    }
    return all.sortedBy { it.t }
}

@Composable
private fun WeightHistoryChart(
    entries: List<WeightEntry>,
    modifier: Modifier = Modifier
) {
    val onSurface = MaterialTheme.colorScheme.onSurface

    Canvas(modifier) {
        if (entries.size < 2) return@Canvas

        val w = size.width
        val h = size.height
        val pad = 12f

        val minT = entries.minOf { it.t }
        val maxT = entries.maxOf { it.t }
        val minW = entries.minOf { it.w }
        val maxW = entries.maxOf { it.w }

        val dt = max(1f, (maxT - minT).toFloat())
        val dw = max(0.001f, (maxW - minW))

        fun x(t: Long): Float = pad + ((t - minT).toFloat() / dt) * (w - 2f * pad)
        fun y(v: Float): Float {
            val n = (v - minW) / dw
            return (h - pad) - n * (h - 2f * pad)
        }

        drawLine(
            color = onSurface.copy(alpha = 0.15f),
            start = Offset(pad, h - pad),
            end = Offset(w - pad, h - pad),
            strokeWidth = 2f
        )

        for (i in 0 until entries.size - 1) {
            val a = entries[i]
            val b = entries[i + 1]
            drawLine(
                color = onSurface.copy(alpha = 0.7f),
                start = Offset(x(a.t), y(a.w)),
                end = Offset(x(b.t), y(b.w)),
                strokeWidth = 3f
            )
        }

        entries.forEach { e ->
            drawCircle(
                color = onSurface.copy(alpha = 0.85f),
                radius = 4f,
                center = Offset(x(e.t), y(e.w))
            )
        }
    }
}

// -------------------- AI PHOTO PERSISTENCE --------------------

private fun saveAIPhotoUri(context: Context, isFront: Boolean, uri: Uri?) {
    val key = if (isFront) KEY_AI_FRONT_PHOTO else KEY_AI_BACK_PHOTO
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(key, uri?.toString())
        .apply()
}

private fun loadAIPhotoUri(context: Context, isFront: Boolean): Uri? {
    val key = if (isFront) KEY_AI_FRONT_PHOTO else KEY_AI_BACK_PHOTO
    val uriString = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(key, null)
    return uriString?.let { Uri.parse(it) }
}

private fun saveShowAIImage(context: Context, show: Boolean) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(KEY_SHOW_AI_IMAGE, show)
        .apply()
}

private fun loadShowAIImage(context: Context): Boolean {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY_SHOW_AI_IMAGE, false)
}

private fun loadAIGeneratedImageUri(context: Context): Uri? {
    val uriString = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_AI_GENERATED_IMAGE, null)
    return uriString?.let { Uri.parse(it) }
}

private fun loadAnalysisResult(context: Context): String? {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_ANALYSIS_RESULT, null)
}

private fun saveAnalysisResult(context: Context, result: String) {
    // Use commit() for critical user data to ensure it's saved synchronously
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_ANALYSIS_RESULT, result)
        .commit()
}

private fun loadScheduleSuggestions(context: Context): String? {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_SCHEDULE_SUGGESTIONS, null)
}

private fun saveScheduleSuggestions(context: Context, suggestions: String) {
    // Use commit() for critical user data to ensure it's saved synchronously
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_SCHEDULE_SUGGESTIONS, suggestions)
        .commit()
}

// -------------------- AI SCREEN --------------------

@Composable
private fun AIScreen(
    days: List<WorkoutDay>,
    profile: Profile,
    goals: Goals?,
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val aiService = remember { GeminiAIService(context) }
    val coroutineScope = rememberCoroutineScope()
    
    var frontPhotoUri by remember { mutableStateOf<Uri?>(loadAIPhotoUri(context, isFront = true)) }
    var backPhotoUri by remember { mutableStateOf<Uri?>(loadAIPhotoUri(context, isFront = false)) }
    
    var isLoading by remember { mutableStateOf(false) }
    var aiSuggestions by remember { mutableStateOf<String?>(null) }
    var aiError by remember { mutableStateOf<String?>(null) }
    var aiGeneratedImageUri by remember { mutableStateOf<Uri?>(null) }
    
    val frontPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            frontPhotoUri = it
            saveAIPhotoUri(context, isFront = true, it)
        }
    }
    
    val backPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            backPhotoUri = it
            saveAIPhotoUri(context, isFront = false, it)
        }
    }
    
    val muscleScores = remember(days, profile) { computeMuscleScores(days, profile) }
    val groupScores = remember(muscleScores) { computeGroupScoresFromMuscles(muscleScores) }
    val bf = (profile.bodyFatPct ?: 15f).coerceIn(5f, 50f)
    
    // Track previous scores for comparison
    var previousScores by remember { mutableStateOf<Map<String, Int>>(groupScores) }
    
    // Auto-generate visualization when scores change (if photos exist)
    LaunchedEffect(groupScores, bf) {
        if (frontPhotoUri != null && backPhotoUri != null && !isLoading && groupScores != previousScores) {
            isLoading = true
            aiError = null
            aiService.generateUpdatedMuscleMassVisualization(
                frontPhotoUri = frontPhotoUri,
                backPhotoUri = backPhotoUri,
                newScores = groupScores,
                previousScores = previousScores,
                bodyFatPercent = bf
            ).fold(
                onSuccess = { imageUri ->
                    imageUri?.let { uri ->
                        aiGeneratedImageUri = uri
                        // Save generated image URI
                        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                            .edit()
                            .putString(KEY_AI_GENERATED_IMAGE, uri.toString())
                            .apply()
                    }
                    previousScores = groupScores
                    isLoading = false
                },
                onFailure = { e ->
                    aiError = "Failed to generate visualization: ${e.message}"
                    isLoading = false
                }
            )
        }
    }
    
    val scroll = rememberScrollState()
    
    var comprehensiveAnalysisResult by remember { 
        mutableStateOf<String?>(loadAnalysisResult(context))
    }
    var isAnalyzing by remember { mutableStateOf(false) }
    
    // Manual analysis trigger function
    fun triggerAnalysis() {
        val frontUri = frontPhotoUri
        val backUri = backPhotoUri
        if (frontUri != null && backUri != null && !isAnalyzing) {
            isAnalyzing = true
            comprehensiveAnalysisResult = null
            aiError = null
            coroutineScope.launch {
                aiService.comprehensiveAnalysis(
                    frontPhotoUri = frontUri,
                    backPhotoUri = backUri,
                    currentScores = groupScores,
                    bodyFatPercent = bf,
                    goals = goals?.groupScores,
                    goalBodyFat = goals?.bodyFatPercent,
                    days = days
                ).fold(
                    onSuccess = { result ->
                        comprehensiveAnalysisResult = result
                        saveAnalysisResult(context, result)
                        isAnalyzing = false
                    },
                    onFailure = { e ->
                        aiError = "Analysis failed: ${e.message}"
                        isAnalyzing = false
                    }
                )
            }
        }
    }
    
    var selectedTab by remember { mutableStateOf(0) }
    
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Analysis") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Chat") }
            )
        }
        
        when (selectedTab) {
            0 -> AnalysisTab(
                frontPhotoUri = frontPhotoUri,
                backPhotoUri = backPhotoUri,
                frontPhotoLauncher = frontPhotoLauncher,
                backPhotoLauncher = backPhotoLauncher,
                aiGeneratedImageUri = aiGeneratedImageUri,
                comprehensiveAnalysisResult = comprehensiveAnalysisResult,
                isAnalyzing = isAnalyzing,
                aiError = aiError,
                context = context,
                onRefreshAnalysis = { triggerAnalysis() },
                days = days,
                groupScores = groupScores,
                bf = bf,
                aiService = aiService,
                coroutineScope = coroutineScope,
                schedule = loadSchedule(context),
                allExerciseCards = days.flatMap { it.exercises }
            )
            1 -> ChatTab(
                days = days,
                groupScores = groupScores,
                bf = bf,
                aiService = aiService,
                coroutineScope = coroutineScope
            )
        }
    }
}

@Composable
private fun AnalysisTab(
    frontPhotoUri: Uri?,
    backPhotoUri: Uri?,
    frontPhotoLauncher: ActivityResultLauncher<String>,
    backPhotoLauncher: ActivityResultLauncher<String>,
    aiGeneratedImageUri: Uri?,
    comprehensiveAnalysisResult: String?,
    isAnalyzing: Boolean,
    aiError: String?,
    context: Context,
    onRefreshAnalysis: () -> Unit,
    days: List<WorkoutDay>,
    groupScores: Map<String, Int>,
    bf: Float,
    aiService: GeminiAIService,
    coroutineScope: CoroutineScope,
    schedule: Schedule,
    allExerciseCards: List<ExerciseCard>
) {
    val scroll = rememberScrollState()
    
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Visual Assessment Section (collapsible)
        var photosExpanded by remember { mutableStateOf(true) }
        
        CollapsibleCard(
            title = "Visual Assessment",
            expanded = photosExpanded,
            onToggle = { photosExpanded = !photosExpanded }
        ) {
            Text(
                "Upload front and back full body photos for AI analysis.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.alpha(0.7f)
            )
            
            OutlinedButton(
                onClick = {
                    // Launch front photo first, then back
                    if (frontPhotoUri == null) {
                        frontPhotoLauncher.launch("image/*")
                    } else if (backPhotoUri == null) {
                        backPhotoLauncher.launch("image/*")
                    } else {
                        // Both photos exist, allow changing front photo
                        frontPhotoLauncher.launch("image/*")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (frontPhotoUri == null || backPhotoUri == null) "Add Body Photos" else "Change Photos")
            }
            
            if (frontPhotoUri != null && backPhotoUri != null) {
                Text(
                    "Photos uploaded. Use refresh button below to generate analysis.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        
        // AI Generated Visualization (show this instead of uploaded photos)
        if (aiGeneratedImageUri != null) {
            val imageUri = aiGeneratedImageUri
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "AI Generated Muscle Mass Visualization",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    var bitmap by remember(imageUri) { mutableStateOf<Bitmap?>(null) }
                    
                    LaunchedEffect(imageUri) {
                        bitmap = runCatching {
                            context.contentResolver.openInputStream(imageUri)
                                ?.use { BitmapFactory.decodeStream(it) }
                        }.getOrNull()
                    }
                    
                    bitmap?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "AI generated muscle mass visualization",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(400.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    } ?: run {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(400.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Loading visualization...")
                        }
                    }
                }
            }
        }
        
        // Schedule Suggestions Section (collapsible)
        var suggestionsExpanded by remember { mutableStateOf(true) }
        var suggestionsResult by remember { mutableStateOf<String?>(loadScheduleSuggestions(context)) }
        var isLoadingSuggestions by remember { mutableStateOf(false) }
        var suggestionsError by remember { mutableStateOf<String?>(null) }
        
        CollapsibleCard(
            title = "Schedule Suggestions",
            expanded = suggestionsExpanded,
            onToggle = { suggestionsExpanded = !suggestionsExpanded }
        ) {
            Text(
                "Gymini analyzes your weekly schedule and provides optimization suggestions to improve your workout routine.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.alpha(0.7f)
            )
            
            if (suggestionsResult == null && !isLoadingSuggestions) {
                Button(
                    onClick = {
                        isLoadingSuggestions = true
                        suggestionsError = null
                        coroutineScope.launch {
                            aiService.getScheduleSuggestions(
                                schedule = schedule,
                                allExerciseCards = allExerciseCards,
                                currentScores = groupScores
                            ).fold(
                                onSuccess = { result ->
                                    suggestionsResult = result
                                    saveScheduleSuggestions(context, result)
                                    isLoadingSuggestions = false
                                },
                                onFailure = { e ->
                                    suggestionsError = "Failed to get suggestions: ${e.message}"
                                    isLoadingSuggestions = false
                                }
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Get Schedule Suggestions")
                }
            }
            
            if (suggestionsResult != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = {
                            suggestionsResult = null
                            saveScheduleSuggestions(context, "")
                            isLoadingSuggestions = true
                            suggestionsError = null
                            coroutineScope.launch {
                                aiService.getScheduleSuggestions(
                                    schedule = schedule,
                                    allExerciseCards = allExerciseCards,
                                    currentScores = groupScores
                                ).fold(
                                    onSuccess = { result ->
                                        suggestionsResult = result
                                        saveScheduleSuggestions(context, result)
                                        isLoadingSuggestions = false
                                    },
                                    onFailure = { e ->
                                        suggestionsError = "Failed to get suggestions: ${e.message}"
                                        isLoadingSuggestions = false
                                    }
                                )
                            }
                        },
                        enabled = !isLoadingSuggestions,
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "Refresh",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            
            if (isLoadingSuggestions) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        "Getting suggestions...",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.alpha(0.7f)
                    )
                }
            }
            
            suggestionsError?.let { error ->
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            suggestionsResult?.let { result ->
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp)
                )
                
                MarkdownText(
                    text = result,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        
        // Analysis Section (collapsible)
        var analysisExpanded by remember { mutableStateOf(true) }
        
        CollapsibleCard(
            title = "Analysis",
            expanded = analysisExpanded,
            onToggle = { analysisExpanded = !analysisExpanded }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = onRefreshAnalysis,
                    enabled = frontPhotoUri != null && backPhotoUri != null && !isAnalyzing,
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = "Refresh",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            
            Text(
                "Gymini analyzes your body photos, current scores, and goals to provide a full analysis with specific recommendations on what to do and how much to do to reach your goals.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.alpha(0.7f)
            )
            
            if (frontPhotoUri == null || backPhotoUri == null) {
                Text(
                    "Upload body photos above to enable analysis.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            } else {
                if (isAnalyzing) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            "Analyzing your photos, scores, and goals...",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.alpha(0.7f)
                        )
                    }
                }
                
                comprehensiveAnalysisResult?.let { result ->
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                    
                    MarkdownText(
                        text = result,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        
        // Error message
        aiError?.let { error ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Text(
                    error,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
private fun ChatTab(
    days: List<WorkoutDay>,
    groupScores: Map<String, Int>,
    bf: Float,
    aiService: GeminiAIService,
    coroutineScope: CoroutineScope
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var chatMessages by remember { mutableStateOf(loadChatHistory(context)) }
    var promptText by remember { mutableStateOf("") }
    var isPromptLoading by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    
    // Scroll to bottom when new message is added
    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        // Messages list
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 80.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(chatMessages) { message ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.75f)
                            .padding(horizontal = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (message.isUser) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = message.text,
                            modifier = Modifier.padding(12.dp),
                            color = if (message.isUser) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            
            if (isPromptLoading) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth(0.75f)
                                .padding(horizontal = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                LinearProgressIndicator(
                                    modifier = Modifier.weight(1f).height(4.dp)
                                )
                                Text(
                                    "Thinking...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // Input box at bottom
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = promptText,
                onValueChange = { promptText = it },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp, max = 120.dp),
                placeholder = { Text("Type a message...") },
                maxLines = 4,
                enabled = !isPromptLoading,
                shape = RoundedCornerShape(24.dp)
            )
            
            FloatingActionButton(
                onClick = {
                    if (promptText.isNotBlank() && !isPromptLoading) {
                        val userMessage = promptText
                        promptText = ""
                        
                        // Add user message
                        val newMessages = chatMessages + ChatMessage(userMessage, isUser = true)
                        chatMessages = newMessages
                        saveChatHistory(context, newMessages)
                        
                        isPromptLoading = true
                        
                        coroutineScope.launch {
                            aiService.sendCustomPrompt(
                                prompt = userMessage,
                                days = days,
                                currentScores = groupScores,
                                bodyFatPercent = bf
                            ).fold(
                                onSuccess = { response ->
                                    val updatedMessages = chatMessages + ChatMessage(response, isUser = false)
                                    chatMessages = updatedMessages
                                    saveChatHistory(context, updatedMessages)
                                    isPromptLoading = false
                                },
                                onFailure = { e ->
                                    val errorMessage = "Sorry, I encountered an error: ${e.message}"
                                    val updatedMessages = chatMessages + ChatMessage(errorMessage, isUser = false)
                                    chatMessages = updatedMessages
                                    saveChatHistory(context, updatedMessages)
                                    isPromptLoading = false
                                }
                            )
                        }
                    }
                },
                modifier = Modifier.size(48.dp),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    Icons.Filled.Send,
                    contentDescription = "Send",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

// -------------------- COLLAPSIBLE CARD --------------------

@Composable
private fun CollapsibleCard(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium
                )
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand"
                )
            }
            
            if (expanded) {
                content()
            }
        }
    }
}

// -------------------- MARKDOWN PARSER --------------------

@Composable
private fun MarkdownText(text: String, style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium) {
    val annotatedString = remember(text) {
        buildAnnotatedString {
            var remaining = text
            while (remaining.isNotEmpty()) {
                // Check for header (###)
                if (remaining.startsWith("### ")) {
                    val endIndex = remaining.indexOf('\n').takeIf { it > 0 } ?: remaining.length
                    val headerText = remaining.substring(4, endIndex).trim()
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, fontSize = style.fontSize * 1.2f)) {
                        append(headerText)
                    }
                    if (endIndex < remaining.length) {
                        append("\n\n")
                    }
                    remaining = remaining.substring((endIndex + 1).coerceAtMost(remaining.length))
                }
                // Check for bold (**text**)
                else if (remaining.startsWith("**")) {
                    val endBold = remaining.indexOf("**", 2)
                    if (endBold > 0) {
                        val boldText = remaining.substring(2, endBold)
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(boldText)
                        }
                        remaining = remaining.substring(endBold + 2)
                    } else {
                        // No closing **, just append as normal
                        append(remaining[0])
                        remaining = remaining.substring(1)
                    }
                }
                // Regular text
                else {
                    val nextHeader = remaining.indexOf("### ")
                    val nextBold = remaining.indexOf("**")
                    val nextSpecial = when {
                        nextHeader >= 0 && nextBold >= 0 -> minOf(nextHeader, nextBold)
                        nextHeader >= 0 -> nextHeader
                        nextBold >= 0 -> nextBold
                        else -> Int.MAX_VALUE
                    }
                    if (nextSpecial < Int.MAX_VALUE) {
                        append(remaining.substring(0, nextSpecial))
                        remaining = remaining.substring(nextSpecial)
                    } else {
                        append(remaining)
                        remaining = ""
                    }
                }
            }
        }
    }
    
    Text(annotatedString, style = style)
}

// -------------------- SETTINGS SCREEN --------------------

@Composable
private fun SettingsScreen(
    profile: Profile,
    unitSystem: UnitSystem,
    auth: FirebaseAuth,
    days: MutableList<WorkoutDay>,
    schedule: Schedule,
    goals: Goals?,
    onProfileClick: () -> Unit,
    onUnitsClick: () -> Unit,
    onClearData: () -> Unit,
    onScheduleChanged: (Schedule) -> Unit,
    onProfileChanged: (Profile) -> Unit,
    onGoalsChanged: (Goals?) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var currentUser by remember { mutableStateOf(auth.currentUser) }
    var isSigningIn by remember { mutableStateOf(false) }
    var signInError by remember { mutableStateOf<String?>(null) }
    
    // Listen for auth state changes
    LaunchedEffect(Unit) {
        auth.addAuthStateListener { firebaseAuth ->
            currentUser = firebaseAuth.currentUser
        }
    }
    
    fun getWebClientIdFromGoogleServices(context: Context): String? {
        return try {
            // Try multiple paths to find google-services.json
            val possiblePaths = listOf(
                // Path 1: From project root (for development)
                java.io.File(context.filesDir.parentFile?.parentFile?.parentFile, "app/google-services.json"),
                // Path 2: From app module root
                java.io.File(context.filesDir.parentFile?.parentFile, "app/google-services.json"),
                // Path 3: Try reading from assets
                null // Will try assets separately
            )
            
            var json: String? = null
            var file: java.io.File? = null
            
            // Try file paths first
            for (path in possiblePaths.filterNotNull()) {
                if (path.exists()) {
                    file = path
                    json = path.readText()
                    android.util.Log.d("Settings", "Found google-services.json at: ${path.absolutePath}")
                    break
                }
            }
            
            // Try assets as fallback
            if (json == null) {
                try {
                    val inputStream = context.assets.open("google-services.json")
                    json = inputStream.bufferedReader().use { it.readText() }
                    android.util.Log.d("Settings", "Found google-services.json in assets")
                } catch (e: Exception) {
                    android.util.Log.w("Settings", "google-services.json not found in assets", e)
                }
            }
            
            if (json == null) {
                android.util.Log.e("Settings", "Could not find google-services.json in any location")
                return null
            }
            
            val jsonObject = JSONObject(json)
            val clients = jsonObject.getJSONArray("client")
            
            for (i in 0 until clients.length()) {
                val client = clients.getJSONObject(i)
                val oauthClients = client.optJSONArray("oauth_client")
                if (oauthClients != null && oauthClients.length() > 0) {
                    for (j in 0 until oauthClients.length()) {
                        val oauthClient = oauthClients.getJSONObject(j)
                        val clientType = oauthClient.optInt("client_type", -1)
                        // client_type 3 is Web client
                        if (clientType == 3) {
                            val clientId = oauthClient.getString("client_id")
                            android.util.Log.d("Settings", "Found web client ID: $clientId")
                            return clientId
                        }
                    }
                }
            }
            android.util.Log.w("Settings", "Web client ID (client_type 3) not found in google-services.json")
            null
        } catch (e: Exception) {
            android.util.Log.e("Settings", "Error reading google-services.json", e)
            e.printStackTrace()
            null
        }
    }
    
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        isSigningIn = true
        signInError = null
        coroutineScope.launch {
            try {
                val account = task.getResult(ApiException::class.java)
                val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                auth.signInWithCredential(credential).await()
                isSigningIn = false
            } catch (e: ApiException) {
                isSigningIn = false
                signInError = when (e.statusCode) {
                    12501 -> "Sign-in was cancelled"
                    10 -> "Google Sign-In is not available. Please try again later."
                    else -> "Sign-in failed: ${e.message}"
                }
                android.util.Log.e("Settings", "Google sign in failed", e)
            } catch (e: Exception) {
                isSigningIn = false
                signInError = "Sign-in failed: ${e.message}"
                android.util.Log.e("Settings", "Sign in failed", e)
            }
        }
    }
    
    fun signInWithGoogle() {
        // Get web client ID from string resources or fallback to reading google-services.json
        val webClientId = try {
            val resId = context.resources.getIdentifier("web_client_id", "string", context.packageName)
            if (resId != 0) {
                val id = context.getString(resId)
                if (id.isNotBlank() && id != "YOUR_WEB_CLIENT_ID_HERE") {
                    id
                } else {
                    getWebClientIdFromGoogleServices(context)
                }
            } else {
                getWebClientIdFromGoogleServices(context)
            }
        } catch (e: Exception) {
            android.util.Log.e("Settings", "Error getting web_client_id", e)
            getWebClientIdFromGoogleServices(context)
        }
        
        if (webClientId.isNullOrBlank()) {
            signInError = "Google Sign-In is not available. Please try again later."
            return
        }
        
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
        val googleSignInClient = GoogleSignIn.getClient(context, gso)
        googleSignInLauncher.launch(googleSignInClient.signInIntent)
    }
    
    fun signOut() {
        auth.signOut()
        GoogleSignIn.getClient(context, GoogleSignInOptions.DEFAULT_SIGN_IN).signOut()
    }
    val scroll = rememberScrollState()
    
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Account Section
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Account",
                    style = MaterialTheme.typography.titleMedium
                )
                
                if (currentUser != null) {
                    // Signed in
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    currentUser?.displayName ?: "Signed in",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    currentUser?.email ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.alpha(0.7f)
                                )
                            }
                            IconButton(
                                onClick = { signOut() },
                                enabled = !isSigningIn
                            ) {
                                Icon(
                                    Icons.Filled.Logout,
                                    contentDescription = "Sign out"
                                )
                            }
                        }
                        Text(
                            "Your data is synced with your Google account",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.alpha(0.7f)
                        )
                    }
                } else {
                    // Not signed in
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Sign in with Google to sync your data across devices",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.alpha(0.7f)
                        )
                        if (signInError != null) {
                            Text(
                                signInError!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Button(
                            onClick = { signInWithGoogle() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isSigningIn
                        ) {
                            if (isSigningIn) {
                                Text("Signing in...")
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.AccountCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text("Sign in with Google")
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Profile Section
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Profile",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "Manage your personal information and body metrics",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.alpha(0.7f)
                )
                Button(
                    onClick = onProfileClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Edit Profile")
                }
            }
        }
        
        // Units Section
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Units",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "Current: ${if (unitSystem == UnitSystem.METRIC) "Metric (kg, cm)" else "Imperial (lb, ft)"}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(
                    onClick = onUnitsClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Change Units")
                }
            }
        }
        
        // Data Management Section
        var isUploadingBackup by remember { mutableStateOf(false) }
        val authForEmail = FirebaseAuth.getInstance()
        
        // Export launcher
        val exportLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("text/plain")
        ) { uri ->
            if (uri != null) {
                val email = authForEmail.currentUser?.email
                val dataString = exportAllDataToString(days, profile, schedule, goals, email)
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(dataString.toByteArray(Charsets.UTF_8))
                    }
                }
            }
        }
        
        // Import launcher
        val importLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                runCatching {
                    val dataString = context.contentResolver.openInputStream(uri)
                        ?.bufferedReader(Charsets.UTF_8)
                        ?.use { it.readText() }
                    
                    if (!dataString.isNullOrBlank()) {
                        val result = importAllDataFromString(dataString)
                        if (result.success) {
                            result.days?.let {
                                days.clear()
                                days.addAll(it)
                                saveDays(context, it)
                            }
                            result.profile?.let {
                                onProfileChanged(it)
                            }
                            result.schedule?.let {
                                onScheduleChanged(it)
                            }
                            result.goals?.let {
                                onGoalsChanged(it)
                            }
                        }
                    }
                }
            }
        }
        
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Data Management",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "Export or import your workout data as a text file",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.alpha(0.7f)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            exportLauncher.launch("gymnotes_backup.txt")
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Export Data")
                    }
                    Button(
                        onClick = {
                            importLauncher.launch(arrayOf("text/plain", "text/*"))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Import Data")
                    }
                }
                
                // Cloud backup button
                if (currentUser != null && currentUser?.email != null) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        "Cloud Backup",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        "Your data is automatically backed up monthly. You can also manually upload now.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.alpha(0.7f)
                    )
                    var backupError by remember { mutableStateOf<String?>(null) }
                    var backupSuccess by remember { mutableStateOf(false) }
                    
                    if (backupError != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                backupError!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Tip: You can use 'Export Data' above to save a backup file instead.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.alpha(0.7f)
                            )
                        }
                    }
                    if (backupSuccess) {
                        Text(
                            "Backup uploaded successfully!",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    
                    Button(
                        onClick = {
                            isUploadingBackup = true
                            backupError = null
                            backupSuccess = false
                            coroutineScope.launch {
                                try {
                                    val email = currentUser?.email
                                    if (email.isNullOrBlank()) {
                                        isUploadingBackup = false
                                        backupError = "Please sign in with Google to use cloud backup"
                                        return@launch
                                    }
                                    
                                    // Verify user is still authenticated
                                    val authCheck = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                                    if (authCheck == null || authCheck.email != email) {
                                        isUploadingBackup = false
                                        backupError = "Authentication expired. Please sign in again."
                                        return@launch
                                    }
                                    
                                    val dataString = exportAllDataToString(days, profile, schedule, goals, email)
                                    
                                    // Add timeout to prevent infinite hanging
                                    val result = kotlinx.coroutines.withTimeoutOrNull(30000) { // 30 second timeout
                                        uploadToCloudBackup(context, email, dataString)
                                    }
                                    
                                    if (result == null) {
                                        isUploadingBackup = false
                                        backupError = "Upload timed out. Please check your internet connection and try again."
                                        return@launch
                                    }
                                    
                                    result.fold(
                                        onSuccess = { 
                                            isUploadingBackup = false
                                            backupSuccess = true
                                            backupError = null
                                            android.util.Log.d("Settings", "Backup uploaded successfully")
                                        },
                                        onFailure = { e ->
                                            isUploadingBackup = false
                                            val errorMsg = when {
                                                e.message?.contains("PERMISSION_DENIED", ignoreCase = true) == true -> 
                                                    "Unable to upload backup. Please try again later."
                                                e.message?.contains("UNAVAILABLE", ignoreCase = true) == true -> 
                                                    "Backup service is temporarily unavailable. Please try again later."
                                                e.message?.contains("network", ignoreCase = true) == true -> 
                                                    "Network error. Please check your internet connection and try again."
                                                e.message?.contains("timeout", ignoreCase = true) == true -> 
                                                    "Upload timed out. Please check your internet connection and try again."
                                                e.message?.contains("Authentication", ignoreCase = true) == true -> 
                                                    "Please sign in with Google to use cloud backup."
                                                else -> "Upload failed. Please try again later."
                                            }
                                            backupError = errorMsg
                                            android.util.Log.e("Settings", "Backup failed", e)
                                        }
                                    )
                                } catch (e: Exception) {
                                    isUploadingBackup = false
                                    backupError = "Error: ${e.message ?: "Unknown error occurred"}"
                                    android.util.Log.e("Settings", "Backup exception", e)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isUploadingBackup
                    ) {
                        if (isUploadingBackup) {
                            Text("Uploading...")
                        } else {
                            Text("Upload to Cloud Now")
                        }
                    }
                }
            }
        }
        
        // Danger Zone Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Danger Zone",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    "Permanently delete all workout data. This cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                OutlinedButton(
                    onClick = onClearData,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Clear All Data")
                }
            }
        }
    }
}

// -------------------- HELPERS --------------------

private fun displayGroupName(day: WorkoutDay?): String {
    if (day == null) return "Workout"
    val preset = day.groupId?.let { id -> MuscleGroup.entries.firstOrNull { it.id == id }?.displayName }
    return preset ?: day.groupCustomName ?: "Unassigned"
}

private fun displayPrimaryMuscle(ex: ExerciseCard): String {
    val preset = ex.primaryMuscleId?.let { id -> Muscle.entries.firstOrNull { it.id == id }?.displayName }
    return preset ?: ex.primaryMuscleCustomName ?: "Unassigned"
}
