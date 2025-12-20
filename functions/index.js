/**
 * functions/index.js
 * - Callable function: testFirestore
 * - Callable function: geminiProxy (rate limited + cooldown)
 * - Uses NAMED Firestore database: gymdb
 *
 * IMPORTANT:
 * - Initialize admin ONLY ONCE.
 * - Use getFirestore(app, "gymdb") for named database.
 */

const functions = require("firebase-functions");
const admin = require("firebase-admin");
const { getFirestore } = require("firebase-admin/firestore");
const { GoogleGenerativeAI } = require("@google/generative-ai");

// ✅ Initialize Admin once
admin.initializeApp();

// ✅ Connect to NAMED Firestore database "gymdb"
const db = getFirestore(admin.app(), "gymdb");

// Optional Firestore settings
db.settings({ ignoreUndefinedProperties: true });

// Log once (helps debugging)
console.log("Admin initialized. Project:", process.env.GCLOUD_PROJECT);
console.log("Using Firestore databaseId: gymdb");

// Get Gemini API key from Firebase Secrets
// Secret is set via: firebase functions:secrets:set GEMINI_API_KEY
// It's automatically available as process.env.GEMINI_API_KEY when deployed
const GEMINI_API_KEY = process.env.GEMINI_API_KEY;

// Daily limits for different operations
const DAILY_CHAT_LIMIT = 20; // 20 chat messages per day
const DAILY_ANALYSIS_LIMIT = 1; // 1 analysis refresh per day
const DAILY_SUGGESTIONS_LIMIT = 1; // 1 suggestions refresh per day

// Cooldown between requests (30 seconds)
const COOLDOWN_SECONDS = 30;

/**
 * Check cooldown period for a user/device and operation type.
 * Cooldown is per-operation, so you can switch between chat/analysis/suggestions.
 * NOTE: This implementation "claims" cooldown immediately (fail-closed for concurrency).
 *
 * @param {string} userIdentifier
 * @param {string} operation
 * @param {boolean} updateTimestamp - if true, sets lastRequestTime immediately
 * @returns {Promise<{allowed: boolean, waitSeconds: number}>}
 */
async function checkCooldown(userIdentifier, operation, updateTimestamp = true) {
  const cooldownRef = db
    .collection("cooldowns")
    .doc(`${userIdentifier}_${operation}`);

  try {
    const doc = await cooldownRef.get();
    const now = Date.now();

    if (doc.exists) {
      const data = doc.data();
      let lastRequestTime = data?.lastRequestTime;

      // Handle Firestore Timestamp if it's not a number
      if (lastRequestTime && typeof lastRequestTime.toMillis === "function") {
        lastRequestTime = lastRequestTime.toMillis();
      }

      if (lastRequestTime && typeof lastRequestTime === "number") {
        const timeSinceLastRequest = (now - lastRequestTime) / 1000;
        if (timeSinceLastRequest < COOLDOWN_SECONDS) {
          const waitSeconds = Math.ceil(
            COOLDOWN_SECONDS - timeSinceLastRequest
          );
          console.log(
            `Cooldown active: ${waitSeconds}s remaining for ${userIdentifier}, op=${operation}`
          );
          return { allowed: false, waitSeconds };
        }
      }
    }

    if (updateTimestamp) {
      await cooldownRef.set(
        {
          lastRequestTime: now,
        },
        { merge: true }
      );
    }

    return { allowed: true, waitSeconds: 0 };
  } catch (error) {
    console.error("Error checking cooldown:", error?.code, error?.message || error);

    // Fail-open for cooldown errors (but still try to set timestamp)
    if (updateTimestamp) {
      try {
        const now = Date.now();
        await cooldownRef.set({ lastRequestTime: now }, { merge: true });
        console.log(
          `Cooldown timestamp set after error for ${userIdentifier}, op=${operation}: ${now}`
        );
      } catch (updateError) {
        console.error("Error setting cooldown timestamp after error:", updateError);
      }
    }

    return { allowed: true, waitSeconds: 0 };
  }
}

/**
 * Check and update rate limit for a user/device based on operation type
 * @param {string} userIdentifier
 * @param {string} operation
 * @returns {Promise<{allowed: boolean, remaining: number, limit: number}>}
 */
