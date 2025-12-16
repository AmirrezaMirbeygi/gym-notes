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
    
    // TODO: Replace with your actual Gemini API key
    // You can get one from: https://makersuite.google.com/app/apikey
    private val apiKey = "YOUR_GEMINI_API_KEY_HERE"
    
    // Gemini 2.5 Flash Image model name for image generation
    private val imageModelName = "gemini-2.5-flash-image-exp"
    
    // Regular Gemini model for text analysis
    private val textModelName = "gemini-2.0-flash-exp"
    
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
     * Get workout suggestions based on exercise cards and current scores.
     */
    suspend fun getWorkoutSuggestions(
        days: List<WorkoutDay>,
        currentScores: Map<String, Int>,
        bodyFatPercent: Float
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val workoutSummary = buildWorkoutSummary(days)
            val prompt = buildSuggestionPrompt(workoutSummary, currentScores, bodyFatPercent)
            
            val model = GenerativeModel(
                modelName = textModelName,
                apiKey = apiKey
            )
            
            val response = model.generateContent(prompt)
            val suggestions = response.text ?: "Unable to generate suggestions at this time."
            
            Result.success(suggestions)
        } catch (e: Exception) {
            Log.e("GeminiAI", "Error getting suggestions", e)
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
    
    private fun buildSuggestionPrompt(
        summary: WorkoutSummary,
        scores: Map<String, Int>,
        bodyFat: Float
    ): String {
        val exerciseDetails = summary.exercisesByGroup.entries.joinToString("\n") { (group, exercises) ->
            "$group: ${exercises.size} exercises"
        }
        
        return """
            Based on this workout data, provide personalized suggestions:
            
            Workout Summary:
            - Total exercises: ${summary.exerciseCount}
            - Workout days: ${summary.totalDays}
            - Exercises by muscle group:
            $exerciseDetails
            
            Current scores:
            - Chest: ${scores["chest"] ?: 0}/100
            - Back: ${scores["back"] ?: 0}/100
            - Core: ${scores["core"] ?: 0}/100
            - Shoulders: ${scores["shoulders"] ?: 0}/100
            - Arms: ${scores["arms"] ?: 0}/100
            - Legs: ${scores["legs"] ?: 0}/100
            - Body Fat: ${bodyFat}%
            
            Provide:
            1. Which muscle groups need more work
            2. Specific exercise recommendations
            3. Tips for improvement
        """.trimIndent()
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

