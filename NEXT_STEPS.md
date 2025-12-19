# Next Steps: Firebase Setup

## ✅ What's Done
- Node.js installed (v24.12.0)
- Firebase CLI installed

## 🚀 What to Do Now

### Step 1: Login to Firebase (Run in your terminal)

Open your terminal (PowerShell) and run:

```bash
firebase login
```

This will:
- Open your browser
- Ask you to sign in with Google
- Authorize Firebase CLI

**After login succeeds**, you'll see: "Success! Logged in as your-email@gmail.com"

---

### Step 2: Create Firebase Project (In Browser)

1. Go to: https://console.firebase.google.com/
2. Click **"Add project"** or **"Create a project"**
3. Enter project name: `gymnotes` (or your preferred name)
4. **Disable Google Analytics** (optional - not needed)
5. Click **"Create project"**
6. Wait ~30 seconds for it to finish

**Note the Project ID** - you'll need it later (shown in project settings)

---

### Step 3: Enable Firestore Database (In Browser)

1. In Firebase Console, click **"Build"** → **"Firestore Database"**
2. Click **"Create database"**
3. Select **"Start in test mode"** (we have security rules)
4. Choose location (pick closest to you, e.g., `us-central1`)
5. Click **"Enable"**

---

### Step 4: Enable Cloud Functions (In Browser)

1. In Firebase Console, click **"Build"** → **"Functions"**
2. Click **"Get started"**
3. Review pricing (free tier is generous)
4. Click **"Continue"**

---

### Step 5: Initialize Firebase in Your Project (Run in terminal)

After creating the project, run in your terminal:

```bash
firebase init
```

**Select these options:**
- ✅ **Functions** (press Space to select, then Enter)
- ✅ **Firestore** (press Space to select, then Enter)
- **Use an existing project** → Select your project (`gymnotes` or whatever you named it)
- **Language**: JavaScript
- **ESLint**: Yes
- **Install dependencies**: Yes

**Important**: When it asks about overwriting files:
- `firebase.json` → **No** (we already have it)
- `firestore.rules` → **No** (we already have it)
- `firestore.indexes.json` → **No** (we already have it)
- `functions/package.json` → **No** (we already have it)
- `functions/index.js` → **No** (we already have it)

---

### Step 6: Update Project ID

1. Open `.firebaserc` in your editor
2. Replace `"your-firebase-project-id"` with your actual project ID
   - Find it in Firebase Console → Project Settings → General → Project ID
   - Or it might already be set from `firebase init`

---

### Step 7: Set API Key in Firebase

Run in terminal:

```bash
firebase functions:config:set gemini.api_key="AIzaSyBA1eIZ8IMhpMlrHSdggg_TXNovspMKoXE"
```

---

### Step 8: Install Function Dependencies

```bash
cd functions
npm install
cd ..
```

---

### Step 9: Deploy Functions

```bash
firebase deploy --only functions
```

**This takes 2-3 minutes the first time!**

You'll see output like:
```
✔  functions[geminiProxy(us-central1)] Successful create operation.
Function URL: https://us-central1-your-project.cloudfunctions.net/geminiProxy
```

**Copy that URL** - we'll need it for the Android app!

---

### Step 10: Deploy Firestore Rules

```bash
firebase deploy --only firestore:rules
```

---

## ✅ Backend is Live!

Your Firebase Functions are now running on Google's servers!

## Next: Update Android App

After backend is working, we'll:
1. Add Firebase SDK to Android app
2. Update `GeminiAIService.kt` to use Firebase Functions
3. Test it

---

## Need Help?

If you get stuck at any step, tell me:
- Which step you're on
- What error you see
- What command you ran

Let's get this backend working! 🚀

