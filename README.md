# Gym Notes

A comprehensive Android fitness tracking app built with Kotlin and Jetpack Compose. Track your workouts, monitor muscle group progress, visualize your body composition with 3D models, and get AI-powered fitness insights.

## Features

### 🏋️ Workout Tracking
- Create and organize workout days by muscle groups
- Add exercises with sets, reps, weight, and notes
- Track weight progression over time with visual charts
- Support for custom muscle groups and exercises
- Video attachments for exercise demonstrations

### 📊 Progress Monitoring
- **Muscle Group Scoring**: Automatic scoring system based on workout data
- **3D Body Visualization**: Interactive 3D model that morphs based on your muscle scores and body fat percentage
- **Progress Charts**: Visual representation of weight progression for each exercise
- **Goal Setting**: Set target scores for muscle groups and body fat percentage
- **Goal Tracking**: Visual indicators showing progress toward your goals

### 🤖 AI-Powered Insights (Gymini)
- **Photo Analysis**: Upload front and back body photos for AI analysis
- **Muscle Mass Visualization**: AI-generated visualizations based on your photos and fitness metrics
- **Workout Suggestions**: Personalized recommendations based on your exercise data
- **Progress Analysis**: Get insights on which muscle groups need more work
- **Toggle Visualization**: Switch between 3D model and AI-generated images

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
   - Get your API key from [Google AI Studio](https://makersuite.google.com/app/apikey)
   - Open `app/src/main/java/com/goofyapps/gymnotes/GeminiAIService.kt`
   - Replace `"YOUR_GEMINI_API_KEY_HERE"` on line 24 with your actual API key

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
│   │   │       ├── body_morph18.glb    # 3D body models
│   │   │       ├── body_morph19.glb
│   │   │       └── body_morph20.glb
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
- **Google Generative AI SDK**: Gemini AI integration
  - Text analysis and suggestions
  - Image generation (Gemini 2.5 Flash Image)

### Other
- **Compose Reorderable**: Drag-and-drop reordering
- **Coil**: Image loading

## Usage

### Getting Started

1. **Set Up Profile**: Go to Settings → Profile and enter your weight, height, body fat percentage, and sex
2. **Add Workouts**: Navigate to the Workout tab and create workout days
3. **Add Exercises**: For each workout day, add exercises with sets, reps, and weights
4. **Track Progress**: View your progress in the Progress tab with 3D visualization and charts
5. **Set Goals**: Use Goal Mode to set target scores for muscle groups
6. **AI Analysis**: Use the Gymini tab to upload photos and get AI-powered insights

### Muscle Group Scoring

The app automatically calculates scores for each muscle group based on:
- Exercise volume (sets × reps × weight)
- Exercise frequency
- Weight progression over time
- Calibration based on your profile data

### AI Features (Gymini)

1. **Upload Photos**: Upload front and back full-body photos (one-time setup)
2. **Analyze**: Click "Analyze with AI" to get insights and generate visualizations
3. **View Results**: See AI-generated muscle mass visualizations and suggestions
4. **Toggle View**: In Progress tab, toggle between 3D model and AI-generated image

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

- **SharedPreferences**: Used for storing workout data, profile, goals, and settings
- **JSON Format**: Workout data is serialized to JSON for export/import
- **Local Storage**: Photos and generated images are stored in app cache

## Known Issues / TODOs

- [ ] Implement Gemini 2.5 Flash Image generation (API structure verification needed)
- [ ] Add prompt function to Gymini tab
- [ ] Improve image extraction from Gemini API responses

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

[Add your license here]

## Support

For issues and questions, please open an issue on the repository.

---

**Note**: This app requires a Gemini API key for AI features. Get yours at [Google AI Studio](https://makersuite.google.com/app/apikey).

