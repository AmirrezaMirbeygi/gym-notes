const functions = require('firebase-functions');
const admin = require('firebase-admin');
const { GoogleGenerativeAI } = require('@google/generative-ai');

// Initialize Firebase Admin
admin.initializeApp();

// Get Gemini API key
// TODO: Move to secrets when billing is enabled: firebase functions:secrets:set GEMINI_API_KEY
// For now, using hardcoded value (works without billing, but less secure)
const GEMINI_API_KEY = process.env.GEMINI_API_KEY || 'AIzaSyBA1eIZ8IMhpMlrHSdggg_TXNovspMKoXE';

// Daily limits for different operations
const DAILY_CHAT_LIMIT = 10;  // 10 chat messages per day
const DAILY_ANALYSIS_LIMIT = 1;  // 1 analysis refresh per day
const DAILY_SUGGESTIONS_LIMIT = 1;  // 1 suggestions refresh per day

// Cooldown between requests (15 seconds)
const COOLDOWN_SECONDS = 15;

/**
 * Check cooldown period for a user/device and operation type
 * Cooldown is per-operation, so you can switch between chat/analysis/suggestions
 * @param {string} userIdentifier - User ID (from Google) or device ID
 * @param {string} operation - Operation type: 'chat', 'analysis', or 'scheduleSuggestions'
 * @param {boolean} updateTimestamp - If true, update the cooldown timestamp (only on success)
 * @returns {Promise<{allowed: boolean, waitSeconds: number}>}
 */
async function checkCooldown(userIdentifier, operation, updateTimestamp = true) {
  const cooldownRef = admin.firestore().collection('cooldowns').doc(`${userIdentifier}_${operation}`);

  try {
    const doc = await cooldownRef.get();
    const now = Date.now();

    if (doc.exists) {
      const data = doc.data();
      let lastRequestTime = data.lastRequestTime;
      
      // Handle Firestore Timestamp if it's not a number
      if (lastRequestTime && typeof lastRequestTime.toMillis === 'function') {
        lastRequestTime = lastRequestTime.toMillis();
      }
      
      // Only check if we have a valid timestamp
      if (lastRequestTime && typeof lastRequestTime === 'number') {
        const timeSinceLastRequest = (now - lastRequestTime) / 1000; // seconds
        
        if (timeSinceLastRequest < COOLDOWN_SECONDS) {
          const waitSeconds = Math.ceil(COOLDOWN_SECONDS - timeSinceLastRequest);
          console.log(`Cooldown active: ${waitSeconds} seconds remaining for user ${userIdentifier}, operation ${operation}`);
          return { allowed: false, waitSeconds };
        }
      }
    }

    // Only update timestamp if this is a successful request
    if (updateTimestamp) {
      await cooldownRef.set({
        lastRequestTime: now,
      }, { merge: true });
    }

    return { allowed: true, waitSeconds: 0 };
  } catch (error) {
    console.error('Error checking cooldown:', error);
    // On error, allow the request (fail open) but log it
    return { allowed: true, waitSeconds: 0 };
  }
}

/**
 * Update cooldown timestamp after successful request
 * @param {string} userIdentifier - User ID (from Google) or device ID
 * @param {string} operation - Operation type
 */
async function updateCooldown(userIdentifier, operation) {
  const cooldownRef = admin.firestore().collection('cooldowns').doc(`${userIdentifier}_${operation}`);
  try {
    await cooldownRef.set({
      lastRequestTime: Date.now(),
    }, { merge: true });
  } catch (error) {
    console.error('Error updating cooldown:', error);
  }
}

/**
 * Check and update rate limit for a user/device based on operation type
 * @param {string} userIdentifier - User ID (from Google) or device ID
 * @param {string} operation - Operation type: 'chat', 'analysis', or 'scheduleSuggestions'
 * @returns {Promise<{allowed: boolean, remaining: number, limit: number}>}
 */
