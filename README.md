# ClipVault

Local-first clipboard history for Android. Captures, searches, and restores copied text with zero cloud dependency.

## Features

- Automatic clipboard capture with foreground service monitoring
- Full-text search across history
- Pin important clips to prevent pruning
- Smart content detection (URL, email, phone, code)
- Floating bubble overlay for quick access
- Shake-to-open gesture
- Home screen widget and quick-settings tile
- Dark theme with AMOLED black and dynamic colors
- Undo for deletions

## Requirements

- Android 8.0 (API 26) or higher
- Java 21 for building from source

## Build from source

```bash
git clone <repository-url>
cd ClipVault

# Debug APK
./gradlew :app:assembleDebug

# Release APK
./gradlew :app:assembleRelease

# Release AAB
./gradlew :app:bundleRelease
```

## Architecture

- **Language:** Kotlin 2.0.20
- **UI:** Jetpack Compose with Material 3
- **Pattern:** MVVM + Unidirectional Data Flow
- **DI:** Hilt with KSP
- **Database:** Room (SQLite) + DataStore Preferences
- **Services:** Foreground Service, Accessibility Service, Tile Service
- **Widget:** Glance AppWidget

## Project structure

```
app/src/main/java/com/clipvault/manager/
├── app/              # Application and Activity entry points
├── data/             # Repository, DAO, entities, preferences
├── di/               # Hilt dependency injection modules
├── domain/           # Domain models and business logic
├── haptic/           # Haptic feedback utilities
├── sensor/           # Sensor-based features (shake detection)
├── service/          # Background services and receivers
├── ui/               # Compose screens and components
└── widget/           # Glance home screen widget
```

## Privacy

All clipboard data is stored locally in a private SQLite database. The app has no network permissions except for optional URL title fetching when you tap a URL entry. No analytics, no tracking, no accounts.

See [PRIVACY_POLICY.md](PRIVACY_POLICY.md) for details.

## License

MIT
