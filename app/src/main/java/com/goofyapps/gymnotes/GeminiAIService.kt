package com.goofyapps.gymnotes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.installations.FirebaseInstallations
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Service for interacting with Google Gemini AI via Firebase Functions.
 * All API calls go through the secure backend with rate limiting.
 */
class GeminiAIService(private val context: Context) {
    
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    
    /**
     * Get user identifier for rate limiting
     * Uses Google account UID if signed in, otherwise device ID
     */
    private suspend fun getUserIdentifier(): String = withContext(Dispatchers.IO) {
        // Always check if user is signed in with Google first - use that as identifier
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val userId = currentUser.uid
            if (userId.isNotBlank()) {
                Log.d("GeminiAI", "User signed in with Google - using UID: $userId")
                return@withContext userId
            } else {
                Log.w("GeminiAI", "User is signed in but UID is blank!")
            }
        } else {
            Log.d("GeminiAI", "User not signed in - will use device ID")
        }
        
        // Only use device ID if not signed in
        val deviceId = getDeviceId()
        if (deviceId.isBlank()) {
            Log.e("GeminiAI", "CRITICAL: getDeviceId() returned blank string!")
            // Generate a fallback ID
            val fallbackId = "device_${System.currentTimeMillis()}_${(0..9999).random()}"
            Log.d("GeminiAI", "Using fallback device ID: $fallbackId")
            return@withContext fallbackId
        }
        deviceId
    }
    
    /**
     * Get unique device ID for rate limiting (fallback when not signed in)
     */
    private suspend fun getDeviceId(): String = withContext(Dispatchers.IO) {
        // First, try to get a stored device ID (works on emulator and real device)
        val prefs = context.getSharedPreferences("gymnotes_device", Context.MODE_PRIVATE)
        var deviceId = prefs.getString("device_id", null)
        if (deviceId != null && deviceId.isNotBlank()) {
            Log.d("GeminiAI", "Using stored device ID: $deviceId")
            return@withContext deviceId
        }
        
        // Try Firebase Installations (works on real device, may fail on emulator)
        try {
            val installationId = FirebaseInstallations.getInstance().id.await()
            if (installationId.isNotBlank()) {
                Log.d("GeminiAI", "Got Firebase Installation ID: $installationId")
                // Store it for future use (use commit for reliability)
                prefs.edit().putString("device_id", installationId).commit()
                return@withContext installationId
            }
        } catch (e: Exception) {
            Log.e("GeminiAI", "Error getting Firebase Installation ID (may be emulator): ${e.message}")
        }
        
        // Fallback to Android ID (works on emulator and real device)
        try {
            val androidId = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            )
            if (androidId != null && androidId.isNotBlank() && androidId != "9774d56d682e549c") {
                // "9774d56d682e549c" is the default Android ID on some emulators, skip it
                Log.d("GeminiAI", "Using Android ID: $androidId")
                // Store it for future use (use commit for reliability)
                prefs.edit().putString("device_id", androidId).commit()
                return@withContext androidId
            }
        } catch (e: Exception) {
            Log.e("GeminiAI", "Error getting Android ID", e)
        }
        
        // Last resort: generate a unique ID and store it (always works)
        deviceId = "device_${System.currentTimeMillis()}_${(0..9999).random()}"
        prefs.edit().putString("device_id", deviceId).commit()
        Log.d("GeminiAI", "Generated new device ID: $deviceId")
        deviceId
    }
    
    /**
     * Call Firebase Function with rate limiting
     */
    private suspend fun callGeminiFunction(
        operation: String,
        prompt: String? = null,
        contextData: Map<String, Any>? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Check if user is signed in with Google FIRST - use that as identifier
            val currentUser = try {
                auth.currentUser
            } catch (e: Exception) {
                Log.e("GeminiAI", "Error getting current user from Firebase Auth", e)
                null
            }
            
            // Build data map with identifier
            val data = mutableMapOf<String, Any>()
            
            // ALWAYS set operation first - this is required
            data["operation"] = operation
            Log.d("GeminiAI", "Set operation in data map: '$operation'")
            
            if (currentUser != null && currentUser.uid.isNotBlank()) {
                // User is signed in - use Google account UID directly
                val userId = currentUser.uid.trim()
                data["userId"] = userId
                Log.d("GeminiAI", "User signed in with Google - sending userId: '$userId', operation: '$operation'")
            } else {
                // User not signed in - get device ID
                Log.d("GeminiAI", "User not signed in, getting device ID")
                val deviceId = getDeviceId().trim()
                if (deviceId.isBlank()) {
                    Log.e("GeminiAI", "ERROR: deviceId is blank after all fallbacks!")
                    return@withContext Result.failure(Exception("Failed to get device identifier. Please restart the app."))
                }
                data["deviceId"] = deviceId
                Log.d("GeminiAI", "User not signed in - sending deviceId: '$deviceId', operation: '$operation'")
            }
            
            // Final validation - ensure we have either userId or deviceId set
            val hasUserId = data.containsKey("userId") && (data["userId"] as? String)?.isNotBlank() == true
            val hasDeviceId = data.containsKey("deviceId") && (data["deviceId"] as? String)?.isNotBlank() == true
            
            if (!hasUserId && !hasDeviceId) {
                Log.e("GeminiAI", "CRITICAL: Neither userId nor deviceId is set! Data keys: ${data.keys}")
                return@withContext Result.failure(Exception("Failed to prepare user identifier for backend."))
            }
            
            Log.d("GeminiAI", "Sending to backend - hasUserId: $hasUserId, hasDeviceId: $hasDeviceId, operation: '${data["operation"]}'")
            
            if (prompt != null && prompt.isNotBlank()) {
                data["prompt"] = prompt
                Log.d("GeminiAI", "Added prompt to data: '$prompt'")
            } else {
                Log.w("GeminiAI", "Prompt is null or blank, not adding to data")
            }
            
            if (contextData != null && contextData.isNotEmpty()) {
                data["context"] = contextData as Map<String, Any>
                Log.d("GeminiAI", "Added context to data: ${contextData.keys.joinToString()}")
            } else {
                Log.d("GeminiAI", "No context data to add")
            }
            
            // Log the exact data structure before sending
            Log.d("GeminiAI", "=== DATA BEING SENT TO BACKEND ===")
            Log.d("GeminiAI", "Data map size: ${data.size}")
            Log.d("GeminiAI", "Data keys: ${data.keys.joinToString()}")
            Log.d("GeminiAI", "Operation value: '${data["operation"]}' (type: ${data["operation"]?.javaClass?.simpleName})")
            data.forEach { (key, value) ->
                val valueStr = when (value) {
                    is String -> value
                    is Map<*, *> -> "Map(${value.size} entries)"
                    else -> value.toString()
                }
                Log.d("GeminiAI", "  $key = '$valueStr' (type: ${value?.javaClass?.simpleName})")
            }
            
            // Final check - ensure operation is set
            if (!data.containsKey("operation") || data["operation"] == null) {
                Log.e("GeminiAI", "CRITICAL: operation is missing from data map before sending!")
                return@withContext Result.failure(Exception("Internal error: Operation not set"))
            }
            
            Log.d("GeminiAI", "==================================")
            
            // Final validation before sending - ensure we have a user identifier
            val userIdValue = data["userId"] as? String
            val deviceIdValue = data["deviceId"] as? String
            if (userIdValue.isNullOrBlank() && deviceIdValue.isNullOrBlank()) {
                Log.e("GeminiAI", "FATAL: About to send data without userId or deviceId!")
                Log.e("GeminiAI", "userId: '$userIdValue', deviceId: '$deviceIdValue'")
                return@withContext Result.failure(Exception("Internal error: Missing user identifier"))
            }
            
            val result = functions
                .getHttpsCallable("geminiProxy")
                .call(data)
                .await()
            
            val resultData = result.data as? Map<*, *>
            val responseText = resultData?.get("result") as? String
                ?: throw Exception("Invalid response from function")
            
            Result.success(responseText)
        } catch (e: Exception) {
            Log.e("GeminiAI", "Error calling Firebase Function", e)
            // Log the full error for debugging
            e.printStackTrace()
            
            // Try to extract detailed error message from Firebase Functions exception
            val detailedError = when {
                e is FirebaseFunctionsException -> {
                    val code = e.code
                    val message = e.message ?: "Unknown error"
                    val details = e.details
                    Log.e("GeminiAI", "Firebase Functions Error - Code: $code, Message: $message, Details: $details")
                    when (code) {
                        FirebaseFunctionsException.Code.INTERNAL -> {
                            // Try to get the actual error message from details
                            val errorDetails = details?.toString() ?: message
                            "Backend error: $errorDetails"
                        }
                        else -> message
                    }
                }
                else -> null
            }
            
            // Check for Firebase Functions specific error codes
            val errorMessage = detailedError ?: when {
                // Check error message for rate limit indicators
                e.message?.contains("RESOURCE_EXHAUSTED", ignoreCase = true) == true ||
                e.message?.contains("resource-exhausted", ignoreCase = true) == true -> {
                    // Check if it's a cooldown or daily limit
                    when {
                        e.message?.contains("wait", ignoreCase = true) == true -> {
                            e.message ?: "Please wait before making another request."
                        }
                        e.message?.contains("Daily limit", ignoreCase = true) == true ||
                        e.message?.contains("limit reached", ignoreCase = true) == true -> {
                            "Daily limit of 10 prompts reached. Please try again tomorrow."
                        }
                        else -> {
                            "Please wait 15 seconds before making another request."
                        }
                    }
                }
                e.message?.contains("PERMISSION_DENIED", ignoreCase = true) == true ||
                e.message?.contains("permission-denied", ignoreCase = true) == true -> {
                    "Permission denied. Please check your app configuration."
                }
                e.message?.contains("UNAVAILABLE", ignoreCase = true) == true ||
                e.message?.contains("unavailable", ignoreCase = true) == true -> {
                    "Service temporarily unavailable. Please try again later."
                }
                else -> {
                    // Show the actual error message if available, otherwise generic message
                    val msg = e.message ?: "Unknown error occurred"
                    if (msg.startsWith("Error: ")) msg else "Error: $msg"
                }
            }
            Result.failure(Exception(errorMessage))
        }
    }
    
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
            
            // Build context for analysis
            val contextData = mapOf(
                "currentScores" to currentScores,
                "bodyFatPercent" to bodyFatPercent,
                "hasPhotos" to true
            )
            
            // Call Firebase Function for analysis
            val analysisResult = callGeminiFunction("analysis", contextData = contextData)
            
            val analysisText = analysisResult.getOrElse {
                return@withContext Result.failure(it)
            }
            
            // TODO: Implement image generation with Gemini 2.5 Flash Image
            // For now, return analysis text only
            Result.success(
                AIAnalysisResult(
                    analysisText = analysisText,
                    generatedImageUri = null
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
            val contextData = mutableMapOf<String, Any>()
            
            if (currentScores != null) {
                contextData["currentScores"] = currentScores
            }
            
            if (bodyFatPercent != null) {
                contextData["bodyFatPercent"] = bodyFatPercent
            }
            
            if (days != null) {
                contextData["days"] = days.size
            }
            
            val result = callGeminiFunction(
                operation = "chat",
                prompt = prompt,
                contextData = if (contextData.isNotEmpty()) contextData else null
            )
            
            result
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
            // Convert schedule to format expected by backend
            val scheduleData = schedule.mapKeys { it.key.toString() }
                .mapValues { entry ->
                    entry.value.map { item ->
                        when (item) {
                            is ScheduleItem.ExerciseCard -> mapOf(
                                "type" to "exercise",
                                "cardId" to item.cardId
                            )
                            is ScheduleItem.Rest -> mapOf("type" to "rest")
                        }
                    }
                }
            
            // Convert exercise cards to simple format
            val cardsData = allExerciseCards.map { card ->
                mapOf(
                    "id" to card.id,
                    "equipmentName" to card.equipmentName
                )
            }
            
            val contextData = mapOf(
                "schedule" to scheduleData,
                "allExerciseCards" to cardsData,
                "scores" to currentScores
            )
            
            val result = callGeminiFunction(
                operation = "scheduleSuggestions",
                contextData = contextData
            )
            
            result
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
            
            // TODO: Implement image generation with Gemini 2.5 Flash Image
            // For now, return null
            Result.success(null)
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
            val contextData = mutableMapOf<String, Any>(
                "currentScores" to currentScores,
                "bodyFatPercent" to bodyFatPercent,
                "hasPhotos" to (frontPhotoUri != null && backPhotoUri != null)
            )
            
            if (goals != null) {
                contextData["goals"] = goals
            }
            
            if (goalBodyFat != null) {
                contextData["goalBodyFat"] = goalBodyFat
            }
            
            // Add workout summary
            val totalExercises = days.sumOf { it.exercises.size }
            contextData["days"] = listOf(
                mapOf(
                    "totalDays" to days.size,
                    "totalExercises" to totalExercises
                )
            )
            
            val result = callGeminiFunction(
                operation = "analysis",
                contextData = contextData
            )
            
            result
        } catch (e: Exception) {
            Log.e("GeminiAI", "Error in comprehensive analysis", e)
            Result.failure(e)
        }
    }
    
    private fun saveGeneratedImage(imageData: ByteArray): Uri? {
        return try {
            val cacheDir = context.cacheDir
            val imageFile = File(cacheDir, "ai_generated_muscle_${System.currentTimeMillis()}.png")
            
            FileOutputStream(imageFile).use { out ->
                out.write(imageData)
            }
            
            // Use FileProvider-compatible URI instead of deprecated Uri.fromFile()
            // For cache directory (app-private), we can use file:// URI
            // Note: For sharing with other apps, use FileProvider instead
            android.net.Uri.parse("file://${imageFile.absolutePath}")
        } catch (e: Exception) {
            Log.e("GeminiAI", "Error saving generated image", e)
            null
        }
    }
    
    /**
     * Result containing both text analysis and generated image URI
     */
    data class AIAnalysisResult(
        val analysisText: String,
        val generatedImageUri: Uri?
    )
    
    /**
     * Test Firestore access - call this to verify Firestore is working
     */
    suspend fun testFirestore(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val result = functions
                .getHttpsCallable("testFirestore")
                .call(emptyMap<String, Any>())
                .await()

            val resultData = result.data as? Map<*, *>
            val success = resultData?.get("success") as? Boolean ?: false
            val message = resultData?.get("message") as? String ?: "Unknown response"
            
            if (success) {
                val tests = resultData?.get("tests") as? Map<*, *>
                val testDetails = tests?.entries?.joinToString("\n") { "${it.key}: ${it.value}" } ?: ""
                Log.d("GeminiAI", "Firestore test passed: $message\n$testDetails")
                Result.success("✅ Firestore is working!\n\n$message\n\nTest results:\n$testDetails")
            } else {
                val error = resultData?.get("error") as? Map<*, *>
                val errorCode = error?.get("code") as? Number
                val errorMessage = error?.get("message") as? String ?: "Unknown error"
                Log.e("GeminiAI", "Firestore test failed: $errorMessage (code: $errorCode)")
                Result.failure(Exception("❌ Firestore test failed:\nCode: $errorCode\nMessage: $errorMessage"))
            }
        } catch (e: Exception) {
            Log.e("GeminiAI", "Error calling testFirestore function", e)
            Result.failure(Exception("Error testing Firestore: ${e.message}"))
        }
    }
}
