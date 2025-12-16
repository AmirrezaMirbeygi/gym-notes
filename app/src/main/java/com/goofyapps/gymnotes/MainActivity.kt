@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.goofyapps.gymnotes.ui.theme.GymNotesTheme
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorderAfterLongPress
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min
import androidx.compose.runtime.Composable


// ---------- DATA MODELS ----------

data class WorkoutDay(
    val id: Long = System.currentTimeMillis(),
    val groupId: String? = null,          // preset group id OR null
    val groupCustomName: String? = null,  // custom name if groupId is null
    val exercises: MutableList<ExerciseCard> = mutableListOf()
)

data class ExerciseCard(
    val id: Long = System.currentTimeMillis(),
    val equipmentName: String,
    val videoUri: String?,               // local URI as string
    val sets: Int,
    val reps: Int,
    val weight: Float?,
    val weightUnit: String = "kg",
    val primaryMuscleId: String? = null,          // preset muscle id OR null
    val primaryMuscleCustomName: String? = null,  // custom muscle name if primaryMuscleId is null
    val previousWeights: MutableList<Float> = mutableListOf(), // newest first
    val notes: String = ""
)

// ---------- NAV + TABS ----------

enum class Tab { WORKOUT, PROGRESS }

sealed class Screen {
    object DaysList : Screen()
    data class DayDetail(val dayIndex: Int) : Screen()
    data class ExerciseDetail(val dayIndex: Int, val exerciseIndex: Int) : Screen()
}

// ---------- ACTIVITY ----------

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GymNotesTheme {
                GymNotesApp()
            }
        }
    }
}

// ---------- PERSISTENCE (SharedPreferences + JSON) ----------

private const val PREFS_NAME = "gym_notes_prefs"
private const val KEY_DATA = "workout_data"

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

                    val prevArr = JSONArray()
                    ex.previousWeights.forEach { w -> prevArr.put(w.toDouble()) }
                    put("prevWeights", prevArr)
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

        // Backward compatibility
        val legacyName = if (dayObj.has("name")) dayObj.optString("name") else null
        val resolvedGroupCustomName = groupCustomName ?: legacyName

        val exercises = mutableListOf<ExerciseCard>()
        val exArr = dayObj.optJSONArray("exercises") ?: JSONArray()

        for (j in 0 until exArr.length()) {
            val exObj = exArr.getJSONObject(j)

            val prevArr = exObj.optJSONArray("prevWeights") ?: JSONArray()
            val prevList = mutableListOf<Float>()
            for (k in 0 until prevArr.length()) prevList.add(prevArr.getDouble(k).toFloat())

            val primaryMuscleId = exObj.optString("primaryMuscleId").takeIf { it.isNotBlank() }
            val primaryMuscleCustomName =
                exObj.optString("primaryMuscleCustomName").takeIf { it.isNotBlank() }

            val legacyTarget = if (exObj.has("targetMuscle")) exObj.optString("targetMuscle") else null
            val resolvedCustomMuscle = primaryMuscleCustomName ?: legacyTarget

            val ex = ExerciseCard(
                id = exObj.optLong("id"),
                equipmentName = exObj.getString("equipmentName"),
                videoUri = if (exObj.isNull("videoUri")) null else exObj.getString("videoUri"),
                sets = exObj.getInt("sets"),
                reps = exObj.getInt("reps"),
                weight = if (exObj.isNull("weight")) null else exObj.getDouble("weight").toFloat(),
                weightUnit = exObj.optString("weightUnit", "kg"),
                primaryMuscleId = primaryMuscleId,
                primaryMuscleCustomName = resolvedCustomMuscle,
                previousWeights = prevList,
                notes = exObj.optString("notes", "")
            )
            exercises.add(ex)
        }

        result.add(
            WorkoutDay(
                id = id,
                groupId = groupId,
                groupCustomName = resolvedGroupCustomName,
                exercises = exercises
            )
        )
    }

    return result
}

