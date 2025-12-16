package com.goofyapps.gymnotes

enum class MuscleGroup(val id: String, val displayName: String) {
    CHEST("chest", "Chest"),
    BACK("back", "Back"),
    CORE("core", "Core"),
    SHOULDERS("shoulders", "Shoulders"),
    ARMS("arms", "Arms"),
    LEGS("legs", "Legs"),
}

enum class Muscle(val id: String, val groupId: String, val displayName: String) {

    // Chest (CAL uses chest_pecs)
    CHEST_PEC("chest_pecs", "chest", "Chest (Pectorals)"),
    CHEST_GENERAL("chest_general", "chest", "Chest (General)"),
    UPPER_CHEST("upper_chest", "chest", "Upper Chest"),
    LOWER_CHEST("lower_chest", "chest", "Lower Chest"),

    // Back (CAL uses upper_back, lats, lower_back_erectors)
    UPPER_BACK("upper_back", "back", "Upper Back"),
    LATS("lats", "back", "Lats"),
    LOWER_BACK_ERECTORS("lower_back_erectors", "back", "Lower Back (Erectors)"),

    // Core (CAL uses core_abs_obliques)
    CORE_ABS_OBLIQUES("core_abs_obliques", "core", "Core (Abs + Obliques)"),
    ABS("abs", "core", "Abs"),
    OBLIQUES("obliques", "core", "Obliques"),

    // Shoulders (CAL uses shoulders_delts)
    SHOULDERS_DELTS("shoulders_delts", "shoulders", "Shoulders (Deltoids)"),
    FRONT_DELTS("front_delts", "shoulders", "Front Delts"),
    SIDE_DELTS("side_delts", "shoulders", "Side Delts"),
    REAR_DELTS("rear_delts", "shoulders", "Rear Delts"),

    // Arms (CAL uses biceps, triceps, forearms)
    BICEPS("biceps", "arms", "Biceps"),
    TRICEPS("triceps", "arms", "Triceps"),
    FOREARMS("forearms", "arms", "Forearms"),

    // Legs (CAL uses quads, glutes_hamstrings, hamstrings_isolation, calves)
    QUADS("quads", "legs", "Quads"),
    GLUTES_HAMS("glutes_hamstrings", "legs", "Glutes + Hamstrings"),
    HAMS_ISO("hamstrings_isolation", "legs", "Hamstrings (Isolation)"),
    HAMSTRINGS("hamstrings", "legs", "Hamstrings"),
    GLUTES("glutes", "legs", "Glutes"),
    CALVES("calves", "legs", "Calves"),
}


fun musclesForGroup(groupId: String): List<Muscle> =
    Muscle.entries.filter { it.groupId == groupId }
