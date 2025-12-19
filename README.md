# Gym Notes

A comprehensive Android fitness tracking app built with Kotlin and Jetpack Compose. Track your workouts, monitor muscle group progress, visualize your body composition with 3D models, and get AI-powered fitness insights.

## Features

### 🏋️ Workout Tracking
- Create and organize workout days by muscle groups
- Add exercises with sets, reps, weight, and notes
- Track weight progression over time with visual charts
- Support for custom muscle groups and exercises
- Video attachments for exercise demonstrations
- **Weekly Schedule**: Plan your workouts across the week (Monday-Sunday)
  - Add exercise cards or rest days to specific days
  - Organize by muscle groups
  - Easy drag-and-drop interface

### 📊 Progress Monitoring
- **Muscle Group Scoring**: Automatic scoring system based on workout data
- **3D Body Visualization**: Interactive 3D model that morphs based on your muscle scores and body fat percentage
- **Progress Charts**: Visual representation of weight progression for each exercise
- **Goal Setting**: Set target scores for muscle groups and body fat percentage
- **Goal Tracking**: Visual indicators showing progress toward your goals

### 🤖 AI-Powered Insights (Gymini)
- **Analysis Tab**:
  - Upload front and back body photos for comprehensive AI analysis
  - Get detailed fitness analysis with personalized recommendations
  - View AI-generated muscle mass visualizations
  - Receive schedule optimization suggestions based on your weekly plan
  - All analysis results persist across app restarts
- **Chat Tab**:
  - WhatsApp-style chat interface with Gymini
  - Ask any fitness-related question and get personalized responses
  - Your workout data, muscle scores, and body fat are automatically included as context
  - Chat history is saved and retained
- **Toggle Visualization**: Switch between 3D model and AI-generated images in Progress/Goal Mode tabs

### ⚙️ Customization
- **Unit System**: Toggle between metric (kg/cm) and imperial (lb/ft) units
- **Profile Management**: Track weight, height, body fat percentage, and sex
- **Data Export/Import**: Backup and restore your workout data

## Setup

### Prerequisites
- Android Studio (latest version)
- Android SDK 24+ (minimum)
- Kotlin
- JDK 11+

### Installation

1. Clone the repository:
```bash
git clone <repository-url>
cd GymNotes
```

2. Open the project in Android Studio

3. Sync Gradle dependencies