private fun saveDays(context: Context, days: List<WorkoutDay>) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putString(KEY_DATA, serializeDays(days)).apply()
}

private fun loadDays(context: Context): MutableList<WorkoutDay> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val json = prefs.getString(KEY_DATA, null) ?: return mutableListOf()
    return parseDaysFromJson(json)
}

// ---------- VIDEO THUMB + PLAYER ----------

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
fun VideoThumbnail(uriString: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bitmap by remember(uriString) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(uriString) {
        bitmap = runCatching {
            loadVideoThumbnail(context, Uri.parse(uriString))
        }.getOrNull()
    }

    bitmap?.let { bmp ->
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "Exercise video thumbnail",
            contentScale = ContentScale.Crop,
            modifier = modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(12.dp))
        )
    }
}

// ---------- GENERIC DROPDOWN ----------

@Composable
private fun <T> SimpleDropdown(
    label: String,
    options: List<T>,
    selected: T?,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = selected?.let(optionLabel) ?: "",
        onValueChange = {},
        label = { Text(label) },
        readOnly = true,
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = {
            IconButton(onClick = { expanded = true }) {
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
        }
    )

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
    ) {
        options.forEach { item ->
            DropdownMenuItem(
                text = { Text(optionLabel(item)) },
                onClick = {
                    onSelect(item)
                    expanded = false
                }
            )
        }
    }
}

// ---------- ROOT APP ----------

@Composable
fun GymNotesApp() {
    val context = LocalContext.current
    val days = remember { mutableStateListOf<WorkoutDay>().apply { addAll(loadDays(context)) } }

    var tab by remember { mutableStateOf(Tab.WORKOUT) }
    var screen by remember { mutableStateOf<Screen>(Screen.DaysList) }
    var showSettings by remember { mutableStateOf(false) }

    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            runCatching {
                val json = serializeDays(days)
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray())
                }
            }
        }

    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri ?: return@rememberLauncherForActivityResult
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (tab == Tab.WORKOUT) "Workout" else "Progress") },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == Tab.WORKOUT,
                    onClick = { tab = Tab.WORKOUT },
                    label = { Text("Workout") },
                    icon = { Text("🏋️") }
                )
                NavigationBarItem(
                    selected = tab == Tab.PROGRESS,
                    onClick = { tab = Tab.PROGRESS },
                    label = { Text("Progress") },
                    icon = { Text("📈") }
                )
            }
        }
    ) { padding ->

        Box(Modifier.padding(padding)) {
            when (tab) {
                Tab.WORKOUT -> {
                    when (val s = screen) {
                        is Screen.DaysList -> {
                            DaysListScreen(
                                days = days,
                                onDayClick = { idx -> screen = Screen.DayDetail(idx) },
                                onDaysChanged = { saveDays(context, days) },
                                onAddDayRequested = { groupId, customName ->
                                    val newDay = if (groupId == CUSTOM_ID) {
                                        WorkoutDay(groupId = null, groupCustomName = customName.trim())
                                    } else {
                                        WorkoutDay(groupId = groupId, groupCustomName = null)
                                    }
                                    days.add(newDay)
                                    saveDays(context, days)
                                }
                            )
                        }

                        is Screen.DayDetail -> {
                            val dayIdx = s.dayIndex
                            if (dayIdx !in days.indices) {
                                screen = Screen.DaysList
                            } else {
                                DayDetailScreen(
                                    day = days[dayIdx],
                                    onBack = { screen = Screen.DaysList },
                                    onExerciseClick = { exIdx -> screen = Screen.ExerciseDetail(dayIdx, exIdx) },
                                    onDayUpdated = { updated ->
                                        days[dayIdx] = updated
                                        saveDays(context, days)
                                    }
                                )
                            }
                        }

                        is Screen.ExerciseDetail -> {
                            val dayIdx = s.dayIndex
                            val exIdx = s.exerciseIndex
                            if (dayIdx !in days.indices || exIdx !in days[dayIdx].exercises.indices) {
                                screen = Screen.DaysList
                            } else {
                                val ctx = LocalContext.current
                                ExerciseDetailScreen(
                                    dayName = displayGroupName(days[dayIdx]),
                                    exercise = days[dayIdx].exercises[exIdx],
                                    onBack = { screen = Screen.DayDetail(dayIdx) },
                                    onSave = { updated ->
                                        val oldDay = days[dayIdx]
                                        val newExercises = oldDay.exercises.toMutableList()
                                        newExercises[exIdx] = updated
                                        days[dayIdx] = oldDay.copy(exercises = newExercises)
                                        saveDays(context, days)
                                        screen = Screen.DayDetail(dayIdx)
                                    },
                                    onPlayVideo = { uriString ->
                                        runCatching { openVideo(ctx, Uri.parse(uriString)) }
                                    }
                                )
                            }
                        }
                    }
                }

                Tab.PROGRESS -> {
                    ProgressScreen(days = days)
                }
            }
        }
    }

    if (showSettings) {
        SettingsDialog(
            onDismiss = { showSettings = false },
            onExport = { exportLauncher.launch("gym_notes_backup.json") },
            onImport = { importLauncher.launch(arrayOf("application/json", "text/*")) },
            onClearAll = {
                days.clear()
                saveDays(context, days)
            }
        )
    }
}

