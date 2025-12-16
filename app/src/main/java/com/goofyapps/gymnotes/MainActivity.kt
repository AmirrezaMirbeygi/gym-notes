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
    val weightHistory: MutableList<WeightEntry> = mutableListOf(), // ✅ timestamped
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

// Profile keys
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

                    // ✅ timestamped history
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

            // ✅ new history format
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

            // Backward compat: if no history, seed from current weight (one entry) so charts work.
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
                IconButton(onClick = { gearOpen = true }) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                }
                DropdownMenu(expanded = gearOpen, onDismissRequest = { gearOpen = false }) {
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
                    onOpenGroup = { gid -> screen = Screen.ProgressGroupDetail(gid) }
                )

                is Screen.ProgressGroupDetail -> ProgressGroupDetailScreen(
                    groupId = s.groupId,
                    days = days
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
                    label = { Text("Weight (kg)") },
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

// -------------------- WORKOUT HOME (Groups reorder, NO DOTS) --------------------

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
                                    .detectReorder(reorderState) // drag anywhere
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

// -------------------- DAY DETAIL (Exercises reorder, NO DOTS) --------------------

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
                                    Text(ex.equipmentName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

// -------------------- ADD EXERCISE (seed first history entry) --------------------

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

                if (!parentGroupId.isNullOrBlank()) {
                    val groupName = MuscleGroup.entries.firstOrNull { it.id == parentGroupId }?.displayName ?: "Group"
                    Text("Group: $groupName", style = MaterialTheme.typography.bodySmall)
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

// -------------------- EXERCISE EDIT (append history ONLY if weight changed) --------------------

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
        Modifier.fillMaxSize().padding(12.dp),
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

                // ✅ only append when changed (and not null)
                val oldWeight = exercise.weight
                if (newWeight != null && (oldWeight == null || newWeight != oldWeight)) {
                    // Also avoid duplicating the same weight as the last entry
                    val last = hist.lastOrNull()
                    if (last == null || last.w != newWeight) {
                        hist.add(WeightEntry(System.currentTimeMillis(), newWeight))
                    }
                    // hard cap to avoid bloat
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

// -------------------- PROGRESS --------------------

@Composable
private fun ProgressScreen(
    days: List<WorkoutDay>,
    onOpenGroup: (String) -> Unit
) {
    val scores = remember(days) { computeGroupScores(days) }
    var showBack by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Muscle Group Scores", style = MaterialTheme.typography.titleMedium)

        BodyScoreFigureHybrid(
            scores = scores,
            showBack = showBack,
            onToggleSide = { showBack = !showBack }
        )

        ScoreBars(
            scores = scores,
            onClickGroup = onOpenGroup
        )
    }
}
// -------------------- SCORE CALIBRATION (10th→90th linear) --------------------

private data class PercentileCal(val p10Kg: Float, val p90Kg: Float)

/**
 * Uses your table:
 * score = 0 if x<=p10
 * score = 100 if x>=p90
 * else score = 10 + 80*(x-p10)/(p90-p10)
 *
 * NOTE: values are in KG and are MALE benchmarks.
 */
private val MUSCLE_GROUP_CAL = mapOf(
    // groupId must match your MuscleGroup ids
    "chest" to PercentileCal(61f, 166f),        // 135–365 lb
    "quads" to PercentileCal(61f, 204f),        // 135–450 lb
    "back" to PercentileCal(52f, 127f),         // Bent-over row 115–280 lb (mapped to "back")
    "lats" to PercentileCal(45f, 132f),         // 100–290 lb (only used if you actually have "lats" group)
    "shoulders" to PercentileCal(30f, 91f),     // 65–200 lb
    "triceps" to PercentileCal(61f, 136f),      // 135–300 lb
    "biceps" to PercentileCal(25f, 79f),        // 55–175 lb
    "calves" to PercentileCal(82f, 295f),       // 180–650 lb
    "core" to PercentileCal(27f, 180f),         // 60–220 lb
    "forearms" to PercentileCal(34f, 82f),      // 75–180 lb

    // Your UI uses "legs" (not "quads"). We'll map legs to quads calibration for now.
    "legs" to PercentileCal(61f, 204f),

    // Your UI uses "arms" (not biceps/triceps). We'll map arms to a blended-ish calibration.
    // For now, pick mid of biceps+triceps 10th and 90th.
    "arms" to PercentileCal((25f + 61f) / 2f, (79f + 136f) / 2f)
)

private fun estimate1RM_Epley(weightKg: Float, reps: Int): Float {
    val r = reps.coerceIn(1, 30)
    return weightKg * (1f + r / 30f)
}

private fun scoreFrom10to90(oneRmKg: Float, cal: PercentileCal): Int {
    val x = oneRmKg
    val p10 = cal.p10Kg
    val p90 = cal.p90Kg

    return when {
        x <= p10 -> 0
        x >= p90 -> 100
        else -> {
            val t = (x - p10) / (p90 - p10)  // 0..1 between 10th and 90th
            (10f + 80f * t).toInt().coerceIn(0, 100)
        }
    }
}

private fun computeGroupScores(days: List<WorkoutDay>): Map<String, Int> {
    // Best (max) estimated 1RM per groupId
    val best1RmByGroup = mutableMapOf<String, Float>()

    for (d in days) {
        for (ex in d.exercises) {
            val groupId = ex.primaryMuscleId?.let { id ->
                Muscle.entries.firstOrNull { it.id == id }?.groupId
            } ?: d.groupId

            if (groupId.isNullOrBlank()) continue

            val cal = MUSCLE_GROUP_CAL[groupId] ?: continue

            val w = ex.weight ?: continue
            if (w <= 0f || ex.reps <= 0) continue

            val oneRm = estimate1RM_Epley(w, ex.reps)
            val prevBest = best1RmByGroup[groupId] ?: 0f
            if (oneRm > prevBest) best1RmByGroup[groupId] = oneRm
        }
    }

    // Produce a score for every MuscleGroup entry (default 0 if no data / no calibration)
    return MuscleGroup.entries.associate { g ->
        val cal = MUSCLE_GROUP_CAL[g.id]
        val oneRm = best1RmByGroup[g.id] ?: 0f
        val score = if (cal == null) 0 else scoreFrom10to90(oneRm, cal)
        g.id to score
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

// -------------------- PROGRESS GROUP DETAIL (muscles + charts) --------------------

@Composable
private fun ProgressGroupDetailScreen(
    groupId: String,
    days: List<WorkoutDay>
) {
    val muscles = remember(groupId) { musclesForGroup(groupId) }

    Column(
        Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Muscles", style = MaterialTheme.typography.titleMedium)

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(muscles, key = { it.id }) { m ->
                val entries = remember(days, m.id) { collectWeightHistoryForMuscle(days, m.id) }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(m.displayName, style = MaterialTheme.typography.titleMedium)
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
            if (ex.primaryMuscleId == muscleId) {
                all.addAll(ex.weightHistory)
            }
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

        // axes baseline
        drawLine(
            color = onSurface.copy(alpha = 0.15f),
            start = Offset(pad, h - pad),
            end = Offset(w - pad, h - pad),
            strokeWidth = 2f
        )

        // line
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

        // points
        entries.forEach { e ->
            drawCircle(
                color = onSurface.copy(alpha = 0.85f),
                radius = 4f,
                center = Offset(x(e.t), y(e.w))
            )
        }
    }
}

// -------------------- FIGURE --------------------

@Composable
private fun BodyScoreFigureHybrid(
    scores: Map<String, Int>,
    showBack: Boolean,
    onToggleSide: () -> Unit
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val primary = MaterialTheme.colorScheme.primary

    Card(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(240.dp)
                .padding(12.dp)
        ) {
            Canvas(Modifier.fillMaxSize().align(Alignment.Center)) {
                val w = size.width
                val h = size.height
                val cx = w / 2f

                fun score01(id: String): Float = ((scores[id] ?: 0).coerceIn(0, 100) / 100f)

                val chest = score01("chest")
                val back = score01("back")
                val shoulders = score01("shoulders")
                val arms = score01("arms")
                val legs = score01("legs")
                val core = score01("core")

                val shoulderScale = 1f + shoulders * 0.35f
                val chestBackScale = 1f + max(chest, back) * 0.25f
                val waistScale = 1f - core * 0.15f
                val armScale = 1f + arms * 0.30f
                val legScale = 1f + legs * 0.30f

                val headR = min(w, h) * 0.06f
                val shoulderY = headR * 3.2f
                val chestY = h * 0.44f
                val waistY = h * 0.62f
                val hipY = h * 0.72f
                val footY = h * 0.95f

                val baseShoulderHalf = w * 0.18f
                val baseChestHalf = w * 0.16f
                val baseWaistHalf = w * 0.13f
                val baseHipHalf = w * 0.15f

                val shoulderHalf = baseShoulderHalf * shoulderScale
                val chestHalf = baseChestHalf * chestBackScale
                val waistHalf = baseWaistHalf * waistScale
                val hipHalf = baseHipHalf * legScale

                val armX = shoulderHalf + w * 0.06f
                val armTopY = shoulderY + headR * 0.2f
                val armBottomY = waistY
                val armThickness = w * 0.03f * armScale

                val legGap = w * 0.05f
                val legHalf = max(1f, (hipHalf - legGap) / 2f)
                val legThickness = legHalf * 0.90f

                drawCircle(
                    color = onSurface.copy(alpha = 0.18f),
                    radius = headR,
                    center = Offset(cx, headR * 1.3f)
                )

                val torsoPath = Path().apply {
                    moveTo(cx - shoulderHalf, shoulderY)
                    lineTo(cx + shoulderHalf, shoulderY)
                    lineTo(cx + chestHalf, chestY)
                    lineTo(cx + waistHalf, waistY)
                    lineTo(cx + hipHalf, hipY)
                    lineTo(cx - hipHalf, hipY)
                    lineTo(cx - waistHalf, waistY)
                    lineTo(cx - chestHalf, chestY)
                    close()
                }

                drawPath(torsoPath, onSurface.copy(alpha = 0.12f), style = Fill)
                drawPath(torsoPath, onSurface.copy(alpha = 0.18f), style = Stroke(width = 2f))

                drawLine(
                    color = onSurface.copy(alpha = 0.16f),
                    start = Offset(cx - armX, armTopY),
                    end = Offset(cx - armX, armBottomY),
                    strokeWidth = armThickness
                )
                drawLine(
                    color = onSurface.copy(alpha = 0.16f),
                    start = Offset(cx + armX, armTopY),
                    end = Offset(cx + armX, armBottomY),
                    strokeWidth = armThickness
                )

                val leftLegX = cx - legGap / 2f - legHalf
                val rightLegX = cx + legGap / 2f + legHalf

                drawLine(
                    color = onSurface.copy(alpha = 0.16f),
                    start = Offset(leftLegX, hipY),
                    end = Offset(leftLegX, footY),
                    strokeWidth = legThickness
                )
                drawLine(
                    color = onSurface.copy(alpha = 0.16f),
                    start = Offset(rightLegX, hipY),
                    end = Offset(rightLegX, footY),
                    strokeWidth = legThickness
                )

                fun heatAlpha(v: Float): Float = (0.06f + v * 0.22f).coerceIn(0.06f, 0.30f)

                val emphasizeChest = !showBack

                val shoulderPath = Path().apply {
                    moveTo(cx - shoulderHalf, shoulderY)
                    lineTo(cx + shoulderHalf, shoulderY)
                    lineTo(cx + chestHalf, chestY)
                    lineTo(cx - chestHalf, chestY)
                    close()
                }
                drawPath(shoulderPath, primary.copy(alpha = heatAlpha(shoulders)), style = Fill)

                val chestBackPath = Path().apply {
                    moveTo(cx - chestHalf, chestY)
                    lineTo(cx + chestHalf, chestY)
                    lineTo(cx + waistHalf, waistY)
                    lineTo(cx - waistHalf, waistY)
                    close()
                }
                drawPath(
                    chestBackPath,
                    primary.copy(alpha = heatAlpha(if (emphasizeChest) chest else back)),
                    style = Fill
                )

                val corePath = Path().apply {
                    moveTo(cx - waistHalf, waistY)
                    lineTo(cx + waistHalf, waistY)
                    lineTo(cx + hipHalf, hipY)
                    lineTo(cx - hipHalf, hipY)
                    close()
                }
                drawPath(corePath, primary.copy(alpha = heatAlpha(core)), style = Fill)

                drawLine(
                    color = primary.copy(alpha = heatAlpha(arms)),
                    start = Offset(cx - armX, armTopY),
                    end = Offset(cx - armX, armBottomY),
                    strokeWidth = armThickness * 1.05f
                )
                drawLine(
                    color = primary.copy(alpha = heatAlpha(arms)),
                    start = Offset(cx + armX, armTopY),
                    end = Offset(cx + armX, armBottomY),
                    strokeWidth = armThickness * 1.05f
                )

                drawLine(
                    color = primary.copy(alpha = heatAlpha(legs)),
                    start = Offset(leftLegX, hipY),
                    end = Offset(leftLegX, footY),
                    strokeWidth = legThickness * 1.05f
                )
                drawLine(
                    color = primary.copy(alpha = heatAlpha(legs)),
                    start = Offset(rightLegX, hipY),
                    end = Offset(rightLegX, footY),
                    strokeWidth = legThickness * 1.05f
                )
            }

            IconButton(
                onClick = onToggleSide,
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Icon(
                    imageVector = Icons.Filled.FlipCameraAndroid,
                    contentDescription = if (showBack) "Show front" else "Show back"
                )
            }

            // If you want ZERO text, delete this.
            Text(
                text = if (showBack) "Back" else "Front",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 2.dp)
                    .alpha(0.65f)
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
