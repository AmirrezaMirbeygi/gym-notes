package com.goofyapps.gymnotes

const val CUSTOM_ID = "__custom__"

enum class MuscleGroup(
    val id: String,
    val displayName: String
) {
    CHEST("chest", "Chest"),
    BACK("back", "Back"),
    CORE("core", "Core"),
    SHOULDERS("shoulders", "Shoulders"),
    ARMS("arms", "Arms"),
    LEGS("legs", "Legs");
}

enum class Muscle(
    val id: String,
    val groupId: String,
    val displayName: String
) {
    // Chest
    CHEST_GENERAL("chest_general", "chest", "Chest"),
    UPPER_CHEST("upper_chest", "chest", "Upper Chest"),
    LOWER_CHEST("lower_chest", "chest", "Lower Chest"),

    // Back
    LATS("lats", "back", "Lats"),
    UPPER_BACK("upper_back", "back", "Upper Back"),
    LOWER_BACK("lower_back", "back", "Lower Back"),

    // Core
    ABS("abs", "core", "Abs"),
    OBLIQUES("obliques", "core", "Obliques"),

    // Shoulders
    FRONT_DELTS("front_delts", "shoulders", "Front Delts"),
    SIDE_DELTS("side_delts", "shoulders", "Side Delts"),
    REAR_DELTS("rear_delts", "shoulders", "Rear Delts"),

    // Arms
    BICEPS("biceps", "arms", "Biceps"),
    TRICEPS("triceps", "arms", "Triceps"),

    // Legs
    QUADS("quads", "legs", "Quads"),
    HAMSTRINGS("hamstrings", "legs", "Hamstrings"),
    GLUTES("glutes", "legs", "Glutes"),
    CALVES("calves", "legs", "Calves");
}

fun musclesForGroup(groupId: String): List<Muscle> =
    Muscle.entries.filter { it.groupId == groupId }
