# Firebase Backend Setup Instructions

## Prerequisites

1. **Node.js** (v18 or higher) - [Download](https://nodejs.org/)
2. **Firebase CLI** - Install with: `npm install -g firebase-tools`
3. **Firebase Account** - Sign up at [Firebase Console](https://console.firebase.google.com/)

## Step 1: Create Firebase Project

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Click "Add project" or "Create a project"
3. Enter project name: `gymnotes` (or your preferred name)
4. **Disable Google Analytics** (optional, not needed for this)
5. Click "Create project"
6. Wait for project to be created

## Step 2: Enable Required Services

### Enable Firestore Database

1. In Firebase Console, go to **Build** → **Firestore Database**
2. Click "Create database"
3. Select **Start in test mode** (we'll secure it with rules)
4. Choose a location (closest to your users)
5. Click "Enable"

### Enable Cloud Functions

1. In Firebase Console, go to **Build** → **Functions**
2. Click "Get started"
3. Review pricing (free tier is generous)
4. Click "Continue"

## Step 3: Initialize Firebase in Your Project

1. Open terminal in your project root (`GymNotes` folder)
2. Login to Firebase:
   ```bash
   firebase login
   ```
   (This will open browser for authentication)

3. Initialize Firebase:
   ```bash
   firebase init
   ```

4. Select the following:
   - ✅ **Functions**: Configure a Cloud Functions directory
   - ✅ **Firestore**: Configure security rules and indexes
   - Use an existing project: **Select your project** (the one you created)
   - Language: **JavaScript**
   - ESLint: **Yes** (recommended)
   - Install dependencies: **Yes**

5. This will create:
   - `functions/` folder (already created)
   - `firebase.json`
   - `.firebaserc` (update with your project ID)

## Step 4: Update Firebase Project ID

1. Open `.firebaserc`
2. Replace `"your-firebase-project-id"` with your actual Firebase project ID
   - You can find it in Firebase Console → Project Settings → General

## Step 5: Set Gemini API Key in Firebase

1. Get your Gemini API key from [Google AI Studio](https://aistudio.google.com/app/apikey)

2. Set it in Firebase Functions config:
   ```bash
   firebase functions:config:set gemini.api_key="YOUR_GEMINI_API_KEY_HERE"
   ```

   Replace `YOUR_GEMINI_API_KEY_HERE` with your actual key.

## Step 6: Install Function Dependencies

```bash
cd functions
npm install
cd ..
```

## Step 7: Deploy Functions

```bash
firebase deploy --only functions
```

This will:
- Upload your function code
- Deploy to Google Cloud
- Give you a URL like: `https://us-central1-your-project.cloudfunctions.net/geminiProxy`

**Note**: First deployment may take 2-3 minutes.

## Step 8: Update Firestore Rules

The `firestore.rules` file is already created. Deploy it:

```bash
firebase deploy --only firestore:rules
```

## Step 9: Test the Function

You can test using Firebase Console:
1. Go to **Functions** in Firebase Console
2. Click on `geminiProxy`
3. Click "Testing" tab
4. Enter test data:
   ```json
   {
     "deviceId": "test-device-123",
     "operation": "chat",
     "prompt": "Hello",
     "context": {
       "currentScores": {"chest": 50},
       "bodyFatPercent": 15
     }
   }
   ```
5. Click "Test function"

## Step 10: Update Android App

Now we need to update the Android app to use Firebase Functions instead of direct API calls.

### Add Firebase Dependencies

Add to `app/build.gradle.kts`:

```kotlin
// Firebase
implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
implementation("com.google.firebase:firebase-functions")
implementation("com.google.firebase:firebase-installations")
implementation("com.google.firebase:firebase-appcheck")
```

### Add Firebase to Project

1. In Firebase Console, go to **Project Settings** (gear icon)
2. Scroll down to "Your apps"
3. Click **Add app** → **Android**
4. Enter package name: `com.goofyapps.gymnotes`
5. Download `google-services.json`
6. Place it in `app/` folder
7. Add to `build.gradle.kts` (project level):
   ```kotlin
   plugins {
       // ... existing plugins
       id("com.google.gms.google-services") version "4.4.0" apply false
   }
   ```
8. Add to `app/build.gradle.kts`:
   ```kotlin
   plugins {
       // ... existing plugins
       id("com.google.gms.google-services")
   }
   ```

## Step 11: Enable App Check (Optional but Recommended)

1. In Firebase Console, go to **Build** → **App Check**
2. Click "Get started"
3. Register your Android app
4. For development: Use Debug tokens
5. For production: Use Play Integrity (when published to Play Store)

## Troubleshooting

### Function deployment fails
- Check Node.js version: `node --version` (should be 18+)
- Check Firebase CLI: `firebase --version`
- Try: `firebase functions:log` to see errors

### API key not found
- Make sure you ran: `firebase functions:config:set gemini.api_key="..."`
- Check with: `firebase functions:config:get`

### Rate limiting not working
- Check Firestore is enabled
- Check Firestore rules are deployed
- Check function logs: `firebase functions:log`

## Next Steps

After setup, we'll update the Android app code to use Firebase Functions. See the next steps in the implementation.