async function checkRateLimit(userIdentifier, operation) {
  const today = new Date().toISOString().split('T')[0]; // YYYY-MM-DD
  
  // Determine limit based on operation
  let dailyLimit;
  let limitKey;
  
  switch (operation) {
    case 'chat':
      dailyLimit = DAILY_CHAT_LIMIT;
      limitKey = 'chatCount';
      break;
    case 'analysis':
      dailyLimit = DAILY_ANALYSIS_LIMIT;
      limitKey = 'analysisCount';
      break;
    case 'scheduleSuggestions':
      dailyLimit = DAILY_SUGGESTIONS_LIMIT;
      limitKey = 'suggestionsCount';
      break;
    default:
      dailyLimit = DAILY_CHAT_LIMIT;
      limitKey = 'chatCount';
  }
  
  const limitDocId = `${userIdentifier}_${today}`;
  const rateLimitRef = admin.firestore().collection('rateLimits').doc(limitDocId);

  try {
    const doc = await rateLimitRef.get();
    const data = doc.exists ? doc.data() : {};
    const currentCount = data[limitKey] || 0;

    // Check if limit is reached
    if (currentCount >= dailyLimit) {
      return { allowed: false, remaining: 0, limit: dailyLimit };
    }

    // Increment counter for this operation
    const updateData = {
      userIdentifier: userIdentifier,
      date: today,
      [limitKey]: currentCount + 1,
      lastUpdated: admin.firestore.FieldValue.serverTimestamp(),
    };
    
    await rateLimitRef.set(updateData, { merge: true });

    return { allowed: true, remaining: dailyLimit - (currentCount + 1), limit: dailyLimit };
  } catch (error) {
    console.error('Error checking rate limit:', error);
    // On error, allow the request (fail open) but log it
    return { allowed: true, remaining: dailyLimit, limit: dailyLimit };
  }
}

/**
 * Proxy function for Gemini API calls with rate limiting
 * Supports multiple operation types: chat, analysis, schedule suggestions
 */
