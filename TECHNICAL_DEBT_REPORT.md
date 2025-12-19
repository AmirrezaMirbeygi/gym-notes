# Technical Debt & Code Quality Report

## 🔴 CRITICAL ISSUES (Fix Immediately)

### 1. **Security: API Key Exposure**
- **Location**: `GeminiAIService.kt:24`, `build.gradle.kts:31`
- **Issue**: API key is compiled into BuildConfig, making it extractable from APK
- **Risk**: Anyone can extract your API key and use it, leading to quota abuse and costs
- **Fix**: Move to secure backend proxy with Firebase App Check and rate limiting
- **Priority**: HIGH - Already in TODO list

### 2. **Deprecated API: Uri.fromFile()** ✅ FIXED
- **Location**: `GeminiAIService.kt:426`
- **Issue**: `Uri.fromFile()` is deprecated and won't work on Android 7.0+ (FileProvider required)
- **Risk**: App crashes when trying to display AI-generated images
- **Fix**: ✅ Changed to `Uri.parse("file://...")` for cache directory files
- **Status**: Fixed - Using file:// URI for app-private cache directory

### 3. **Unsafe Null Assertions (!!)** ✅ FIXED
- **Locations**: 
  - `MainActivity.kt:1439, 1457` - `selectedMuscleGroup!!`
  - `MainActivity.kt:1899, 1900` - `videoUri!!`
  - `MainActivity.kt:2739, 2740` - `frontPhotoUri!!`, `backPhotoUri!!`
- **Issue**: Force unwrapping can cause crashes if state is null
- **Risk**: App crashes in edge cases
- **Fix**: ✅ Replaced all `!!` with safe null handling:
  - `selectedMuscleGroup!!` → Safe local copy with early return
  - `videoUri!!` → Safe `?.let{}` block
  - `frontPhotoUri!!`, `backPhotoUri!!` → Safe local copies
- **Status**: Fixed - All null assertions removed

### 4. **Data Loss Risk: SharedPreferences .apply()** ✅ FIXED
- **Location**: Multiple places using `.apply()` instead of `.commit()`
- **Issue**: `.apply()` is asynchronous and can lose data if app crashes
- **Risk**: User data loss (goals, schedule, chat history, etc.)
- **Fix**: ✅ Changed to `.commit()` for all critical user data:
  - `saveDays()` - Workout data
  - `saveProfile()` - User profile
  - `saveGoals()` - User goals
  - `saveSchedule()` - Weekly schedule
  - `saveChatHistory()` - Chat messages
  - `saveAnalysisResult()` - AI analysis
  - `saveScheduleSuggestions()` - Schedule suggestions
- **Status**: Fixed - Critical data now uses synchronous `.commit()`

## 🟡 MEDIUM PRIORITY ISSUES

### 5. **JSON Parsing Error Handling**
- **Location**: `MainActivity.kt:302-368` (parseDaysFromJson), `parseProfile`, `parseGoals`, etc.
- **Issue**: JSON parsing can throw exceptions, but error handling is minimal
- **Risk**: Corrupted data causes app crashes
- **Fix**: Add try-catch blocks and provide fallback/default values
- **Priority**: MEDIUM

### 6. **Missing Input Validation**
- **Location**: Various input fields (weight, height, body fat, etc.)
- **Issue**: No validation for extreme values (negative, too large, NaN)
- **Risk**: Invalid data causes calculation errors or crashes
- **Fix**: Add input validation and sanitization
- **Priority**: MEDIUM

### 7. **Resource Leaks: File Streams**
- **Location**: `GeminiAIService.kt:225`, `BodyScoreFigure3D.kt:62`, `MainActivity.kt:2897`
- **Status**: ✅ GOOD - Using `.use{}` properly
- **Note**: Keep this pattern

### 8. **No Timeout for API Calls**
- **Location**: `GeminiAIService.kt` - All API calls
- **Issue**: Network requests can hang indefinitely
- **Risk**: Poor UX, battery drain
- **Fix**: Add timeout configuration to GenerativeModel or use withTimeout
- **Priority**: MEDIUM

### 9. **Memory Issues: Large Images**
- **Location**: Image loading in `BodyScoreFigure3D.kt`, `MainActivity.kt:2896`
- **Issue**: Loading full-resolution images without downscaling
- **Risk**: OutOfMemoryError on low-end devices
- **Fix**: Use BitmapFactory.Options to downscale or use Coil library
- **Priority**: MEDIUM

### 10. **No Error Recovery for Corrupted Data**
- **Location**: All data loading functions
- **Issue**: If JSON is corrupted, app might crash or show empty state
- **Risk**: User loses all data
- **Fix**: Add data validation, backup/restore, migration logic
- **Priority**: MEDIUM

## 🟢 LOW PRIORITY / CODE QUALITY

### 11. **Code Duplication**
- **Location**: Multiple serialization/deserialization patterns
- **Issue**: Similar code patterns repeated
- **Fix**: Extract common utilities
- **Priority**: LOW

### 12. **Magic Numbers**
- **Location**: Various calculations (e.g., `coerceIn(5f, 50f)`, `0.018f` scale)
- **Issue**: Hardcoded values without constants
- **Fix**: Extract to named constants
- **Priority**: LOW

### 13. **Missing Documentation**
- **Location**: Complex functions like `computeMuscleScores`, `scoreFromCalibration`
- **Issue**: Hard to understand algorithm logic
- **Fix**: Add KDoc comments
- **Priority**: LOW

### 14. **Incomplete Feature: Image Generation**
- **Location**: `GeminiAIService.kt:62-76, 203-209`
- **Issue**: TODOs for Gemini 2.5 Flash Image generation
- **Status**: Known limitation
- **Priority**: LOW (feature not yet implemented)

### 15. **Potential Race Conditions**
- **Location**: State updates in coroutines (e.g., `comprehensiveAnalysisResult`)
- **Issue**: State might update after composable disposal
- **Risk**: Memory leaks or crashes
- **Fix**: Check `isActive` or use proper cancellation
- **Priority**: LOW (Compose handles most of this)

### 16. **No Data Migration Strategy**
- **Location**: Data models (WorkoutDay, ExerciseCard, etc.)
- **Issue**: If data structure changes, old data might break
- **Fix**: Add version numbers and migration logic
- **Priority**: LOW

### 17. **Cache Directory Growth**
- **Location**: `GeminiAIService.kt:420` - Saving images to cache
- **Issue**: No cleanup of old generated images
- **Risk**: Cache directory grows indefinitely
- **Fix**: Implement cleanup logic or limit number of cached images
- **Priority**: LOW

### 18. **Missing Unit Tests**
- **Location**: All business logic
- **Issue**: No automated tests
- **Fix**: Add unit tests for critical functions
- **Priority**: LOW (but recommended for production)

## 📋 SUMMARY

**Critical Issues**: 4
**Medium Priority**: 6
**Low Priority**: 8

**Recommended Action Plan:**
1. Fix all CRITICAL issues first (especially security and crashes)
2. Address MEDIUM priority issues before next release
3. Gradually improve LOW priority items

**Estimated Fix Time:**
- Critical: 4-6 hours
- Medium: 6-8 hours
- Low: 8-12 hours (ongoing)

