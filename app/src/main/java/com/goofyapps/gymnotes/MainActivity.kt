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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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

sealed class Screen {
    data object Workout : Screen()
    data object Progress : Screen()
    data class ProgressGroupDetail(val groupId: String) : Screen()
    data class DayDetail(val dayIndex: Int) : Screen()
    data class ExerciseDetail(val dayIndex: Int, val exerciseIndex: Int) : Screen()

    // NEW
    data object GoalMode : Screen()
    data object Gymini : Screen()
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

private const val PREFS_NAME = "gym_notes_prefs"
private const val KEY_DATA = "workout_data"
private const val KEY_PROFILE = "profile_json"
private const val KEY_GOALS = "goals_json"
private const val KEY_UNIT_SYSTEM = "unit_system"
private const val KEY_AI_FRONT_PHOTO = "ai_front_photo_uri"
private const val KEY_AI_BACK_PHOTO = "ai_back_photo_uri"
private const val KEY_AI_GENERATED_IMAGE = "ai_generated_image_uri"
private const val KEY_SHOW_AI_IMAGE = "show_ai_image_toggle"

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

    return result
}

private fun saveDays(context: Context, days: List<WorkoutDay>) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_DATA, serializeDays(days))
        .apply()
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
    val o = JSONObject(json)
    val w = if (o.isNull("weightKg")) null else o.optDouble("weightKg").toFloat()
    val h = if (o.isNull("heightCm")) null else o.optDouble("heightCm").toFloat()
    val bf = if (o.isNull("bodyFatPct")) null else o.optDouble("bodyFatPct").toFloat()
    val sex = o.optString("sex", "Unspecified")
    return Profile(weightKg = w, heightCm = h, bodyFatPct = bf, sex = sex)
}

private fun loadProfile(context: Context): Profile {
    val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_PROFILE, null) ?: return Profile()
    return runCatching { parseProfile(json) }.getOrElse { Profile() }
}

private fun saveProfile(context: Context, p: Profile) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_PROFILE, serializeProfile(p))
        .apply()
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

private data class Goals(
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
    val o = JSONObject(json)
    val bf = o.optDouble("bodyFatPercent", 20.0).toFloat()
    val groupsObj = o.optJSONObject("groups") ?: JSONObject()
    val map = mutableMapOf<String, Int>()
    val keys = groupsObj.keys()
    while (keys.hasNext()) {
        val k = keys.next()
        map[k] = groupsObj.optInt(k, 0)
    }
    return Goals(bodyFatPercent = bf.coerceIn(5f, 50f), groupScores = map)
}

private fun loadGoals(context: Context): Goals? {
    val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_GOALS, null) ?: return null
    return runCatching { parseGoals(json) }.getOrNull()
}

