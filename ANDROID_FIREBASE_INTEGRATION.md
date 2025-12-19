# Android Firebase Integration Guide

## Step 1: Add Firebase Dependencies

### Update `build.gradle.kts` (project level)

Add the Google Services plugin:

```kotlin
// Top-level build file
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    id("com.google.gms.google-services") version "4.4.0" apply false
}
```

### Update `app/build.gradle.kts`

Add Firebase dependencies and plugin:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services") // Add this
}

// ... existing code ...

dependencies {
    // ... existing dependencies ...
    
    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
    implementation("com.google.firebase:firebase-functions-ktx")
    implementation("com.google.firebase:firebase-installations-ktx")
    implementation("com.google.firebase:firebase-appcheck-ktx")
    
    // Remove or comment out direct Gemini SDK (we'll use backend)
    // implementation("com.google.ai.client.generativeai:generativeai:0.2.2")
}
```

## Step 2: Add google-services.json

1. In Firebase Console → Project Settings → Your apps
2. Click "Add app" → Android
3. Package name: `com.goofyapps.gymnotes`
4. Download `google-services.json`
5. Place it in `app/` folder (same level as `build.gradle.kts`)

## Step 3: Initialize Firebase in Application

Create or update your Application class (if you have one), or initialize in MainActivity:

```kotlin
// In MainActivity.kt onCreate
FirebaseApp.initializeApp(this)

// Initialize App Check (optional but recommended)
val appCheck = FirebaseAppCheck.getInstance()
appCheck.installAppCheckProviderFactory(
    PlayIntegrityAppCheckProviderFactory.getInstance()
)
```

## Step 4: Update GeminiAIService

The service will be updated to use Firebase Functions. See the updated `GeminiAIService.kt` file.

## Step 5: Build and Test

1. Sync Gradle
2. Build the app
3. Test Firebase Functions integration

## Troubleshooting

### Build fails: "google-services.json not found"
- Make sure `google-services.json` is in `app/` folder
- Sync Gradle after adding it

### Firebase not initialized
- Check `google-services.json` package name matches your app
- Make sure Firebase plugin is applied

### Functions not working
- Check Firebase project is set up correctly
- Check functions are deployed: `firebase functions:list`
- Check function logs: `firebase functions:log`