async function checkRateLimit(userIdentifier, operation) {
  const today = new Date().toISOString().split("T")[0]; // YYYY-MM-DD

  let dailyLimit;
  let limitKey;

  switch (operation) {
    case "chat":
      dailyLimit = DAILY_CHAT_LIMIT;
      limitKey = "chatCount";
      break;
    case "analysis":
      dailyLimit = DAILY_ANALYSIS_LIMIT;
      limitKey = "analysisCount";
      break;
    case "scheduleSuggestions":
      dailyLimit = DAILY_SUGGESTIONS_LIMIT;
      limitKey = "suggestionsCount";
      break;
    default:
      dailyLimit = DAILY_CHAT_LIMIT;
      limitKey = "chatCount";
  }

  const limitDocId = `${userIdentifier}_${today}`;
  const rateLimitRef = db.collection("rateLimits").doc(limitDocId);

  try {
    const doc = await rateLimitRef.get();
    const data = doc.exists ? doc.data() : {};
    const currentCount = data?.[limitKey] || 0;

    if (currentCount >= dailyLimit) {
      return { allowed: false, remaining: 0, limit: dailyLimit };
    }

    const updateData = {
      userIdentifier,
      date: today,
      [limitKey]: currentCount + 1,
      lastUpdated: admin.firestore.FieldValue.serverTimestamp(),
    };

    await rateLimitRef.set(updateData, { merge: true });

    return {
      allowed: true,
      remaining: dailyLimit - (currentCount + 1),
      limit: dailyLimit,
    };
  } catch (error) {
    console.error("Error checking rate limit:", error?.code, error?.message || error);

    // If it's a database NOT_FOUND error (code 5), treat as critical
    if (
      error?.code === 5 ||
      (typeof error?.message === "string" && error.message.includes("NOT_FOUND"))
    ) {
      console.error("CRITICAL: Firestore database NOT_FOUND in rate limit check");
      throw new Error(
        `Firestore database not accessible: ${error.message}. Check Firestore named DB 'gymdb' exists and IAM.`
      );
    }

    // Fail-open on other errors
    return { allowed: true, remaining: dailyLimit, limit: dailyLimit };
  }
}

/**
 * Simple test function - writes "hello world" to Firestore (gymdb)
 */
exports.testFirestore = functions.https.onCall(async (data, context) => {
  try {
    console.log('Test: Writing "hello world" to Firestore (gymdb)...');

    const testRef = db.collection("test").doc("hello");
    await testRef.set({
      message: "hello world",
      timestamp: admin.firestore.FieldValue.serverTimestamp(),
    });

    console.log('Success: Wrote "hello world" to Firestore (gymdb)');

    return {
      success: true,
      message: 'Successfully wrote "hello world" to Firestore (gymdb)!',
    };
  } catch (error) {
    console.error("Firestore test error:", error?.code, error?.message || error);
    return {
      success: false,
      error: {
        code: error?.code ?? null,
        message: error?.message ?? String(error),
      },
      message: `Failed: ${error?.message ?? String(error)}`,
    };
  }
});

/**
 * Proxy function for Gemini API calls with rate limiting
 * Supports operations: chat, analysis, scheduleSuggestions
 */
