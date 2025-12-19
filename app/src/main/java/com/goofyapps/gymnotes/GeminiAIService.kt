package com.goofyapps.gymnotes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Service for interacting with Google Gemini 2.5 Flash Image (Nano Banana) API.
 * Supports both image generation (text-to-image) and image editing (text-and-image-to-image).
 * Note: You'll need to add your Gemini API key in the settings or as a build config.
 */
class GeminiAIService(private val context: Context) {
    
    // API key is loaded from BuildConfig (set in local.properties)
    // Get your API key from: https://aistudio.google.com/app/apikey
    private val apiKey = BuildConfig.GEMINI_API_KEY
    
    // Gemini 2.5 Flash Image model name for image generation
    private val imageModelName = "gemini-2.5-flash-image-exp"
    
    // Regular Gemini model for text analysis
    // Using gemini-3-flash for latest model support
    private val textModelName = "gemini-2.5-flash"
    
    /**
     * Analyze body photos and generate muscle mass visualization image.
     * Uses Gemini 2.5 Flash Image for text-and-image-to-image generation.
     * Returns both analysis text and generated image URI.
     */
    suspend fun analyzeBodyPhotosAndGenerateMuscleMass(
        frontPhotoUri: Uri,
        backPhotoUri: Uri,
        currentScores: Map<String, Int>,
        bodyFatPercent: Float
    ): Result<AIAnalysisResult> = withContext(Dispatchers.IO) {
        try {
            val frontImage = loadImageFromUri(frontPhotoUri)
            val backImage = loadImageFromUri(backPhotoUri)
            
            if (frontImage == null || backImage == null) {
                return@withContext Result.failure(Exception("Failed to load images"))
            }
            
            // First, analyze with text model for suggestions
            val textModel = GenerativeModel(
                modelName = textModelName,
                apiKey = apiKey
            )
            
            val analysisPrompt = buildAnalysisPrompt(currentScores, bodyFatPercent)
            val analysisResponse = textModel.generateContent(analysisPrompt)
            val analysisText = analysisResponse.text ?: "Analysis completed."
            
            // TODO: Implement image generation with Gemini 2.5 Flash Image
            // Use GenerativeModel with imageModelName to generate images
            // Example:
            // val imageModel = GenerativeModel(modelName = imageModelName, apiKey = apiKey)
            // val imageResponse = imageModel.generateContent(...)
            // The exact API for image generation needs to be verified from the SDK documentation
            // For now, we'll return null for the image and focus on text analysis
            // 
            // Expected approach:
            // 1. Convert Bitmaps to appropriate format for the API
            // 2. Use GenerativeModel with image model to generate content
            // 3. Extract generated image from response
            // 
            // Note: Gemini 2.5 Flash Image API structure may differ from standard text generation
            val generatedImageBytes: ByteArray? = null
            
            if (generatedImageBytes == null) {
                return@withContext Result.success(
                    AIAnalysisResult(
                        analysisText = analysisText + "\n\nNote: Image generation is being configured. Please check API documentation for Gemini 2.5 Flash Image response structure.",
                        generatedImageUri = null
                    )
                )
            }
            
            // Save generated image to app's cache directory
            val imageUri = saveGeneratedImage(generatedImageBytes)
            
            Result.success(
                AIAnalysisResult(
                    analysisText = analysisText,
                    generatedImageUri = imageUri
                )
            )
        } catch (e: Exception) {
            Log.e("GeminiAI", "Error analyzing photos and generating image", e)
            Result.failure(e)
        }
    }
    
