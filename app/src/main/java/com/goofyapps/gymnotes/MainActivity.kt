@file:OptIn(ExperimentalMaterial3Api::class)

package com.goofyapps.gymnotes

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
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
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import androidx.compose.material3.Slider


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
    val context = LocalContext.current
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
    val context = LocalContext.current
    val days = remember { mutableStateListOf<WorkoutDay>().apply { addAll(loadDays(context)) } }
    var profile by remember { mutableStateOf(loadProfile(context)) }

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
                // IMPORTANT: Anchor DropdownMenu properly, otherwise it often "does nothing".
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
                selected = screen is Screen.Progress || screen is Screen.ProgressGroupDetail,
                onClick = { screen = Screen.Progress },
                icon = { Icon(Icons.Filled.ShowChart, contentDescription = "Progress") },
                label = { Text("Progress") }
            )
        }
    }

    Scaffold(
        topBar = {
            when (val s = screen) {
                is Screen.Workout -> topBar("Workout", showBack = false).invoke()
                is Screen.Progress -> topBar("Progress", showBack = false).invoke()
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
            }
        },
        bottomBar = bottomBar
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = screen) {
                is Screen.Workout -> WorkoutHome(
                    days = days,
                    onOpenDay = { idx -> screen = Screen.DayDetail(idx) },
                    onSave = { saveDays(context, days) }
                )

                is Screen.Progress -> ProgressScreen(
                    days = days,
                    profile = profile,
                    onOpenGroup = { gid -> screen = Screen.ProgressGroupDetail(gid) }
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
            onDismiss = { showProfile = false },
            onSave = { p ->
                profile = p
                saveProfile(context, p)
                showProfile = false
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
    onDismiss: () -> Unit,
    onSave: (Profile) -> Unit
) {
    var weightText by remember { mutableStateOf(initial.weightKg?.toString() ?: "") }
    var heightText by remember { mutableStateOf(initial.heightCm?.toString() ?: "") }
    var bodyFatText by remember { mutableStateOf(initial.bodyFatPct?.toString() ?: "") }

    val sexOptions = listOf("Unspecified", "Male", "Female")
    var sexExpanded by remember { mutableStateOf(false) }
    var sex by remember { mutableStateOf(initial.sex.takeIf { it.isNotBlank() } ?: "Unspecified") }

    val w = weightText.toFloatOrNull()
    val h = heightText.toFloatOrNull()
    val bf = bodyFatText.toFloatOrNull()
    val bmi = Profile(weightKg = w, heightCm = h, bodyFatPct = bf, sex = sex).bmi()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = weightText,
                    onValueChange = { v -> if (v.all { it.isDigit() || it == '.' }) weightText = v },
                    label = { Text("Bodyweight (kg)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = heightText,
                    onValueChange = { v -> if (v.all { it.isDigit() || it == '.' }) heightText = v },
                    label = { Text("Height (cm)") },
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

// -------------------- WORKOUT HOME --------------------

@Composable
private fun WorkoutHome(
    days: MutableList<WorkoutDay>,
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
                                    ex.weight?.let { Text("Weight: $it ${ex.weightUnit}", style = MaterialTheme.typography.bodySmall) }

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
    onDismiss: () -> Unit,
    onAdd: (ExerciseCard) -> Unit
) {
    val context = LocalContext.current

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

                // IMPORTANT: do not ask / show group — parentGroupId already implies it.

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

                OutlinedTextField(
                    value = weightText,
                    onValueChange = { v -> if (v.all { it.isDigit() || it == '.' }) weightText = v },
                    label = { Text("Weight (kg)") },
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
                    val weight = weightText.toFloatOrNull()

                    val hist = mutableListOf<WeightEntry>()
                    if (weight != null) {
                        hist.add(WeightEntry(System.currentTimeMillis(), weight))
                    }

                    onAdd(
                        ExerciseCard(
                            equipmentName = equipmentName.trim(),
                            videoUri = videoUri?.toString(),
                            sets = sets,
                            reps = reps,
                            weight = weight,
                            weightUnit = "kg",
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
    onSave: (ExerciseCard) -> Unit,
    onPlayVideo: (String) -> Unit
) {
    val context = LocalContext.current

    var equipmentName by remember { mutableStateOf(exercise.equipmentName) }
    var setsText by remember { mutableStateOf(exercise.sets.toString()) }
    var repsText by remember { mutableStateOf(exercise.reps.toString()) }
    var weightText by remember { mutableStateOf(exercise.weight?.toString() ?: "") }
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

        OutlinedTextField(
            value = weightText,
            onValueChange = { v -> if (v.all { it.isDigit() || it == '.' }) weightText = v },
            label = { Text("Weight (kg)") },
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
                val newWeight = weightText.toFloatOrNull()

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
    Muscle.LOWER_BACK_ERECTORS.id  to Calibration(mid(115f, 135f) * LB_TO_KG, mid(250f, 280f) * LB_TO_KG), // reuse upper-back row cal

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
    val volFactor = (ln(1f + vol.toFloat()) / ln(31f)).coerceIn(0.2f, 1.4f) // 3x10 => ~1.0
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
    onOpenGroup: (String) -> Unit
) {
    val muscleScores = remember(days, profile) { computeMuscleScores(days, profile) }
    val groupScores = remember(muscleScores) { computeGroupScoresFromMuscles(muscleScores) }
    var showBack by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Muscle Group Scores", style = MaterialTheme.typography.titleMedium)

        var bf by remember { mutableStateOf(profile.bodyFatPct ?: 20f) }

        Text("Body fat: ${bf.toInt()}%")
        Slider(
            value = bf.coerceIn(0f, 100f),
            onValueChange = { bf = it.coerceIn(0f, 100f) },
            valueRange = 0f..100f
        )
        BodyScoreFigure3D(
            scores = groupScores,
            bodyFatPercent = bf
        )



        ScoreBars(scores = groupScores, onClickGroup = onOpenGroup)

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
private fun ScoreBars(
    scores: Map<String, Int>,
    onClickGroup: (String) -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MuscleGroup.entries.forEach { g ->
                val v = (scores[g.id] ?: 0).coerceIn(0, 100)

                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onClickGroup(g.id) },
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(g.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(v.toString())
                }
                LinearProgressIndicator(
                    progress = { v / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
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