// ---------- SETTINGS ----------

@Composable
fun SettingsDialog(
    onDismiss: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onClearAll: () -> Unit
) {
    var showClearConfirm by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(modifier = Modifier.fillMaxWidth(), onClick = onExport) { Text("Export data") }
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        onImport()
                        onDismiss()
                    }
                ) { Text("Import data") }

                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showClearConfirm = true }
                ) { Text("Clear all data") }

                // TODO: Move Profile into gear menu (stub)
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { /* TODO: Profile screen */ }
                ) { Text("Profile") }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear all data?") },
            text = { Text("This will delete all days and exercises. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    onClearAll()
                    showClearConfirm = false
                    onDismiss()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

// ---------- DAYS LIST ----------

@Composable
fun DaysListScreen(
    days: MutableList<WorkoutDay>,
    onDayClick: (Int) -> Unit,
    onDaysChanged: () -> Unit,
    onAddDayRequested: (groupId: String, customName: String) -> Unit
) {
    var showAddDayDialog by remember { mutableStateOf(false) }
    var showDayDeleteConfirm by remember { mutableStateOf(false) }
    var dayToDeleteIndex by remember { mutableStateOf<Int?>(null) }

    val reorderState = rememberReorderableLazyListState(
        onMove = { from, to ->
            val temp = days.toMutableList()
            val item = temp.removeAt(from.index)
            temp.add(to.index, item)
            days.clear()
            days.addAll(temp)
            onDaysChanged()
        }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Muscle Groups", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { showAddDayDialog = true }) { Text("Add") }
        }

        if (days.isEmpty()) {
            Text(
                text = "No days yet. Add one to start.",
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .reorderable(reorderState)
                    .detectReorderAfterLongPress(reorderState),
                state = reorderState.listState
            ) {
                itemsIndexed(items = days, key = { _, day -> day.id }) { index, day ->
                    ReorderableItem(state = reorderState, key = day.id) {
                        DayRow(
                            day = day,
                            onClick = { onDayClick(index) },
                            onDelete = {
                                dayToDeleteIndex = index
                                showDayDeleteConfirm = true
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddDayDialog) {
        AddDayDialog(
            onDismiss = { showAddDayDialog = false },
            onAddDay = { groupId, customName ->
                onAddDayRequested(groupId, customName)
                showAddDayDialog = false
            }
        )
    }

    if (showDayDeleteConfirm && dayToDeleteIndex != null) {
        AlertDialog(
            onDismissRequest = {
                showDayDeleteConfirm = false
                dayToDeleteIndex = null
            },
            title = { Text("Delete day?") },
            text = { Text("Delete this day and all its exercises?") },
            confirmButton = {
                TextButton(onClick = {
                    val idx = dayToDeleteIndex!!
                    if (idx in days.indices) {
                        days.removeAt(idx)
                        onDaysChanged()
                    }
                    showDayDeleteConfirm = false
                    dayToDeleteIndex = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDayDeleteConfirm = false
                    dayToDeleteIndex = null
                }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun DayRow(day: WorkoutDay, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayGroupName(day),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Exercises: ${day.exercises.size}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete day")
            }
        }
    }
}

// ---------- DAY DETAIL ----------

@Composable
fun DayDetailScreen(
    day: WorkoutDay,
    onBack: () -> Unit,
    onExerciseClick: (Int) -> Unit,
    onDayUpdated: (WorkoutDay) -> Unit
) {
    var showAddExerciseDialog by remember { mutableStateOf(false) }
    var showExerciseDeleteConfirm by remember { mutableStateOf(false) }
    var exerciseToDeleteIndex by remember { mutableStateOf<Int?>(null) }

    val reorderState = rememberReorderableLazyListState(
        onMove = { from, to ->
            val temp = day.exercises.toMutableList()
            val item = temp.removeAt(from.index)
            temp.add(to.index, item)
            onDayUpdated(day.copy(exercises = temp))
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(displayGroupName(day)) },
                navigationIcon = { IconButton(onClick = onBack) { Text("<") } }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddExerciseDialog = true }) { Text("+") }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp)
        ) {
            if (day.exercises.isEmpty()) {
                Text("No exercises yet. Tap + to add.", style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .reorderable(reorderState)
                        .detectReorderAfterLongPress(reorderState),
                    state = reorderState.listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(items = day.exercises, key = { _, ex -> ex.id }) { index, ex ->
                        ReorderableItem(state = reorderState, key = ex.id) {
                            ExerciseCardView(
                                ex = ex,
                                onClick = { onExerciseClick(index) },
                                onDelete = {
                                    exerciseToDeleteIndex = index
                                    showExerciseDeleteConfirm = true
                                }
                            )
                        }
                    }
                }
            }
        }

        if (showAddExerciseDialog) {
            AddExerciseDialog(
                onDismiss = { showAddExerciseDialog = false },
                onAddExercise = { exercise ->
                    val newList = day.exercises.toMutableList()
                    newList.add(exercise)
                    onDayUpdated(day.copy(exercises = newList))
                    showAddExerciseDialog = false
                }
            )
        }

        if (showExerciseDeleteConfirm && exerciseToDeleteIndex != null) {
            AlertDialog(
                onDismissRequest = {
                    showExerciseDeleteConfirm = false
                    exerciseToDeleteIndex = null
                },
                title = { Text("Delete exercise?") },
                text = { Text("Delete this exercise card?") },
                confirmButton = {
                    TextButton(onClick = {
                        val idx = exerciseToDeleteIndex!!
                        if (idx in day.exercises.indices) {
                            val newList = day.exercises.toMutableList()
                            newList.removeAt(idx)
                            onDayUpdated(day.copy(exercises = newList))
                        }
                        showExerciseDeleteConfirm = false
                        exerciseToDeleteIndex = null
                    }) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showExerciseDeleteConfirm = false
                        exerciseToDeleteIndex = null
                    }) { Text("Cancel") }
                }
            )
        }
    }
}

// ---------- EXERCISE CARD ----------

@Composable
fun ExerciseCardView(ex: ExerciseCard, onClick: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            ex.videoUri?.let { uriStr ->
                VideoThumbnail(
                    uriString = uriStr,
                    modifier = Modifier.clickable {
                        runCatching { openVideo(context, Uri.parse(uriStr)) }
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = ex.equipmentName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Primary Muscle: ${displayPrimaryMuscle(ex)}")
                    Text(text = "Sets x Reps: ${ex.sets} x ${ex.reps}")
                    ex.weight?.let { Text(text = "Weight: $it ${ex.weightUnit}") }
                    if (ex.notes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = ex.notes,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete exercise")
                }
            }
        }
    }
}

// ---------- EXERCISE DETAIL ----------

@Composable
fun ExerciseDetailScreen(
    dayName: String,
    exercise: ExerciseCard,
    onBack: () -> Unit,
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

    // dropdown state
    val presetMuscle = remember(exercise.primaryMuscleId) {
        exercise.primaryMuscleId?.let { id -> Muscle.entries.firstOrNull { it.id == id } }
    }
    var selectedGroup by remember { mutableStateOf<MuscleGroup?>(presetMuscle?.let { m -> MuscleGroup.entries.firstOrNull { it.id == m.groupId } }) }
    var selectedMuscle by remember { mutableStateOf<Muscle?>(presetMuscle) }
    var isCustomMuscle by remember { mutableStateOf(exercise.primaryMuscleId == null) }
    var customMuscleName by remember { mutableStateOf(exercise.primaryMuscleCustomName ?: "") }

    val videoPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri ?: return@rememberLauncherForActivityResult
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            videoUri = uri.toString()
        }

    val sets = setsText.toIntOrNull() ?: 0
    val reps = repsText.toIntOrNull() ?: 0
    val weight = weightText.toFloatOrNull()

    val isValid = remember(equipmentName, setsText, repsText, isCustomMuscle, selectedMuscle, customMuscleName) {
        val baseOk = equipmentName.isNotBlank() && sets > 0 && reps > 0
        val muscleOk = if (isCustomMuscle) customMuscleName.isNotBlank() else selectedMuscle != null
        baseOk && muscleOk
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit: $dayName") },
                navigationIcon = { IconButton(onClick = onBack) { Text("<") } },
                actions = {
                    TextButton(
                        enabled = isValid,
                        onClick = {
                            val newPrev = mutableListOf<Float>().apply {
                                if (exercise.weight != null && exercise.weight != weight) add(exercise.weight)
                                addAll(exercise.previousWeights)
                            }
                            if (newPrev.size > 5) newPrev.subList(5, newPrev.size).clear()

                            val updated = exercise.copy(
                                equipmentName = equipmentName.trim(),
                                sets = sets,
                                reps = reps,
                                weight = weight,
                                previousWeights = newPrev,
                                notes = notes.trim(),
                                videoUri = videoUri,
                                primaryMuscleId = if (isCustomMuscle) null else selectedMuscle?.id,
                                primaryMuscleCustomName = if (isCustomMuscle) customMuscleName.trim() else null
                            )
                            onSave(updated)
                        }
                    ) { Text("Save") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            videoUri?.let { uriStr ->
                VideoThumbnail(
                    uriString = uriStr,
                    modifier = Modifier.clickable { onPlayVideo(uriStr) }
                )
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
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Checkbox(checked = isCustomMuscle, onCheckedChange = { checked ->
                    isCustomMuscle = checked
                    if (!checked) customMuscleName = ""
                })
                Text("Custom primary muscle")
            }

            if (isCustomMuscle) {
                OutlinedTextField(
                    value = customMuscleName,
                    onValueChange = { customMuscleName = it },
                    label = { Text("Primary Muscle") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                SimpleDropdown(
                    label = "Muscle Group",
                    options = MuscleGroup.entries,
                    selected = selectedGroup,
                    optionLabel = { it.displayName },
                    onSelect = {
                        selectedGroup = it
                        selectedMuscle = null
                    }
                )

                SimpleDropdown(
                    label = "Primary Muscle",
                    options = selectedGroup?.let { musclesForGroup(it.id) } ?: emptyList(),
                    selected = selectedMuscle,
                    optionLabel = { it.displayName },
                    onSelect = { selectedMuscle = it }
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = setsText,
                    onValueChange = { setsText = it.filter { c -> c.isDigit() } },
                    label = { Text("Sets") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = repsText,
                    onValueChange = { repsText = it.filter { c -> c.isDigit() } },
                    label = { Text("Reps") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }

            OutlinedTextField(
                value = weightText,
                onValueChange = { new ->
                    if (new.all { it.isDigit() || it == '.' }) weightText = new
                },
                label = { Text("Weight (${exercise.weightUnit})") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                maxLines = 6
            )
        }
    }
}

// ---------- DIALOGS ----------

@Composable
fun AddDayDialog(onDismiss: () -> Unit, onAddDay: (groupId: String, customName: String) -> Unit) {
    val groupOptions = remember { listOf(CUSTOM_ID) + MuscleGroup.entries.map { it.id } }

    fun labelFor(id: String): String =
        if (id == CUSTOM_ID) "Custom" else MuscleGroup.entries.firstOrNull { it.id == id }?.displayName ?: "Unknown"

    var selectedGroupId by remember { mutableStateOf(MuscleGroup.entries.first().id) }
    var customName by remember { mutableStateOf("") }

    val isCustom = selectedGroupId == CUSTOM_ID
    val isValid = if (isCustom) customName.isNotBlank() else true

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Muscle Group") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SimpleDropdown(
                    label = "Muscle Group",
                    options = groupOptions,
                    selected = selectedGroupId,
                    optionLabel = { labelFor(it) },
                    onSelect = { selectedGroupId = it }
                )

                if (isCustom) {
                    OutlinedTextField(
                        value = customName,
                        onValueChange = { customName = it },
                        label = { Text("Custom name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = isValid, onClick = { onAddDay(selectedGroupId, customName) }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun AddExerciseDialog(onDismiss: () -> Unit, onAddExercise: (ExerciseCard) -> Unit) {
    val context = LocalContext.current

    var equipmentName by remember { mutableStateOf("") }
    var setsText by remember { mutableStateOf("3") }
    var repsText by remember { mutableStateOf("10") }
    var weightText by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var videoUri by remember { mutableStateOf<Uri?>(null) }

    var isCustomMuscle by remember { mutableStateOf(false) }
    var customMuscleName by remember { mutableStateOf("") }
    var selectedGroup by remember { mutableStateOf<MuscleGroup?>(MuscleGroup.entries.firstOrNull()) }
    var selectedMuscle by remember { mutableStateOf<Muscle?>(null) }

    val videoPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri ?: return@rememberLauncherForActivityResult
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            videoUri = uri
        }

    val sets = setsText.toIntOrNull() ?: 0
    val reps = repsText.toIntOrNull() ?: 0
    val weight = weightText.toFloatOrNull()

    val isValid = remember(equipmentName, setsText, repsText, isCustomMuscle, selectedMuscle, customMuscleName) {
        val baseOk = equipmentName.isNotBlank() && sets > 0 && reps > 0
        val muscleOk = if (isCustomMuscle) customMuscleName.isNotBlank() else selectedMuscle != null
        baseOk && muscleOk
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
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Checkbox(checked = isCustomMuscle, onCheckedChange = { checked ->
                        isCustomMuscle = checked
                        if (!checked) customMuscleName = ""
                    })
                    Text("Custom primary muscle")
                }

                if (isCustomMuscle) {
                    OutlinedTextField(
                        value = customMuscleName,
                        onValueChange = { customMuscleName = it },
                        label = { Text("Primary Muscle") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    SimpleDropdown(
                        label = "Muscle Group",
                        options = MuscleGroup.entries,
                        selected = selectedGroup,
                        optionLabel = { it.displayName },
                        onSelect = {
                            selectedGroup = it
                            selectedMuscle = null
                        }
                    )

                    SimpleDropdown(
                        label = "Primary Muscle",
                        options = selectedGroup?.let { musclesForGroup(it.id) } ?: emptyList(),
                        selected = selectedMuscle,
                        optionLabel = { it.displayName },
                        onSelect = { selectedMuscle = it }
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        modifier = Modifier.weight(1f),
                        value = setsText,
                        onValueChange = { setsText = it.filter { c -> c.isDigit() } },
                        label = { Text("Sets") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        modifier = Modifier.weight(1f),
                        value = repsText,
                        onValueChange = { repsText = it.filter { c -> c.isDigit() } },
                        label = { Text("Reps") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                OutlinedTextField(
                    value = weightText,
                    onValueChange = { new -> if (new.all { it.isDigit() || it == '.' }) weightText = new },
                    label = { Text("Weight (kg)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp)
                )

                Button(onClick = { videoPickerLauncher.launch(arrayOf("video/*")) }) {
                    Text(if (videoUri == null) "Pick video" else "Change video")
                }

                Text(
                    text = if (videoUri != null) "Video selected" else "No video selected",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = {
                    val ex = ExerciseCard(
                        equipmentName = equipmentName.trim(),
                        videoUri = videoUri?.toString(),
                        sets = sets,
                        reps = reps,
                        weight = weight,
                        weightUnit = "kg",
                        notes = notes.trim(),
                        primaryMuscleId = if (isCustomMuscle) null else selectedMuscle?.id,
                        primaryMuscleCustomName = if (isCustomMuscle) customMuscleName.trim() else null
                    )
                    onAddExercise(ex)
                }
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ---------- PROGRESS TAB ----------
// TODO: In Progress tab have an image of body with score per muscle group,
// and body shape changes as each muscle group score increases.

@Composable
fun ProgressScreen(days: List<WorkoutDay>) {
    val scores = remember(days) { computeGroupScores(days) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Muscle Group Scores", style = MaterialTheme.typography.titleMedium)

        BodyScoreFigure(scores = scores)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MuscleGroup.entries.forEach { g ->
                    val s = scores[g.id] ?: 0
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(g.displayName)
                        Text("$s")
                    }
                    LinearProgressIndicator(
                        progress = (s.coerceIn(0, 100) / 100f),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

private fun computeGroupScores(days: List<WorkoutDay>): Map<String, Int> {
    // Simple scoring:
    // volume = sets * reps * (weight or 1)
    // normalized per group into 0..100 using max group volume
    val groupVolume = mutableMapOf<String, Float>()

    for (d in days) {
        for (ex in d.exercises) {
            val muscleId = ex.primaryMuscleId ?: continue
            val muscle = Muscle.entries.firstOrNull { it.id == muscleId } ?: continue
            val groupId = muscle.groupId

            val w = ex.weight ?: 1f
            val vol = (ex.sets * ex.reps).toFloat() * max(1f, w)
            groupVolume[groupId] = (groupVolume[groupId] ?: 0f) + vol
        }
    }

    val maxVol = groupVolume.values.maxOrNull() ?: 0f
    if (maxVol <= 0f) return MuscleGroup.entries.associate { it.id to 0 }

    return MuscleGroup.entries.associate { g ->
        val v = groupVolume[g.id] ?: 0f
        val score = ((v / maxVol) * 100f).toInt().coerceIn(0, 100)
        g.id to score
    }
}

@Composable
fun BodyScoreFigure(scores: Map<String, Int>) {
    // ✅ Read theme colors OUTSIDE Canvas (Canvas draw block is not composable)
    val onSurface = MaterialTheme.colorScheme.onSurface
    val headColor = onSurface.copy(alpha = 0.20f)
    val bodyColor = onSurface.copy(alpha = 0.18f)
    val limbColor = onSurface.copy(alpha = 0.20f)

    Card(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val cx = w / 2f

                fun s(id: String): Float = (scores[id] ?: 0) / 100f

                val chestScale = 1f + s("chest") * 0.35f
                val shoulderScale = 1f + s("shoulders") * 0.40f
                val armScale = 1f + s("arms") * 0.35f
                val legScale = 1f + s("legs") * 0.40f
                val coreScale = 1f + s("core") * 0.25f
                val backScale = 1f + s("back") * 0.25f

                val headR = min(w, h) * 0.06f
                val shoulderY = (headR * 2.4f) + (headR * 0.7f)
                val waistY = h * 0.62f
                val hipY = h * 0.72f
                val footY = h * 0.95f

                val baseShoulderHalf = w * 0.18f
                val baseChestHalf = w * 0.16f
                val baseWaistHalf = w * 0.13f
                val baseHipHalf = w * 0.15f

                val shoulderHalf = baseShoulderHalf * shoulderScale
                val chestHalf = baseChestHalf * max(chestScale, backScale)
                val waistHalf = baseWaistHalf * coreScale
                val hipHalf = baseHipHalf * legScale

                val armX = shoulderHalf + w * 0.06f
                val armTopY = shoulderY + headR * 0.2f
                val armBottomY = waistY
                val armThickness = w * 0.03f * armScale

                val legGap = w * 0.04f
                val legHalf = (hipHalf - legGap) / 2f
                val legThickness = legHalf * legScale

                // Head
                drawCircle(
                    color = headColor,
                    radius = headR,
                    center = Offset(cx, headR * 1.3f)
                )

                // Torso
                val torso = Path().apply {
                    moveTo(cx - shoulderHalf, shoulderY)
                    lineTo(cx + shoulderHalf, shoulderY)
                    lineTo(cx + chestHalf, h * 0.44f)
                    lineTo(cx + waistHalf, waistY)
                    lineTo(cx + hipHalf, hipY)
                    lineTo(cx - hipHalf, hipY)
                    lineTo(cx - waistHalf, waistY)
                    lineTo(cx - chestHalf, h * 0.44f)
                    close()
                }

                drawPath(path = torso, color = bodyColor)

                // Arms
                drawLine(
                    color = limbColor,
                    start = Offset(cx - armX, armTopY),
                    end = Offset(cx - armX, armBottomY),
                    strokeWidth = armThickness
                )
                drawLine(
                    color = limbColor,
                    start = Offset(cx + armX, armTopY),
                    end = Offset(cx + armX, armBottomY),
                    strokeWidth = armThickness
                )

                // Legs
                val leftLegX = cx - legGap / 2f - legHalf
                val rightLegX = cx + legGap / 2f + legHalf

                drawLine(
                    color = limbColor,
                    start = Offset(leftLegX, hipY),
                    end = Offset(leftLegX, footY),
                    strokeWidth = legThickness
                )
                drawLine(
                    color = limbColor,
                    start = Offset(rightLegX, hipY),
                    end = Offset(rightLegX, footY),
                    strokeWidth = legThickness
                )
            }
        }
    }
}



// ---------- HELPERS ----------

private fun displayGroupName(day: WorkoutDay): String {
    val preset = day.groupId?.let { id ->
        MuscleGroup.entries.firstOrNull { it.id == id }?.displayName
    }
    return preset ?: day.groupCustomName ?: "Unassigned"
}

private fun displayPrimaryMuscle(ex: ExerciseCard): String {
    val preset = ex.primaryMuscleId?.let { id ->
        Muscle.entries.firstOrNull { it.id == id }?.displayName
    }
    return preset ?: ex.primaryMuscleCustomName ?: "Unassigned"
}

// ---------- PREVIEW ----------

@Preview(showBackground = true)
@Composable
fun GymNotesPreview() {
    GymNotesTheme { GymNotesApp() }
}