private fun saveGoals(context: Context, goals: Goals) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_GOALS, serializeGoals(goals))
        .apply()
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

    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(serializeDays(days).toByteArray())
                    }
                }
            }
        }

    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                runCatching {
                    val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    if (!json.isNullOrBlank()) {
                        val imported = parseDaysFromJson(json)
                        days.clear()
                        days.addAll(imported)
                        saveDays(context, days)
                    }
                }
            }
        }

    var gearOpen by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }
    var showUnits by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    fun topBar(title: String, showBack: Boolean, onBack: (() -> Unit)? = null): @Composable () -> Unit = {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = {
                if (showBack && onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            },
            actions = {
                Box {
                    IconButton(onClick = { gearOpen = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                    DropdownMenu(
                        expanded = gearOpen,
                        onDismissRequest = { gearOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Profile") },
                            onClick = {
                                gearOpen = false
                                showProfile = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Units (kg / lb)") },
                            onClick = {
                                gearOpen = false
                                showUnits = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Export data") },
                            onClick = {
                                gearOpen = false
                                exportLauncher.launch("gym_notes_backup.json")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Import data") },
                            onClick = {
                                gearOpen = false
                                importLauncher.launch(arrayOf("application/json", "text/*"))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Clear all data") },
                            onClick = {
                                gearOpen = false
                                showClearConfirm = true
                            }
                        )
                    }
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
        }
    }

    Scaffold(
        topBar = {
            when (val s = screen) {
                is Screen.Workout -> topBar("Workout", showBack = false).invoke()
                is Screen.Progress -> topBar("Progress", showBack = false).invoke()
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
                is Screen.Gymini -> topBar("Gymini", showBack = false).invoke()
            }
        },
        bottomBar = bottomBar
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = screen) {
                is Screen.Workout -> WorkoutHome(
                    days = days,
                    unitSystem = unitSystem,
                    onOpenDay = { idx -> screen = Screen.DayDetail(idx) },
                    onSave = { saveDays(context, days) }
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
private fun WorkoutHome(
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
            Text("Muscle Groups", style = MaterialTheme.typography.titleMedium)

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
        if (videoUri != null) {
            VideoThumbnail(
                uriString = videoUri!!,
                modifier = Modifier.clickable { onPlayVideo(videoUri!!) }
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

// -------------------- AI SCREEN --------------------

@Composable
private fun AIScreen(
    days: List<WorkoutDay>,
    profile: Profile,
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val aiService = remember { GeminiAIService(context) }
    
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
    
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "AI Analysis & Suggestions",
            style = MaterialTheme.typography.titleLarge
        )
        
        // Photo Upload Section
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Body Photos",
                    style = MaterialTheme.typography.titleMedium
                )
                
                Text(
                    "Upload front and back full body photos for AI analysis. This is a one-time setup.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.alpha(0.7f)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Front Photo
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (frontPhotoUri != null) {
                            var bitmap by remember(frontPhotoUri) { mutableStateOf<Bitmap?>(null) }
                            
                            LaunchedEffect(frontPhotoUri) {
                                bitmap = runCatching {
                                    context.contentResolver.openInputStream(frontPhotoUri!!)
                                        ?.use { BitmapFactory.decodeStream(it) }
                                }.getOrNull()
                            }
                            
                            bitmap?.let {
                                Image(
                                    bitmap = it.asImageBitmap(),
                                    contentDescription = "Front photo",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                            }
                            
                            Button(onClick = { frontPhotoLauncher.launch("image/*") }) {
                                Text("Change Front Photo")
                            }
                        } else {
                            OutlinedButton(
                                onClick = { frontPhotoLauncher.launch("image/*") },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Upload Front Photo")
                            }
                        }
                    }
                    
                    // Back Photo
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (backPhotoUri != null) {
                            var bitmap by remember(backPhotoUri) { mutableStateOf<Bitmap?>(null) }
                            
                            LaunchedEffect(backPhotoUri) {
                                bitmap = runCatching {
                                    context.contentResolver.openInputStream(backPhotoUri!!)
                                        ?.use { BitmapFactory.decodeStream(it) }
                                }.getOrNull()
                            }
                            
                            bitmap?.let {
                                Image(
                                    bitmap = it.asImageBitmap(),
                                    contentDescription = "Back photo",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                            }
                            
                            Button(onClick = { backPhotoLauncher.launch("image/*") }) {
                                Text("Change Back Photo")
                            }
                        } else {
                            OutlinedButton(
                                onClick = { backPhotoLauncher.launch("image/*") },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Upload Back Photo")
                            }
                        }
                    }
                }
            }
        }
        
        // AI Generated Visualization
        aiGeneratedImageUri?.let { imageUri ->
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
        
        // Analyze Button
        val coroutineScope = rememberCoroutineScope()
        if (frontPhotoUri != null && backPhotoUri != null) {
            Button(
                onClick = {
                    isLoading = true
                    aiError = null
                    aiSuggestions = null
                    
                    // Analyze photos and get initial suggestions
                    coroutineScope.launch {
                        aiService.analyzeBodyPhotosAndGenerateMuscleMass(
                            frontPhotoUri = frontPhotoUri!!,
                            backPhotoUri = backPhotoUri!!,
                            currentScores = groupScores,
                            bodyFatPercent = bf
                        ).fold(
                            onSuccess = { result ->
                                aiSuggestions = result.analysisText
                                aiGeneratedImageUri = result.generatedImageUri
                                // Save generated image URI
                                result.generatedImageUri?.let { uri ->
                                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                                        .edit()
                                        .putString(KEY_AI_GENERATED_IMAGE, uri.toString())
                                        .apply()
                                }
                                isLoading = false
                            },
                            onFailure = { e ->
                                aiError = "Analysis failed: ${e.message}"
                                isLoading = false
                            }
                        )
                        
                        // Get workout suggestions
                        aiService.getWorkoutSuggestions(
                            days = days,
                            currentScores = groupScores,
                            bodyFatPercent = bf
                        ).fold(
                            onSuccess = { suggestions ->
                                aiSuggestions = (aiSuggestions ?: "") + "\n\n" + suggestions
                            },
                            onFailure = { /* Ignore */ }
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    Text("Analyze with AI")
                }
            }
        }
        
        // Loading indicator
        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
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
        
        // AI Suggestions
        aiSuggestions?.let { suggestions ->
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "AI Analysis & Suggestions",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        suggestions,
                        style = MaterialTheme.typography.bodyMedium
                    )
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