exports.geminiProxy = functions.https.onCall(
  async (requestData, context) => {
  // Firebase Functions wraps callable data - extract the actual data
  // The data sent from Android is nested in requestData.data
  const data = requestData.data || requestData;
  
  // DEBUG: Log what we receive (safe logging to avoid circular references)
  console.log('=== BACKEND RECEIVED DATA ===');
  console.log('Request data type:', typeof requestData);
  console.log('Request data keys:', Object.keys(requestData || {}));
  console.log('Extracted data type:', typeof data);
  console.log('Extracted data keys:', Object.keys(data || {}));
  console.log('Operation:', data?.operation);
  console.log('Prompt:', data?.prompt);
  console.log('Context type:', typeof data?.context);
  if (data?.context) {
    console.log('Context keys:', Object.keys(data.context || {}));
    // Safely log context without circular references
    const seen = new WeakSet();
    try {
      const safeContext = JSON.stringify(data.context, (key, value) => {
        // Skip circular references and functions
        if (typeof value === 'object' && value !== null) {
          if (seen.has(value)) {
            return '[Circular]';
          }
          seen.add(value);
        }
        if (typeof value === 'function') {
          return '[Function]';
        }
        return value;
      });
      console.log('Context data:', safeContext);
    } catch (e) {
      console.log('Context (could not stringify):', String(e.message));
      // Log individual context properties
      if (data.context && typeof data.context === 'object') {
        Object.keys(data.context).forEach(key => {
          try {
            console.log(`  ${key}:`, typeof data.context[key], Array.isArray(data.context[key]) ? `[Array(${data.context[key].length})]` : data.context[key]);
          } catch (err) {
            console.log(`  ${key}: [Error logging]`);
          }
        });
      }
    }
  }
  console.log('============================');
  
  // Verify App Check (if enabled)
  // Note: Uncomment when App Check is set up
  // if (!context.app) {
  //   throw new functions.https.HttpsError(
  //     'failed-precondition',
  //     'App check verification failed'
  //   );
  // }

  // TEMPORARILY COMMENTED OUT FOR DEBUGGING - Device ID validation
  // Validate input - accept either userId (from Google Sign-In) or deviceId
  // const userIdentifier = data.userId || data.deviceId;
  // const identifierType = data.userId ? 'userId' : 'deviceId';
  
  // if (!userIdentifier || userIdentifier === '' || typeof userIdentifier !== 'string') {
  //   console.error('Invalid user identifier received:', { 
  //     userId: data.userId,
  //     deviceId: data.deviceId,
  //     type: typeof userIdentifier,
  //     dataKeys: Object.keys(data || {})
  //   });
  //   throw new functions.https.HttpsError(
  //     'invalid-argument',
  //     'userId or deviceId is required and must be a non-empty string'
  //   );
  // }
  
  // Use a default identifier for now (bypassing validation)
  const userIdentifier = data.userId || data.deviceId || 'debug-user-' + Date.now();
  const identifierType = data.userId ? 'userId' : (data.deviceId ? 'deviceId' : 'debug');
  console.log(`Request from ${identifierType}: ${userIdentifier} (validation bypassed)`);

  // Check for operation - handle both data.operation and data.data.operation (in case of nested structure)
  const operation = data.operation || (data.data && data.data.operation);
  if (!operation) {
    console.error('Operation missing! Data structure:', {
      hasOperation: !!data.operation,
      hasDataOperation: !!(data.data && data.data.operation),
      dataKeys: Object.keys(data || {}),
      dataType: typeof data
    });
    throw new functions.https.HttpsError(
      'invalid-argument',
      'operation is required (chat, analysis, scheduleSuggestions). Received data keys: ' + Object.keys(data || {}).join(', ')
    );
  }
  
  // Use the operation we found
  data.operation = operation;

  // TEMPORARILY COMMENTED OUT FOR DEBUGGING - Rate limiting and cooldown
  // // Check cooldown first (15 seconds between successful requests of the same type)
  // // Note: Cooldown is only checked, not updated here - it will be updated after success
  // const cooldown = await checkCooldown(userIdentifier, data.operation, false); // false = don't update yet
  // if (!cooldown.allowed) {
  //   const operationName = data.operation === 'chat' ? 'chat message' :
  //                        data.operation === 'analysis' ? 'analysis' :
  //                        'suggestions';
  //   throw new functions.https.HttpsError(
  //     'resource-exhausted',
  //     `Please wait ${cooldown.waitSeconds} second(s) before another ${operationName} request.`,
  //     { waitSeconds: cooldown.waitSeconds, cooldown: COOLDOWN_SECONDS }
  //   );
  // }

  // // Check daily rate limit for this specific operation
  // const rateLimit = await checkRateLimit(userIdentifier, data.operation);
  // if (!rateLimit.allowed) {
  //   const operationName = data.operation === 'chat' ? 'chat messages' :
  //                        data.operation === 'analysis' ? 'analysis refreshes' :
  //                        'suggestions refreshes';
  //   throw new functions.https.HttpsError(
  //     'resource-exhausted',
  //     `Daily limit of ${rateLimit.limit} ${operationName} reached. Please try again tomorrow.`,
  //     { remaining: 0, limit: rateLimit.limit }
  //   );
  // }
  
  // Bypass rate limiting - use dummy values
  const rateLimit = { remaining: 999, limit: 999 };

  // Initialize Gemini API
  if (!GEMINI_API_KEY) {
    throw new functions.https.HttpsError(
      'internal',
      'Gemini API key not configured'
    );
  }

  const genAI = new GoogleGenerativeAI(GEMINI_API_KEY);
  let model;
  let prompt;

  try {
    // Route to appropriate model and build prompt based on operation
    switch (data.operation) {
      case 'chat':
        if (!data.prompt || typeof data.prompt !== 'string') {
          throw new functions.https.HttpsError(
            'invalid-argument',
            'prompt is required for chat operation and must be a string'
          );
        }
        model = genAI.getGenerativeModel({ model: 'gemini-2.5-flash' });
        prompt = buildChatPrompt(data.prompt, data.context);
        break;

      case 'analysis':
        model = genAI.getGenerativeModel({ model: 'gemini-2.5-flash' });
        prompt = buildAnalysisPrompt(data.context);
        break;

      case 'scheduleSuggestions':
        model = genAI.getGenerativeModel({ model: 'gemini-2.5-flash' });
        prompt = buildScheduleSuggestionPrompt(data.context);
        break;

      default:
        throw new functions.https.HttpsError(
          'invalid-argument',
          `Unknown operation: ${data.operation}`
        );
    }

    // Call Gemini API
    const result = await model.generateContent(prompt);
    const response = result.response;
    const text = response.text();

    // TEMPORARILY COMMENTED OUT FOR DEBUGGING - Cooldown update
    // Only update cooldown after successful API call
    // await updateCooldown(userIdentifier, data.operation);

    // Return result with rate limit info
    return {
      result: text,
      remaining: rateLimit.remaining,
      limit: rateLimit.limit,
    };
  } catch (error) {
    console.error('Gemini API error:', error);
    console.error('Error stack:', error.stack);
    console.error('Error details:', JSON.stringify(error, Object.getOwnPropertyNames(error)));
    
    // If it's a rate limit error from Gemini, pass it through
    if (error.message && error.message.includes('quota')) {
      throw new functions.https.HttpsError(
        'resource-exhausted',
        'API quota exceeded. Please try again later.',
        { remaining: rateLimit.remaining, limit: rateLimit.limit }
      );
    }
    
    // If it's already an HttpsError, re-throw it
    if (error instanceof functions.https.HttpsError) {
      throw error;
    }

    throw new functions.https.HttpsError(
      'internal',
      `Gemini API error: ${error.message || 'Unknown error'}`,
      { remaining: rateLimit.remaining, limit: rateLimit.limit, errorDetails: error.toString() }
    );
  }
});