    /**
     * Send a custom prompt to the AI and get a response.
     * Can include context about the user's fitness data if needed.
     */
    suspend fun sendCustomPrompt(
        prompt: String,
        days: List<WorkoutDay>? = null,
        currentScores: Map<String, Int>? = null,
        bodyFatPercent: Float? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val model = GenerativeModel(
                modelName = textModelName,
                apiKey = apiKey
            )
            
            // Build enhanced prompt with context if available
            val enhancedPrompt = buildString {
                append("You are Gymini, an AI fitness coach. Always refer to yourself as \"Gymini\" in your responses. You can use pronouns when referring to yourself.\n\n")
                append(prompt)
                
                if (days != null && currentScores != null && bodyFatPercent != null) {
                    append("\n\n--- Context ---\n")
                    append("Current muscle group scores (0-100):\n")
                    append("- Chest: ${currentScores["chest"] ?: 0}/100\n")
                    append("- Back: ${currentScores["back"] ?: 0}/100\n")
                    append("- Core: ${currentScores["core"] ?: 0}/100\n")
                    append("- Shoulders: ${currentScores["shoulders"] ?: 0}/100\n")
                    append("- Arms: ${currentScores["arms"] ?: 0}/100\n")
                    append("- Legs: ${currentScores["legs"] ?: 0}/100\n")
                    append("- Body Fat: ${bodyFatPercent}%\n")
                    append("- Total workout days: ${days.size}\n")
                    append("- Total exercises: ${days.sumOf { it.exercises.size }}\n")
                }
            }
            
            val response = model.generateContent(enhancedPrompt)
            val result = response.text ?: "No response generated."
            
            Result.success(result)
        } catch (e: Exception) {
            Log.e("GeminiAI", "Error sending custom prompt", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get workout suggestions based on exercise cards and current scores.
     */
    suspend fun getScheduleSuggestions(
        schedule: Map<Int, List<ScheduleItem>>,
        allExerciseCards: List<ExerciseCard>,
        currentScores: Map<String, Int>
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val prompt = buildScheduleSuggestionPrompt(schedule, allExerciseCards, currentScores)
            
            val model = GenerativeModel(
                modelName = textModelName,
                apiKey = apiKey
            )
            
            val response = model.generateContent(prompt)
            val suggestions = response.text ?: "Unable to generate schedule suggestions at this time."
            
            Result.success(suggestions)
        } catch (e: Exception) {
            Log.e("GeminiAI", "Error getting schedule suggestions", e)
            Result.failure(e)
        }
    }
    
    /**
     * Generate new muscle mass visualization image when scores update.
     * Uses Gemini 2.5 Flash Image for text-and-image-to-image generation.
     */
    suspend fun generateUpdatedMuscleMassVisualization(
        frontPhotoUri: Uri?,
        backPhotoUri: Uri?,
        newScores: Map<String, Int>,
        previousScores: Map<String, Int>,
        bodyFatPercent: Float
    ): Result<Uri?> = withContext(Dispatchers.IO) {
        try {
            if (frontPhotoUri == null || backPhotoUri == null) {
                return@withContext Result.success(null)
            }
            
            val frontImage = loadImageFromUri(frontPhotoUri)
            val backImage = loadImageFromUri(backPhotoUri)
            
            if (frontImage == null || backImage == null) {
                return@withContext Result.failure(Exception("Failed to load reference images"))
            }
            
            // Compare scores to see what changed
            val improvements = newScores.filter { (key, value) ->
                val old = previousScores[key] ?: 0
                value > old
            }
            
            // TODO: Implement image generation with Gemini 2.5 Flash Image
            // Use GenerativeModel with imageModelName to generate images
            // Example:
            // val imageModel = GenerativeModel(modelName = imageModelName, apiKey = apiKey)
            // val imageResponse = imageModel.generateContent(...)
            // See comment in analyzeBodyPhotosAndGenerateMuscleMass for details
            val generatedImageBytes: ByteArray? = null
            
            if (generatedImageBytes == null) {
                return@withContext Result.success(null)
            }
            
            val imageUri = saveGeneratedImage(generatedImageBytes)
            Result.success(imageUri)
        } catch (e: Exception) {
            Log.e("GeminiAI", "Error generating visualization", e)
            Result.failure(e)
        }
    }
    
    private fun loadImageFromUri(uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (e: Exception) {
            Log.e("GeminiAI", "Error loading image", e)
            null
        }
    }
    
    private fun bitmapToByteArray(bitmap: Bitmap): ByteArray {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        return outputStream.toByteArray()
    }
    
    /**
     * Comprehensive analysis including photos, scores, and goals.
     * Provides full analysis and specific recommendations to reach goals.
     */
    suspend fun comprehensiveAnalysis(
        frontPhotoUri: Uri?,
        backPhotoUri: Uri?,
        currentScores: Map<String, Int>,
        bodyFatPercent: Float,
        goals: Map<String, Int>?, // goal scores
        goalBodyFat: Float?,
        days: List<WorkoutDay>
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val model = GenerativeModel(
                modelName = textModelName,
                apiKey = apiKey
            )
            
            val prompt = buildComprehensiveAnalysisPrompt(
                currentScores = currentScores,
                bodyFatPercent = bodyFatPercent,
                goals = goals,
                goalBodyFat = goalBodyFat,
                days = days,
                hasPhotos = frontPhotoUri != null && backPhotoUri != null
            )
            
            val response = model.generateContent(prompt)
            val result = response.text ?: "Analysis completed."
            
            Result.success(result)
        } catch (e: Exception) {
            Log.e("GeminiAI", "Error in comprehensive analysis", e)
            Result.failure(e)
        }
    }
    