exports.geminiProxy = functions.https.onCall(async (requestData, context) => {
  // Callable functions typically pass data as requestData (v1) or requestData.data in some wrappers.
  const data = requestData?.data || requestData;

  // Debug logging
  console.log("=== BACKEND RECEIVED DATA ===");
  try {
    console.log("Extracted data keys:", Object.keys(data || {}));
  } catch {
    console.log("Extracted data keys: [unavailable]");
  }
  console.log("Operation:", data?.operation);
  console.log("============================");

  // Validate identifier: accept either userId or deviceId
  const userIdentifier = data?.userId || data?.deviceId;
  const identifierType = data?.userId ? "userId" : "deviceId";

  if (!userIdentifier || typeof userIdentifier !== "string" || userIdentifier.trim() === "") {
    console.error("Invalid user identifier received:", {
      userId: data?.userId,
      deviceId: data?.deviceId,
      type: typeof userIdentifier,
    });
    throw new functions.https.HttpsError(
      "invalid-argument",
      "userId or deviceId is required and must be a non-empty string"
    );
  }

  console.log(`Request from ${identifierType}: ${userIdentifier}`);

  // Validate operation
  const operation = data?.operation || (data?.data && data.data.operation);
  if (!operation) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "operation is required (chat, analysis, scheduleSuggestions)"
    );
  }
  data.operation = operation;

  // Cooldown (claims immediately to prevent parallel spam)
  const cooldown = await checkCooldown(userIdentifier, data.operation, true);
  if (!cooldown.allowed) {
    const operationName =
      data.operation === "chat"
        ? "chat message"
        : data.operation === "analysis"
        ? "analysis"
        : "suggestions";

    throw new functions.https.HttpsError(
      "resource-exhausted",
      `Please wait ${cooldown.waitSeconds} second(s) before another ${operationName} request.`,
      { waitSeconds: cooldown.waitSeconds, cooldown: COOLDOWN_SECONDS }
    );
  }

  // Daily rate limits
  const rateLimit = await checkRateLimit(userIdentifier, data.operation);
  if (!rateLimit.allowed) {
    const operationName =
      data.operation === "chat"
        ? "chat messages"
        : data.operation === "analysis"
        ? "analysis refreshes"
        : "suggestions refreshes";

    throw new functions.https.HttpsError(
      "resource-exhausted",
      `Daily limit of ${rateLimit.limit} ${operationName} reached. Please try again tomorrow.`,
      { remaining: 0, limit: rateLimit.limit }
    );
  }

  // Gemini API Key - check if secret is available
  if (!GEMINI_API_KEY) {
    throw new functions.https.HttpsError(
      "internal",
      "Gemini API key not configured. Set it with: firebase functions:secrets:set GEMINI_API_KEY"
    );
  }

  const genAI = new GoogleGenerativeAI(GEMINI_API_KEY);

  let model;
  let prompt;

  try {
    switch (data.operation) {
      case "chat":
        if (!data.prompt || typeof data.prompt !== "string") {
          throw new functions.https.HttpsError(
            "invalid-argument",
            "prompt is required for chat operation and must be a string"
          );
        }
        model = genAI.getGenerativeModel({ model: "gemini-2.5-flash" });
        prompt = buildChatPrompt(data.prompt, data.context);
        break;

      case "analysis":
        model = genAI.getGenerativeModel({ model: "gemini-2.5-flash" });
        prompt = buildAnalysisPrompt(data.context);
        break;

      case "scheduleSuggestions":
        model = genAI.getGenerativeModel({ model: "gemini-2.5-flash" });
        prompt = buildScheduleSuggestionPrompt(data.context);
        break;

      default:
        throw new functions.https.HttpsError(
          "invalid-argument",
          `Unknown operation: ${data.operation}`
        );
    }

    const result = await model.generateContent(prompt);
    const text = result?.response?.text?.() ?? "";

    return {
      result: text,
      remaining: rateLimit.remaining,
      limit: rateLimit.limit,
    };
  } catch (error) {
    console.error("Gemini API error:", error?.message || error);

    if (typeof error?.message === "string" && error.message.includes("quota")) {
      throw new functions.https.HttpsError(
        "resource-exhausted",
        "API quota exceeded. Please try again later.",
        { remaining: rateLimit.remaining, limit: rateLimit.limit }
      );
    }

    if (error instanceof functions.https.HttpsError) throw error;

    throw new functions.https.HttpsError(
      "internal",
      `Gemini API error: ${error?.message || "Unknown error"}`,
      {
        remaining: rateLimit.remaining,
        limit: rateLimit.limit,
        errorDetails: String(error),
      }
    );
  }
});

/**
 * Build prompt for chat operation
 */