/**
 * Build prompt for chat operation
 */
function buildChatPrompt(userPrompt, context) {
  // Validate userPrompt
  if (!userPrompt || typeof userPrompt !== 'string') {
    throw new Error('userPrompt is required and must be a string');
  }
  
  const { days, currentScores, bodyFatPercent } = context || {};
  
  let contextText = 'You are Gymini, an AI fitness coach. Always refer to yourself as "Gymini" in your responses. You can use pronouns when referring to yourself.\n\n';
  
  if (currentScores && typeof currentScores === 'object') {
    contextText += `Current Muscle Group Scores (0-100):\n`;
    contextText += `- Chest: ${currentScores.chest || 0}/100\n`;
    contextText += `- Back: ${currentScores.back || 0}/100\n`;
    contextText += `- Core: ${currentScores.core || 0}/100\n`;
    contextText += `- Shoulders: ${currentScores.shoulders || 0}/100\n`;
    contextText += `- Arms: ${currentScores.arms || 0}/100\n`;
    contextText += `- Legs: ${currentScores.legs || 0}/100\n`;
  }
  
  if (bodyFatPercent && typeof bodyFatPercent === 'number') {
    contextText += `- Body Fat: ${bodyFatPercent}%\n`;
  }
  
  if (days && Array.isArray(days) && days.length > 0) {
    contextText += `\nWorkout History: ${days.length} workout days recorded.\n`;
  }
  
  contextText += `\nUser Question: ${userPrompt}\n\nPlease provide a helpful response based on this context.`;
  
  return contextText;
}

/**
 * Build prompt for comprehensive analysis
 */