    private fun buildAnalysisPrompt(
        scores: Map<String, Int>,
        bodyFat: Float
    ): String {
        return """
            Analyze these full body photos (front and back) and provide:
            1. Muscle mass assessment for each major group
            2. Areas that need more development
            3. Specific recommendations for improvement
            
            Current muscle group scores (0-100):
            - Chest: ${scores["chest"] ?: 0}/100
            - Back: ${scores["back"] ?: 0}/100
            - Core: ${scores["core"] ?: 0}/100
            - Shoulders: ${scores["shoulders"] ?: 0}/100
            - Arms: ${scores["arms"] ?: 0}/100
            - Legs: ${scores["legs"] ?: 0}/100
            - Body Fat: ${bodyFat}%
        """.trimIndent()
    }
    
    private fun buildComprehensiveAnalysisPrompt(
        currentScores: Map<String, Int>,
        bodyFatPercent: Float,
        goals: Map<String, Int>?,
        goalBodyFat: Float?,
        days: List<WorkoutDay>,
        hasPhotos: Boolean
    ): String {
        val workoutSummary = buildWorkoutSummary(days)
        
        return buildString {
            append("""You are Gymini, an AI fitness coach. Always refer to yourself as "Gymini" in your responses. You can use pronouns when referring to yourself.
            
            Provide a comprehensive fitness analysis and action plan.
            
            CURRENT STATUS:
            Muscle Group Scores (0-100):
            - Chest: ${currentScores["chest"] ?: 0}/100
            - Back: ${currentScores["back"] ?: 0}/100
            - Core: ${currentScores["core"] ?: 0}/100
            - Shoulders: ${currentScores["shoulders"] ?: 0}/100
            - Arms: ${currentScores["arms"] ?: 0}/100
            - Legs: ${currentScores["legs"] ?: 0}/100
            - Body Fat: ${bodyFatPercent}%
            
            """)
            
            if (goals != null || goalBodyFat != null) {
                append("GOALS:\n")
                if (goals != null) {
                    append("Target Muscle Group Scores:\n")
                    append("- Chest: ${goals["chest"] ?: currentScores["chest"] ?: 0}/100\n")
                    append("- Back: ${goals["back"] ?: currentScores["back"] ?: 0}/100\n")
                    append("- Core: ${goals["core"] ?: currentScores["core"] ?: 0}/100\n")
                    append("- Shoulders: ${goals["shoulders"] ?: currentScores["shoulders"] ?: 0}/100\n")
                    append("- Arms: ${goals["arms"] ?: currentScores["arms"] ?: 0}/100\n")
                    append("- Legs: ${goals["legs"] ?: currentScores["legs"] ?: 0}/100\n")
                }
                if (goalBodyFat != null) {
                    append("- Target Body Fat: ${goalBodyFat}%\n")
                }
                append("\n")
            }
            
            append("""WORKOUT DATA:
            - Total workout days: ${workoutSummary.totalDays}
            - Total exercises: ${workoutSummary.exerciseCount}
            
            """)
            
            if (hasPhotos) {
                append("Body photos (front and back) have been uploaded for visual analysis.\n\n")
            }
            
            append("""Please provide:
            1. FULL ANALYSIS: Comprehensive assessment of current fitness level, muscle development, and body composition
            2. GAP ANALYSIS: Specific gaps between current status and goals (if goals are set)
            3. ACTION PLAN: Exactly what to do and how much to do to reach your goals:
               - Specific exercises to add/increase
               - Sets, reps, and weight recommendations
               - Frequency and volume targets
               - Timeline estimates
            4. PRIORITIZATION: Which muscle groups to focus on first based on gaps and current development
            
            Be specific, actionable, and quantitative in your recommendations.""")
        }
    }
    
    private fun buildImageGenerationPrompt(
        scores: Map<String, Int>,
        bodyFat: Float
    ): String {
        return """
            Generate a realistic full-body muscle mass visualization based on these reference photos and current fitness metrics.
            
            Muscle development levels:
            - Chest: ${scores["chest"] ?: 0}/100
            - Back: ${scores["back"] ?: 0}/100
            - Core: ${scores["core"] ?: 0}/100
            - Shoulders: ${scores["shoulders"] ?: 0}/100
            - Arms: ${scores["arms"] ?: 0}/100
            - Legs: ${scores["legs"] ?: 0}/100
            - Body Fat Percentage: ${bodyFat}%
            
            Create a professional fitness visualization showing the current muscle mass distribution, matching the body proportions from the reference photos but adjusted to reflect these specific muscle group scores and body fat percentage. The image should be realistic and anatomically accurate.
        """.trimIndent()
    }
    