4. **Configure Gemini API Key** (Required for AI features):
   - Get your API key from [Google AI Studio](https://aistudio.google.com/app/apikey)
   - Open `local.properties` in the project root
   - Add this line: `GEMINI_API_KEY=your_actual_api_key_here`
   - Replace `your_actual_api_key_here` with your actual Gemini API key
   - **Note**: `local.properties` is already in `.gitignore`, so your API key won't be committed to version control

5. Build and run the app

## Project Structure

```
app/
├── src/
│   ├── main/
│   │   ├── java/com/goofyapps/gymnotes/
│   │   │   ├── MainActivity.kt          # Main app logic, UI, and navigation
│   │   │   ├── BodyScoreFigure3D.kt    # 3D body visualization component
│   │   │   ├── GeminiAIService.kt      # AI service for Gemini integration
│   │   │   └── MuscleTaxonomy.kt       # Muscle group definitions
│   │   ├── assets/
│   │   │   └── models/
│   │   │       └── body_morph25.glb    # 3D body model with morph targets
│   │   └── res/                        # Resources (drawables, values, etc.)
│   └── build.gradle.kts               # App dependencies and configuration
```

## Dependencies

### Core
- **Jetpack Compose**: Modern Android UI toolkit
- **Material 3**: Material Design components
- **Kotlin Coroutines**: Asynchronous programming

### 3D Visualization
- **SceneView**: 3D model rendering and morphing
- GLB model support for body visualization

### AI Integration
- **Google Generative AI SDK** (v0.2.2): Gemini AI integration
  - Text analysis and comprehensive fitness analysis
  - Schedule optimization suggestions
  - Custom prompt responses with workout context
  - Image generation (Gemini 2.5 Flash Image - in progress)

### Other
- **Compose Reorderable**: Drag-and-drop reordering
- **Coil**: Image loading

## Usage

### Getting Started

1. **Set Up Profile**: Go to Settings → Profile and enter your weight, height, body fat percentage, and sex
2. **Add Workouts**: Navigate to the Workout tab → Muscles sub-tab and create workout days
3. **Add Exercises**: For each workout day, add exercises with sets, reps, and weights
4. **Plan Schedule**: Use the Workout tab → Schedule sub-tab to plan your weekly workouts
5. **Track Progress**: View your progress in the Progress tab with 3D visualization and charts
6. **Set Goals**: Use Goal Mode (via "Goal" button) to set target scores for muscle groups and body fat
7. **AI Analysis**: Use the Gymini tab → Analysis sub-tab to upload photos and get AI-powered insights
8. **Chat with AI**: Use the Gymini tab → Chat sub-tab to ask fitness questions

### Muscle Group Scoring

The app automatically calculates scores for each muscle group based on:
- Exercise volume (sets × reps × weight)
- Exercise frequency
- Weight progression over time
- Calibration based on your profile data

### AI Features (Gymini)

#### Analysis Tab
1. **Upload Photos**: Add front and back full-body photos for analysis
2. **Visual Assessment**: View AI-generated muscle mass visualizations
3. **Comprehensive Analysis**: 
   - Click the refresh button to get detailed analysis
   - Analysis includes current status, goals, workout history, and personalized action plan
   - Results are saved and persist across app restarts
4. **Schedule Suggestions**: 
   - Get AI-powered recommendations for optimizing your weekly workout schedule
   - Suggestions focus on muscle group distribution, weak areas, and rest day optimization
   - Results are saved and can be refreshed anytime

#### Chat Tab
1. **Chat with Gymini**: WhatsApp-style interface for asking fitness questions
2. **Context-Aware**: Your workout data, muscle scores, and body fat are automatically included
3. **Persistent History**: All conversations are saved and retained

#### Visualization Toggle
- In Progress and Goal Mode tabs, toggle between 3D model and AI-generated image
- Toggle state is saved and persists across app restarts

## Configuration

### Gemini API Setup

The app uses Google's Gemini AI for:
- Body photo analysis
- Muscle mass visualization generation
- Workout suggestions

**Note**: Image generation features are currently being configured. Text analysis works, but image generation requires API verification.

### Unit System

Switch between metric and imperial units in Settings → Units. All data is stored internally in metric (kg/cm) for consistency.

## Data Storage

- **SharedPreferences**: Used for storing workout data, profile, goals, settings, and AI data
  - Critical data (workouts, profile, goals, schedule, chat history, analysis) uses synchronous `.commit()` to prevent data loss
  - Preferences (unit system, UI toggles) use asynchronous `.apply()` for better performance
- **JSON Format**: Workout data, goals, schedule, and chat history are serialized to JSON
- **Local Storage**: Photos and generated images are stored in app cache directory
- **Data Persistence**: All user data persists across app restarts with proper error handling

## Technical Improvements

- ✅ **Data Safety**: Critical user data uses synchronous `.commit()` to prevent data loss on crashes
- ✅ **Null Safety**: All unsafe null assertions removed, proper null handling throughout
- ✅ **Error Handling**: Robust JSON parsing with fallback values and error recovery
- ✅ **API Compatibility**: Replaced deprecated `Uri.fromFile()` with modern file URI handling
- ✅ **Code Quality**: Comprehensive technical debt review and fixes applied

## Known Issues / TODOs

- [ ] Implement Gemini 2.5 Flash Image generation (API structure verification needed)
- [x] Add prompt function to Gymini tab (Chat tab)
- [x] Add schedule feature with weekly planning
- [x] Add schedule suggestions from AI
- [x] Add comprehensive analysis with persistence
- [x] Improve data persistence and error handling
- [ ] Move Gemini API key to secure backend proxy (security improvement)
  - [ ] Implement Firebase Functions backend
  - [ ] Add rate limiting: 10 free prompts per day per device
  - [ ] Migrate all Gemini API calls to use backend proxy
- [ ] Add image extraction from Gemini API responses when image generation is implemented
- [ ] **Empty State Suggestions**: When there is no schedule, no muscle groups, or no cards in a muscle group, show an option to create suggested cards, groups, and schedule (onboarding/UX improvement)

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

[Add your license here]

## Support

For issues and questions, please open an issue on the repository.

---

**Note**: This app requires a Gemini API key for AI features. Get yours at [Google AI Studio](https://aistudio.google.com/app/apikey).

## Version

Current version: **1.2.1** (versionCode: 6)

### Recent Updates
- Added weekly schedule planning feature
- Enhanced Gymini with Analysis and Chat sub-tabs
- Added schedule optimization suggestions
- Improved data persistence and error handling
- Fixed critical technical debt issues
- Enhanced UI with collapsible cards and better organization