function buildAnalysisPrompt(context) {
  const {
    currentScores,
    bodyFatPercent,
    goals,
    goalBodyFat,
    days,
    hasPhotos,
  } = context || {};

  let prompt = `You are Gymini, an AI fitness coach. Always refer to yourself as "Gymini" in your responses. You can use pronouns when referring to yourself.

Provide a comprehensive fitness analysis and action plan.

CURRENT STATUS:
Muscle Group Scores (0-100):
- Chest: ${currentScores?.chest || 0}/100
- Back: ${currentScores?.back || 0}/100
- Core: ${currentScores?.core || 0}/100
- Shoulders: ${currentScores?.shoulders || 0}/100
- Arms: ${currentScores?.arms || 0}/100
- Legs: ${currentScores?.legs || 0}/100
- Body Fat: ${bodyFatPercent || 0}%

`;

  if (goals || goalBodyFat) {
    prompt += `GOALS:\n`;
    if (goals) {
      prompt += `Target Muscle Group Scores:\n`;
      prompt += `- Chest: ${goals.chest || currentScores?.chest || 0}/100\n`;
      prompt += `- Back: ${goals.back || currentScores?.back || 0}/100\n`;
      prompt += `- Core: ${goals.core || currentScores?.core || 0}/100\n`;
      prompt += `- Shoulders: ${goals.shoulders || currentScores?.shoulders || 0}/100\n`;
      prompt += `- Arms: ${goals.arms || currentScores?.arms || 0}/100\n`;
      prompt += `- Legs: ${goals.legs || currentScores?.legs || 0}/100\n`;
    }
    if (goalBodyFat) {
      prompt += `- Target Body Fat: ${goalBodyFat}%\n`;
    }
    prompt += `\n`;
  }

  if (days && days.length > 0) {
    prompt += `WORKOUT DATA:\n`;
    prompt += `- Total workout days: ${days.length}\n`;
    const totalExercises = days.reduce((sum, day) => sum + (day.exercises?.length || 0), 0);
    prompt += `- Total exercises: ${totalExercises}\n\n`;
  }

  if (hasPhotos) {
    prompt += `Body photos have been uploaded for visual assessment.\n\n`;
  }

  prompt += `Provide a comprehensive analysis including:
1. Current fitness assessment
2. Areas that need improvement
3. Specific recommendations to reach your goals
4. Action plan with concrete steps`;

  return prompt;
}

/**
 * Build prompt for schedule suggestions
 */
function buildScheduleSuggestionPrompt(context) {
  const { schedule, allExerciseCards, scores } = context || {};
  const dayNames = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];

  let scheduleText = 'Current Weekly Schedule:\n';
  if (schedule) {
    for (let i = 0; i < 7; i++) {
      const items = schedule[i] || [];
      if (items.length > 0) {
        const exerciseNames = items.map(item => {
          if (item.type === 'rest') return 'Rest';
          if (item.type === 'exercise' && allExerciseCards) {
            const card = allExerciseCards.find(c => c.id === item.cardId);
            return card ? card.equipmentName : 'Exercise';
          }
          return 'Exercise';
        });
        scheduleText += `${dayNames[i]}: ${exerciseNames.join(', ')}\n`;
      }
    }
  }

  const weakGroups = scores ? Object.keys(scores)
    .filter(key => scores[key] < 50)
    .map(key => key.charAt(0).toUpperCase() + key.slice(1))
    .join(', ') : '';

  const strongGroups = scores ? Object.keys(scores)
    .filter(key => scores[key] >= 70)
    .map(key => key.charAt(0).toUpperCase() + key.slice(1))
    .join(', ') : '';

  return `You are Gymini, an AI fitness coach. Provide brief schedule optimization suggestions (keep response under 200 words).

${scheduleText}

Muscle Group Scores:
- Chest: ${scores?.chest || 0}/100
- Back: ${scores?.back || 0}/100
- Core: ${scores?.core || 0}/100
- Shoulders: ${scores?.shoulders || 0}/100
- Arms: ${scores?.arms || 0}/100
- Legs: ${scores?.legs || 0}/100

${weakGroups ? `Weakest Muscle Groups: ${weakGroups}\n` : ''}
${strongGroups ? `Strongest Muscle Groups: ${strongGroups}\n` : ''}

Based on the current schedule and muscle scores, suggest 2-3 actionable improvements to optimize the weekly workout routine. Focus on balancing muscle groups, ensuring adequate rest, and targeting weaker areas.`;
}