function buildChatPrompt(userPrompt, context) {
  if (!userPrompt || typeof userPrompt !== "string") {
    throw new Error("userPrompt is required and must be a string");
  }

  const { days, currentScores, bodyFatPercent } = context || {};

  let contextText =
    'You are Gymini, an AI fitness coach. Always refer to yourself as "Gymini" in your responses. You can use pronouns when referring to yourself.\n\n';

  if (currentScores && typeof currentScores === "object") {
    contextText += `Current Muscle Group Scores (0-100):\n`;
    contextText += `- Chest: ${currentScores.chest || 0}/100\n`;
    contextText += `- Back: ${currentScores.back || 0}/100\n`;
    contextText += `- Core: ${currentScores.core || 0}/100\n`;
    contextText += `- Shoulders: ${currentScores.shoulders || 0}/100\n`;
    contextText += `- Arms: ${currentScores.arms || 0}/100\n`;
    contextText += `- Legs: ${currentScores.legs || 0}/100\n`;
  }

  if (typeof bodyFatPercent === "number") {
    contextText += `- Body Fat: ${bodyFatPercent}%\n`;
  }

  if (Array.isArray(days) && days.length > 0) {
    contextText += `\nWorkout History: ${days.length} workout days recorded.\n`;
  }

  contextText += `\nUser Question: ${userPrompt}\n\nPlease provide a helpful response based on this context.`;
  return contextText;
}

/**
 * Build prompt for comprehensive analysis
 */
function buildAnalysisPrompt(context) {
  const { currentScores, bodyFatPercent, goals, goalBodyFat, days, hasPhotos } =
    context || {};

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
      prompt += `- Shoulders: ${
        goals.shoulders || currentScores?.shoulders || 0
      }/100\n`;
      prompt += `- Arms: ${goals.arms || currentScores?.arms || 0}/100\n`;
      prompt += `- Legs: ${goals.legs || currentScores?.legs || 0}/100\n`;
    }
    if (goalBodyFat) {
      prompt += `- Target Body Fat: ${goalBodyFat}%\n`;
    }
    prompt += `\n`;
  }

  if (Array.isArray(days) && days.length > 0) {
    prompt += `WORKOUT DATA:\n`;
    prompt += `- Total workout days: ${days.length}\n`;
    const totalExercises = days.reduce(
      (sum, day) => sum + (day.exercises?.length || 0),
      0
    );
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
  const dayNames = [
    "Monday",
    "Tuesday",
    "Wednesday",
    "Thursday",
    "Friday",
    "Saturday",
    "Sunday",
  ];

  let scheduleText = "Current Weekly Schedule:\n";
  if (schedule) {
    for (let i = 0; i < 7; i++) {
      const items = schedule[i] || [];
      if (items.length > 0) {
        const exerciseNames = items.map((item) => {
          if (item.type === "rest") return "Rest";
          if (item.type === "exercise" && allExerciseCards) {
            const card = allExerciseCards.find((c) => c.id === item.cardId);
            return card ? card.equipmentName : "Exercise";
          }
          return "Exercise";
        });
        scheduleText += `${dayNames[i]}: ${exerciseNames.join(", ")}\n`;
      }
    }
  }

  const weakGroups = scores
    ? Object.keys(scores)
        .filter((key) => scores[key] < 50)
        .map((key) => key.charAt(0).toUpperCase() + key.slice(1))
        .join(", ")
    : "";

  const strongGroups = scores
    ? Object.keys(scores)
        .filter((key) => scores[key] >= 70)
        .map((key) => key.charAt(0).toUpperCase() + key.slice(1))
        .join(", ")
    : "";

  return `You are Gymini, an AI fitness coach. Provide brief schedule optimization suggestions (keep response under 200 words).

${scheduleText}

Muscle Group Scores:
- Chest: ${scores?.chest || 0}/100
- Back: ${scores?.back || 0}/100
- Core: ${scores?.core || 0}/100
- Shoulders: ${scores?.shoulders || 0}/100
- Arms: ${scores?.arms || 0}/100
- Legs: ${scores?.legs || 0}/100

${weakGroups ? `Weakest Muscle Groups: ${weakGroups}\n` : ""}
${strongGroups ? `Strongest Muscle Groups: ${strongGroups}\n` : ""}

Based on the current schedule and muscle scores, suggest 2-3 actionable improvements to optimize the weekly workout routine. Focus on balancing muscle groups, ensuring adequate rest, and targeting weaker areas.`;
}
