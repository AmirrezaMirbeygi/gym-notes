# Rate Limiting Implementation Plan

## Requirement

Limit free users to **10 prompts per day per device** when using the Gemini API through Firebase Functions.

## Implementation Strategy

### Option 1: Firestore-Based Rate Limiting (Recommended)

**How it works:**
1. Store device usage in Firestore
2. Track: `deviceId` + `date` → `count`
3. Check count before allowing request
4. Increment count after successful request

**Pros:**
- Persistent across app restarts
- Easy to query and monitor
- Can see usage patterns
- Can implement different limits per user type

**Cons:**
- Requires Firestore (free tier: 50K reads/day, 20K writes/day - plenty for this)
- Small latency (~50ms) for read/write

### Option 2: In-Memory Cache (Redis/Memory)

**How it works:**
1. Store counts in memory/Redis
2. Reset daily

**Pros:**
- Very fast
- No database needed

**Cons:**
- Lost on server restart
- Not persistent
- More complex setup

## Recommended: Firestore Implementation

### Data Structure

```javascript
// Firestore Collection: rateLimits
// Document ID: {deviceId}_{date}
{
  deviceId: "unique-device-id",
  date: "2025-01-20",  // YYYY-MM-DD
  count: 5,            // Number of prompts today
  lastReset: timestamp  // When counter was reset
}
```

### Device ID Options

1. **Firebase Installation ID** (Recommended)
   - Unique per app installation
   - Persistent across app updates
   - Can't be easily changed by user
   - `FirebaseInstallations.getInstance().getId()`

2. **Android ID**
   - Unique per device
   - Survives app uninstall/reinstall
   - Can be reset by factory reset

3. **Custom UUID** (stored in SharedPreferences)
   - Most control
   - Can be reset by clearing app data

**Recommendation: Use Firebase Installation ID** - it's designed for this use case.

### Function Flow

```javascript
exports.geminiProxy = functions.https.onCall(async (data, context) => {
  // 1. Verify App Check
  if (!context.app) {
    throw new functions.https.HttpsError('failed-precondition', 'App check failed');
  }

  // 2. Get device ID from request
  const deviceId = data.deviceId; // Sent from Android app
  
  // 3. Check rate limit
  const today = new Date().toISOString().split('T')[0]; // YYYY-MM-DD
  const limitDocId = `${deviceId}_${today}`;
  const limitDoc = await admin.firestore()
    .collection('rateLimits')
    .doc(limitDocId)
    .get();
  
  const currentCount = limitDoc.exists ? limitDoc.data().count : 0;
  const DAILY_LIMIT = 10;
  
  if (currentCount >= DAILY_LIMIT) {
    throw new functions.https.HttpsError(
      'resource-exhausted',
      `Daily limit of ${DAILY_LIMIT} prompts reached. Please try again tomorrow.`
    );
  }

  // 4. Call Gemini API
  const result = await callGeminiAPI(data.prompt, data.context);

  // 5. Increment counter (after successful call)
  await admin.firestore()
    .collection('rateLimits')
    .doc(limitDocId)
    .set({
      deviceId: deviceId,
      date: today,
      count: currentCount + 1,
      lastReset: admin.firestore.FieldValue.serverTimestamp()
    }, { merge: true });

  // 6. Return result
  return { result: result };
});
```

### Android App Changes

```kotlin
// In GeminiAIService.kt
class GeminiAIService(private val context: Context) {
    private val functions = FirebaseFunctions.getInstance()
    
    // Get device ID (Firebase Installation ID)
    private suspend fun getDeviceId(): String {
        return FirebaseInstallations.getInstance().id.await()
    }
    
    suspend fun sendPrompt(
        prompt: String,
        context: Map<String, Any>
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val deviceId = getDeviceId()
            val data = hashMapOf(
                "deviceId" to deviceId,
                "prompt" to prompt,
                "context" to context
            )
            
            val result = functions
                .getHttpsCallable("geminiProxy")
                .call(data)
                .await()
            
            val response = result.data as? Map<*, *>
            val text = response?.get("result") as? String
                ?: throw Exception("Invalid response")
            
            Result.success(text)
        } catch (e: Exception) {
            when {
                e.message?.contains("resource-exhausted") == true -> {
                    Result.failure(Exception("Daily limit reached. Please try again tomorrow."))
                }
                else -> Result.failure(e)
            }
        }
    }
}
```

### Error Handling

**Rate Limit Exceeded:**
- Return clear error message
- Show in UI: "You've reached your daily limit of 10 prompts. Try again tomorrow!"
- Maybe show countdown timer

**Other Errors:**
- Network errors
- API errors
- Handle gracefully

### Cleanup Strategy

**Option 1: TTL (Time To Live)**
- Set document expiration in Firestore
- Auto-delete after 2 days
- No manual cleanup needed

**Option 2: Scheduled Function**
- Daily Cloud Function to clean old records
- Runs at midnight
- Deletes records older than 1 day

**Option 3: On-Demand Cleanup**
- Check date when reading
- If date is old, reset count
- Simpler, no cleanup needed

**Recommendation: Option 3** - Check date on read, reset if old.

### Enhanced Version (Future)

```javascript
// Support for premium users
const userTier = await getUserTier(deviceId); // Could check Firestore or custom claims
const DAILY_LIMIT = userTier === 'premium' ? 1000 : 10;
```

## Cost Analysis

### Firestore Usage
- **Reads**: 1 per request (check limit)
- **Writes**: 1 per request (update count)
- **Free Tier**: 50K reads/day, 20K writes/day
- **Your App**: Even with 1000 users doing 10 requests = 10K reads + 10K writes
- **Result**: Well within free tier ✅

## Implementation Steps

1. ✅ Add Firestore to Firebase project
2. ✅ Update Firebase Function to check rate limits
3. ✅ Add Firebase Installations SDK to Android app
4. ✅ Update GeminiAIService to use Firebase Functions
5. ✅ Add error handling for rate limit exceeded
6. ✅ Update UI to show rate limit status
7. ✅ Test rate limiting

## UI Enhancements

### Show Rate Limit Status
```kotlin
// In ChatTab or AnalysisTab
var remainingPrompts by remember { mutableStateOf(10) }

// After each request
remainingPrompts = maxOf(0, remainingPrompts - 1)

// Display
Text("${remainingPrompts} prompts remaining today")
```

### Premium Upgrade Option (Future)
- Show "Upgrade to Premium" button when limit reached
- Unlimited prompts for premium users

## Testing

1. **Test limit**: Make 10 requests, verify 11th fails
2. **Test reset**: Wait for next day, verify limit resets
3. **Test different devices**: Verify each device has separate limit
4. **Test error handling**: Verify user sees clear message

## Security Considerations

1. **Device ID Spoofing**: Use Firebase Installation ID (harder to spoof)
2. **App Check**: Already verifying requests come from legitimate app
3. **Rate Limit Bypass**: Can't easily bypass since it's server-side

## Next Steps

When implementing Firebase Functions, I'll include:
1. Rate limiting logic in the function
2. Firestore setup instructions
3. Android app changes to use Firebase Installations
4. Error handling for rate limit exceeded
5. UI updates to show remaining prompts

Ready to implement when you are! 🚀


