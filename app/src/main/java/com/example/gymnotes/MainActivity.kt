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
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymnotes.ui.theme.GymNotesTheme
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorderAfterLongPress
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable
import org.json.JSONArray
import org.json.JSONObject

// ---------- DATA MODELS ----------

data class WorkoutDay(
    val id: Long = System.currentTimeMillis(),
    val name: String,
    val exercises: MutableList<ExerciseCard> = mutableListOf()
)

data class ExerciseCard(
    val id: Long = System.currentTimeMillis(),
    val equipmentName: String,
    val videoUri: String?,          // local URI as string
    val sets: Int,
    val reps: Int,
    val weight: Float?,
    val weightUnit: String = "kg",
    val targetMuscle: String,
    val previousWeights: MutableList<Float> = mutableListOf(), // newest first
    val notes: String = ""
)

// ---------- NAV ----------

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
            put("name", day.name)

            val exArr = JSONArray()
            for (ex in day.exercises) {
                val exObj = JSONObject().apply {
                    put("id", ex.id)
                    put("equipmentName", ex.equipmentName)
                    put("videoUri", ex.videoUri) // may be null
                    put("sets", ex.sets)
                    put("reps", ex.reps)
                    if (ex.weight != null) {
                        put("weight", ex.weight.toDouble())
                    } else {
                        put("weight", JSONObject.NULL)
                    }
                    put("weightUnit", ex.weightUnit)
                    put("targetMuscle", ex.targetMuscle)
                    put("notes", ex.notes)

                    val prevArr = JSONArray()
                    ex.previousWeights.forEach { w ->
                        prevArr.put(w.toDouble())
                    }
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
        val name = dayObj.getString("name")

        val exercises = mutableListOf<ExerciseCard>()
        val exArr = dayObj.optJSONArray("exercises") ?: JSONArray()
        for (j in 0 until exArr.length()) {
            val exObj = exArr.getJSONObject(j)

            val prevArr = exObj.optJSONArray("prevWeights") ?: JSONArray()
            val prevList = mutableListOf<Float>()
            for (k in 0 until prevArr.length()) {
                prevList.add(prevArr.getDouble(k).toFloat())
            }

            val ex = ExerciseCard(
                id = exObj.optLong("id"),
                equipmentName = exObj.getString("equipmentName"),
                videoUri = if (exObj.isNull("videoUri")) null else exObj.getString("videoUri"),
                sets = exObj.getInt("sets"),
                reps = exObj.getInt("reps"),
                weight = if (exObj.isNull("weight")) null else exObj.getDouble("weight").toFloat(),
                weightUnit = exObj.optString("weightUnit", "kg"),
                targetMuscle = exObj.getString("targetMuscle"),
                previousWeights = prevList,
                notes = exObj.optString("notes", "")
            )
            exercises.add(ex)
        }

        result.add(WorkoutDay(id = id, name = name, exercises = exercises))
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
        retriever.getFrameAtTime(0) // first frame
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
        try {
            val uri = Uri.parse(uriString)
            bitmap = loadVideoThumbnail(context, uri)
        } catch (_: Exception) {
            bitmap = null
        }
    }

    val bmp = bitmap
    if (bmp != null) {
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

// ---------- ROOT APP ----------

@Composable
fun GymNotesApp() {
    val context = LocalContext.current

    val days = remember {
        mutableStateListOf<WorkoutDay>().apply {
            addAll(loadDays(context))
        }
    }

    var screen by remember { mutableStateOf<Screen>(Screen.DaysList) }
    var showSettings by remember { mutableStateOf(false) }

    // Export launcher (backup)
    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) {
                val json = serializeDays(days)
                try {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray())
                    }
                } catch (_: Exception) {
                }
            }
        }

    // Import launcher
    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                try {
                    val json = context.contentResolver.openInputStream(uri)?.bufferedReader()
                        ?.use { it.readText() }

                    if (!json.isNullOrBlank()) {
                        val imported = parseDaysFromJson(json)
                        days.clear()
                        days.addAll(imported)
                        saveDays(context, days)
                    }
                } catch (_: Exception) {
                }
            }
        }

    when (val s = screen) {
        is Screen.DaysList -> {
            DaysListScreen(
                days = days,
                onDayClick = { index -> screen = Screen.DayDetail(index) },
                onDaysChanged = { saveDays(context, days) },
                onAddDayRequested = { name ->
                    if (name.isNotBlank()) {
                        days.add(WorkoutDay(name = name.trim()))
                        saveDays(context, days)
                    }
                },
                onOpenSettings = { showSettings = true }
            )
        }

        is Screen.DayDetail -> {
            val dayIdx = s.dayIndex
            if (dayIdx !in days.indices) {
                screen = Screen.DaysList
            } else {
                DayDetailScreen(
                    dayIndex = dayIdx,
                    day = days[dayIdx],
                    onBack = { screen = Screen.DaysList },
                    onExerciseClick = { exIdx ->
                        screen = Screen.ExerciseDetail(dayIdx, exIdx)
                    },
                    onDayUpdated = { updatedDay ->
                        days[dayIdx] = updatedDay
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
                ExerciseDetailScreen(
                    dayName = days[dayIdx].name,
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
                        try {
                            openVideo(context, Uri.parse(uriString))
                        } catch (_: Exception) {
                        }
                    }
                )
            }
        }

        // Safety net so the compiler stops whining
        else -> {
            screen = Screen.DaysList
        }
    }

    if (showSettings) {
        SettingsDialog(
            onDismiss = { showSettings = false },
            onExport = {
                exportLauncher.launch("gym_notes_backup.json")
            },
            onImport = {
                importLauncher.launch(arrayOf("application/json", "text/*"))
            },
            onClearAll = {
                days.clear()
                saveDays(context, days)
            }
        )
    }
}