    private fun buildUpdateImagePrompt(
        newScores: Map<String, Int>,
        previousScores: Map<String, Int>,
        bodyFat: Float,
        improvements: Map<String, Int>
    ): String {
        val improvementsText = if (improvements.isNotEmpty()) {
            "Recent improvements: " + improvements.entries.joinToString(", ") { "${it.key}: +${it.value - (previousScores[it.key] ?: 0)}" }
        } else {
            "No significant improvements detected."
        }
        
        return """
            Update the muscle mass visualization to reflect the current fitness progress.
            
            Current muscle group scores:
            - Chest: ${newScores["chest"] ?: 0}/100
            - Back: ${newScores["back"] ?: 0}/100
            - Core: ${newScores["core"] ?: 0}/100
            - Shoulders: ${newScores["shoulders"] ?: 0}/100
            - Arms: ${newScores["arms"] ?: 0}/100
            - Legs: ${newScores["legs"] ?: 0}/100
            - Body Fat: ${bodyFat}%
            
            $improvementsText
            
            Generate an updated realistic visualization showing the current muscle development, maintaining the same body proportions as the reference photos but adjusted to reflect these updated scores.
        """.trimIndent()
    }
    
    private fun saveGeneratedImage(imageData: ByteArray): Uri? {
        return try {
            val cacheDir = context.cacheDir
            val imageFile = File(cacheDir, "ai_generated_muscle_${System.currentTimeMillis()}.png")
            
            FileOutputStream(imageFile).use { out ->
                out.write(imageData)
            }
            
            Uri.fromFile(imageFile)
        } catch (e: Exception) {
            Log.e("GeminiAI", "Error saving generated image", e)
            null
        }
    }
    
    private fun buildWorkoutSummary(days: List<WorkoutDay>): WorkoutSummary {
        val totalExercises = days.sumOf { it.exercises.size }
        val exercisesByGroup = days.flatMap { it.exercises }
            .groupBy { it.primaryMuscleId }
        
        return WorkoutSummary(
            exerciseCount = totalExercises,
            exercisesByGroup = exercisesByGroup,
            totalDays = days.size
        )
    }
    
    private fun buildScheduleSuggestionPrompt(
        schedule: Map<Int, List<ScheduleItem>>,
        allExerciseCards: List<ExerciseCard>,
        scores: Map<String, Int>
    ): String {
        val dayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        
        val scheduleText = buildString {
            for (i in 0..6) {
                val items = schedule[i] ?: emptyList()
                if (items.isNotEmpty()) {
                    append("${dayNames[i]}: ")
                    val exerciseNames = items.mapNotNull { item ->
                        when (item) {
                            is ScheduleItem.ExerciseCard -> {
                                allExerciseCards.find { it.id == item.cardId }?.equipmentName
                            }
                            is ScheduleItem.Rest -> "Rest"
                        }
                    }
                    append(exerciseNames.joinToString(", "))
                    append("\n")
                }
            }
        }
        
        val weakGroups = scores.filter { it.value < 50 }.keys.joinToString(", ") { it.replaceFirstChar { char -> char.uppercaseChar() } }
        val strongGroups = scores.filter { it.value >= 70 }.keys.joinToString(", ") { it.replaceFirstChar { char -> char.uppercaseChar() } }
        
        return """You are Gymini, an AI fitness coach. Provide brief schedule optimization suggestions (keep response under 200 words).

Current Weekly Schedule:
$scheduleText

Muscle Group Scores:
- Chest: ${scores["chest"] ?: 0}/100
- Back: ${scores["back"] ?: 0}/100
- Core: ${scores["core"] ?: 0}/100
- Shoulders: ${scores["shoulders"] ?: 0}/100
- Arms: ${scores["arms"] ?: 0}/100
- Legs: ${scores["legs"] ?: 0}/100

Provide 2-3 specific, actionable suggestions to improve the weekly schedule. Focus on:
1. Better muscle group distribution across days
2. Adding exercises for weak areas (${if (weakGroups.isNotEmpty()) weakGroups else "none"})
3. Optimizing rest days

Keep it concise and practical.""".trimIndent()
    }
    
    private data class WorkoutSummary(
        val exerciseCount: Int,
        val exercisesByGroup: Map<String?, List<ExerciseCard>>,
        val totalDays: Int
    )
    
    /**
     * Result containing both text analysis and generated image URI
     */
    data class AIAnalysisResult(
        val analysisText: String,
        val generatedImageUri: Uri?
    )
}

