# API Key Security Implementation Guide

## Current Problem

The Gemini API key is currently stored in `BuildConfig`, which means it's compiled into the APK. Anyone can extract it using tools like `apktool` or by decompiling the app, leading to:
- Unauthorized API usage
- Unexpected costs
- Quota exhaustion

## Solution Options

### Option 1: Firebase Functions (Recommended) ⭐

**Pros:**
- Easy to set up and integrate with Android
- Built-in security with Firebase App Check
- Automatic scaling
- Free tier available
- Rate limiting support
- No server management needed

**Cons:**
- Requires Firebase project setup
- Small learning curve

**Best for:** Production apps, easy setup, Google ecosystem

### Option 2: Simple Backend Server

**Pros:**
- Full control over implementation
- Can use any backend (Node.js, Python, etc.)
- Custom rate limiting and logging

**Cons:**
- Requires server hosting (costs money)
- Need to manage security, scaling, monitoring
- More complex setup

**Best for:** Custom requirements, existing backend infrastructure

### Option 3: Android Keystore (Not Recommended)

**Pros:**
- No backend needed
- Key is encrypted

**Cons:**
- Still extractable with root access
- Not truly secure
- Key still in APK (just encrypted)

**Best for:** Not recommended for production

## Recommended Implementation: Firebase Functions

### Architecture

```
Android App → Firebase Functions (with App Check) → Gemini API
```

1. App makes request to Firebase Function
2. Firebase Function verifies request is from your app (App Check)
3. Firebase Function calls Gemini API with secret key
4. Firebase Function returns response to app

### Security Features

- **Firebase App Check**: Verifies requests come from your legitimate app
- **Rate Limiting**: Prevent abuse
- **Environment Variables**: API key stored securely on Firebase
- **No Key in APK**: API key never touches the client

## Implementation Steps

### Step 1: Set Up Firebase Project

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Create a new project (or use existing)
3. Enable Firebase Functions
4. Install Firebase CLI: `npm install -g firebase-tools`
5. Login: `firebase login`

### Step 2: Initialize Firebase Functions

```bash
cd GymNotes
firebase init functions
# Select TypeScript or JavaScript
# Install dependencies: Yes
```

### Step 3: Create Firebase Function

Create a proxy function that:
- Validates App Check token
- **Rate Limiting**: Tracks usage per device (10 prompts/day limit)
- Calls Gemini API
- Returns response

### Step 4: Set Up Firebase App Check

1. Enable App Check in Firebase Console
2. Register your Android app
3. Use Play Integrity (for Play Store) or Debug tokens (for development)

### Step 5: Update Android App

1. Add Firebase SDK dependencies
2. Initialize App Check
3. Replace direct Gemini API calls with Firebase Function calls
4. Remove API key from BuildConfig

## Alternative: Quick Start with Backend Proxy

If you want a simpler backend solution without Firebase:

### Option: Node.js Express Server

Simple Express.js server that:
- Stores API key in environment variable
- Provides REST endpoints
- Adds basic rate limiting
- Can be deployed to Heroku, Railway, or Vercel

## Next Steps

Choose your preferred option and I'll help you implement it:

1. **Firebase Functions** (Recommended) - I'll create the function code and Android integration
2. **Simple Backend** - I'll create a Node.js/Express server you can deploy
3. **Other** - Let me know your preference

Which option would you like to proceed with?

