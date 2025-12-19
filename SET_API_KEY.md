# Setting Gemini API Key

## ⚠️ SECURITY WARNING
**DO NOT commit your API key to git!** The key below is for Firebase Functions configuration only.

## Step 1: Set API Key in Firebase Functions (Production - Secure)

Run this command in your terminal (from project root):

```bash
firebase functions:config:set gemini.api_key="AIzaSyBA1eIZ8IMhpMlrHSdggg_TXNovspMKoXE"
```

Then deploy the functions:
```bash
firebase deploy --only functions
```

This stores the key securely on Firebase servers and it will never be in your app code.

## Step 2: Update local.properties (Development Only)

For local development/testing, you can also add it to `local.properties`:

```
GEMINI_API_KEY=AIzaSyBA1eIZ8IMhpMlrHSdggg_TXNovspMKoXE
```

**Note**: `local.properties` should already be in `.gitignore`, but double-check it's not committed.

## Step 3: Verify It's Set

Check Firebase config:
```bash
firebase functions:config:get
```

You should see:
```
{
  "gemini": {
    "api_key": "AIzaSyBA1eIZ8IMhpMlrHSdggg_TXNovspMKoXE"
  }
}
```

## Important Notes

1. **Never commit API keys** to version control
2. **Firebase Functions config** is the secure way (for production)
3. **local.properties** is fine for development (already gitignored)
4. Once Firebase Functions is set up, the Android app won't need the key at all