// ---------- SETTINGS DIALOG ----------

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
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onExport
                ) {
                    Text("Export data")
                }

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        onImport()
                        onDismiss()
                    }
                ) {
                    Text("Import data")
                }

                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showClearConfirm = true }
                ) {
                    Text("Clear all data")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
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
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ---------- DAYS LIST SCREEN (drag & drop) ----------

@Composable
fun DaysListScreen(
    days: MutableList<WorkoutDay>,
    onDayClick: (Int) -> Unit,
    onDaysChanged: () -> Unit,
    onAddDayRequested: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    var showAddDayDialog by remember { mutableStateOf(false) }
    var showDayDeleteConfirm by remember { mutableStateOf(false) }
    var dayToDeleteIndex by remember { mutableStateOf<Int?>(null) }

    // Drag & drop state for days
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

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDayDialog = true }) {
                Text("+")
            }
        },
        topBar = {
            TopAppBar(
                title = { Text("Gym Notes") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Days",
                    style = MaterialTheme.typography.titleMedium
                )

                TextButton(onClick = { showAddDayDialog = true }) {
                    Text("Add Day")
                }
            }

            if (days.isEmpty()) {
                Text(
                    text = "No days yet. Add a day to start (Legs, Back, Push, etc.).",
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
                    itemsIndexed(
                        items = days,
                        key = { _, day -> day.id }
                    ) { index, day ->
                        ReorderableItem(
                            state = reorderState,
                            key = day.id
                        ) { _ ->
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
                onAddDay = { dayName ->
                    onAddDayRequested(dayName)
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
                text = { Text("Are you sure you want to delete this day and all its exercises?") },
                confirmButton = {
                    TextButton(onClick = {
                        val idx = dayToDeleteIndex!!
                        if (idx in days.indices) {
                            days.removeAt(idx)
                            onDaysChanged()
                        }
                        showDayDeleteConfirm = false
                        dayToDeleteIndex = null
                    }) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showDayDeleteConfirm = false
                        dayToDeleteIndex = null
                    }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun DayRow(
    day: WorkoutDay,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
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
                    text = day.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Exercises: ${day.exercises.size}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete day"
                )
            }
        }
    }
}

// ---------- DAY DETAIL SCREEN ----------

@Composable
fun DayDetailScreen(
    dayIndex: Int,
    day: WorkoutDay,
    onBack: () -> Unit,
    onExerciseClick: (Int) -> Unit,
    onDayUpdated: (WorkoutDay) -> Unit
) {
    var showAddExerciseDialog by remember { mutableStateOf(false) }
    var showExerciseDeleteConfirm by remember { mutableStateOf(false) }
    var exerciseToDeleteIndex by remember { mutableStateOf<Int?>(null) }

    // Drag & drop state for exercises
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
                title = { Text(day.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("<")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddExerciseDialog = true }) {
                Text("+")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp)
        ) {
            if (day.exercises.isEmpty()) {
                Text(
                    text = "No exercises yet. Tap + to add a card.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .reorderable(reorderState)
                        .detectReorderAfterLongPress(reorderState),
                    state = reorderState.listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(
                        items = day.exercises,
                        key = { _, ex -> ex.id }
                    ) { index, ex ->
                        ReorderableItem(
                            state = reorderState,
                            key = ex.id
                        ) { _ ->
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
                text = { Text("Are you sure you want to delete this exercise card?") },
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
                    }) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showExerciseDeleteConfirm = false
                        exerciseToDeleteIndex = null
                    }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

// ---------- EXERCISE CARD (SUMMARY) ----------

@Composable
fun ExerciseCardView(
    ex: ExerciseCard,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {

            if (ex.videoUri != null) {
                VideoThumbnail(
                    uriString = ex.videoUri,
                    modifier = Modifier.clickable {
                        try {
                            openVideo(context, Uri.parse(ex.videoUri))
                        } catch (_: Exception) {
                        }
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
                    Text(text = "Muscle: ${ex.targetMuscle}")
                    Text(text = "Sets x Reps: ${ex.sets} x ${ex.reps}")
                    ex.weight?.let {
                        Text(text = "Weight: $it ${ex.weightUnit}")
                    }
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
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Delete exercise"
                    )
                }
            }

            if (ex.previousWeights.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Previous weights:",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val list = ex.previousWeights.take(5)
                    list.forEachIndexed { index, w ->
                        val base = 12.sp
                        val size = when (index) {
                            0 -> base
                            1 -> base * 0.85f
                            2 -> base * 0.7f
                            3 -> base * 0.6f
                            else -> base * 0.5f
                        }

                        Text(
                            text = w.toString(),
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                fontSize = size
                            )
                        )
                    }
                }
            }
        }
    }
}

// ---------- EXERCISE DETAIL (EDIT MODE + CHANGE VIDEO) ----------

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
    var targetMuscle by remember { mutableStateOf(exercise.targetMuscle) }
    var setsText by remember { mutableStateOf(exercise.sets.toString()) }
    var repsText by remember { mutableStateOf(exercise.reps.toString()) }
    var weightText by remember { mutableStateOf(exercise.weight?.toString() ?: "") }
    var notes by remember { mutableStateOf(exercise.notes) }
    var videoUri by remember { mutableStateOf(exercise.videoUri) }

    val videoPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {
                }
                videoUri = uri.toString()
            }
        }

    val isValid = remember(equipmentName, targetMuscle, setsText, repsText) {
        val sets = setsText.toIntOrNull() ?: 0
        val reps = repsText.toIntOrNull() ?: 0
        equipmentName.isNotBlank() && targetMuscle.isNotBlank() && sets > 0 && reps > 0
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit: $dayName") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("<")
                    }
                },
                actions = {
                    TextButton(
                        enabled = isValid,
                        onClick = {
                            val sets = setsText.toIntOrNull() ?: 0
                            val reps = repsText.toIntOrNull() ?: 0
                            val weight = weightText.toFloatOrNull()

                            if (!isValid) return@TextButton

                            // Weight tracking: push old weight into history if changed
                            val newPrev = mutableListOf<Float>().apply {
                                if (exercise.weight != null && exercise.weight != weight) {
                                    add(exercise.weight)
                                }
                                addAll(exercise.previousWeights)
                            }
                            if (newPrev.size > 5) {
                                newPrev.subList(5, newPrev.size).clear()
                            }

                            val updated = exercise.copy(
                                equipmentName = equipmentName.trim(),
                                targetMuscle = targetMuscle.trim(),
                                sets = sets,
                                reps = reps,
                                weight = weight,
                                previousWeights = newPrev,
                                notes = notes.trim(),
                                videoUri = videoUri
                            )

                            onSave(updated)
                        }
                    ) {
                        Text("Save")
                    }
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
            if (videoUri != null) {
                VideoThumbnail(
                    uriString = videoUri!!,
                    modifier = Modifier
                        .clickable {
                            try {
                                onPlayVideo(videoUri!!)
                            } catch (_: Exception) {
                            }
                        }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = {
                    videoPickerLauncher.launch(arrayOf("video/*"))
                }) {
                    Text(if (videoUri == null) "Add video" else "Change video")
                }

                if (videoUri != null) {
                    Text(
                        text = "Video linked",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            OutlinedTextField(
                value = equipmentName,
                onValueChange = { equipmentName = it },
                label = { Text("Equipment Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = targetMuscle,
                onValueChange = { targetMuscle = it },
                label = { Text("Target Muscle") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = setsText,
                    onValueChange = { setsText = it.filter { c -> c.isDigit() } },
                    label = { Text("Sets") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    )
                )

                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = repsText,
                    onValueChange = { repsText = it.filter { c -> c.isDigit() } },
                    label = { Text("Reps") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    )
                )
            }

            OutlinedTextField(
                value = weightText,
                onValueChange = { new ->
                    if (new.all { it.isDigit() || it == '.' }) {
                        weightText = new
                    }
                },
                label = { Text("Weight (kg)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal
                ),
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

            if (exercise.previousWeights.isNotEmpty()) {
                Text(
                    text = "Previous weights:",
                    style = MaterialTheme.typography.titleSmall
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    exercise.previousWeights.take(10).forEachIndexed { index, w ->
                        Text(
                            text = "#${index + 1}: $w kg",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        )
                    }
                }
            }
        }
    }
}

// ---------- DIALOGS ----------

@Composable
fun AddDayDialog(
    onDismiss: () -> Unit,
    onAddDay: (String) -> Unit
) {
    var dayName by remember { mutableStateOf("") }
    val isValid = dayName.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Day") },
        text = {
            OutlinedTextField(
                value = dayName,
                onValueChange = { dayName = it },
                label = { Text("Day name (e.g. Legs, Back)") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = { onAddDay(dayName) }
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun AddExerciseDialog(
    onDismiss: () -> Unit,
    onAddExercise: (ExerciseCard) -> Unit
) {
    val context = LocalContext.current

    var equipmentName by remember { mutableStateOf("") }
    var setsText by remember { mutableStateOf("3") }
    var repsText by remember { mutableStateOf("10") }
    var weightText by remember { mutableStateOf("") }
    var targetMuscle by remember { mutableStateOf("") }
    var videoUri by remember { mutableStateOf<Uri?>(null) }

    val videoPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {
                }
                videoUri = uri
            }
        }

    val isValid = remember(equipmentName, targetMuscle, setsText, repsText) {
        val sets = setsText.toIntOrNull() ?: 0
        val reps = repsText.toIntOrNull() ?: 0
        equipmentName.isNotBlank() && targetMuscle.isNotBlank() && sets > 0 && reps > 0
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Exercise") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = equipmentName,
                    onValueChange = { equipmentName = it },
                    label = { Text("Equipment Name") },
                    singleLine = true
                )

                OutlinedTextField(
                    value = targetMuscle,
                    onValueChange = { targetMuscle = it },
                    label = { Text("Target Muscle") },
                    singleLine = true
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        modifier = Modifier.weight(1f),
                        value = setsText,
                        onValueChange = { setsText = it.filter { c -> c.isDigit() } },
                        label = { Text("Sets") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        )
                    )

                    OutlinedTextField(
                        modifier = Modifier.weight(1f),
                        value = repsText,
                        onValueChange = { repsText = it.filter { c -> c.isDigit() } },
                        label = { Text("Reps") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        )
                    )
                }

                OutlinedTextField(
                    value = weightText,
                    onValueChange = { new ->
                        if (new.all { it.isDigit() || it == '.' }) {
                            weightText = new
                        }
                    },
                    label = { Text("Weight (kg)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        videoPickerLauncher.launch(arrayOf("video/*"))
                    }
                ) {
                    Text(
                        if (videoUri == null)
                            "Pick Video"
                        else
                            "Change Video"
                    )
                }

                if (videoUri != null) {
                    Text(
                        text = "Video selected ✔",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Text(
                        text = "No video selected",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = {
                    val sets = setsText.toIntOrNull() ?: 0
                    val reps = repsText.toIntOrNull() ?: 0
                    val weight = weightText.toFloatOrNull()

                    if (!isValid) return@TextButton

                    val ex = ExerciseCard(
                        equipmentName = equipmentName.trim(),
                        videoUri = videoUri?.toString(),
                        sets = sets,
                        reps = reps,
                        weight = weight,
                        weightUnit = "kg",
                        targetMuscle = targetMuscle.trim()
                    )

                    onAddExercise(ex)
                }
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// ---------- PREVIEW ----------

@Preview(showBackground = true)
@Composable
fun GymNotesPreview() {
    GymNotesTheme {
        GymNotesApp()
    }
}
