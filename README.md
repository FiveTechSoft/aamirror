# AA Mirror

Phone screen mirroring to Android Auto car display with bidirectional touch passthrough.

**Target:** Samsung S23+ (Android 14, One UI 6.x) — no root required.

## How it works

```
Phone Screen → MediaProjection → ImageReader → OpenGL ES → AA Surface → Car Display
Car Touch   → SurfaceCallback → Messenger IPC → AccessibilityService → Phone
```

Two processes in same APK:
- **Main process**: MediaProjection capture, OpenGL render, AccessibilityService touch injection
- **Car process**: Android Auto `CarAppService` with `SurfaceCallback` template, binds via Messenger IPC

## Build Requirements (PC)

| Software | Version | Download |
|----------|---------|----------|
| **JDK** | 17+ | [Adoptium Temurin 17](https://adoptium.net/download/) |
| **Android SDK** | API 34 | Via [Android Studio](https://developer.android.com/studio) or [command line tools](https://developer.android.com/studio#command-line-tools-only) |
| **Android Build Tools** | 34.0.0 | Installed via SDK Manager |
| **Gradle** | 8.4 | Auto-downloaded by wrapper (`gradlew`) |

### Environment Variables

```
JAVA_HOME=C:\path\to\jdk-17
ANDROID_HOME=C:\path\to\Android\Sdk
```

### SDK Packages Required

```
build-tools;34.0.0
platforms;android-34
```

Install via:
```bash
sdkmanager "build-tools;34.0.0" "platforms;android-34"
```

## Build

```bash
git clone https://github.com/FiveTechSoft/aamirror.git
cd aamirror
./gradlew :app:assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

## Install on Phone

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Device Setup

### 1. Enable Android Auto Developer Mode
- Open Android Auto app → Settings → Tap **Version** 10 times until "Developer mode enabled"
- Tap ⋮ → **Developer settings** → Enable **Unknown sources**

### 2. Grant Permissions
- Settings → Apps → AA Mirror → **Display over other apps** → Allow
- Settings → Accessibility → **AA Mirror Touch** → Enable
- Settings → Apps → AA Mirror → **Notifications** → Allow

### 3. Use
- Open AA Mirror → tap **Start Capture** → grant screen recording dialog
- Connect phone to car (USB or wireless Android Auto)
- AA Mirror appears in car app list → launch it
- Phone screen mirrors to car display, touch on car controls phone

## Permissions Explained

| Permission | Why |
|------------|-----|
| `SYSTEM_ALERT_WINDOW` | Required by MediaProjection (screen capture) |
| `FOREGROUND_SERVICE` + `mediaProjection` | Keep capture alive in background |
| `BIND_ACCESSIBILITY_SERVICE` | Inject touch from car to phone |
| `POST_NOTIFICATIONS` | Foreground service notification |

## Known Limitations

- MediaProjection consent dialog every session (Android security, no-root)
- ~30fps capture, ~50-100ms touch latency
- DRM content (Netflix, etc.) shows black
- Audio plays through car Bluetooth normally (not mirrored)
- May break on Android Auto app updates (Google restricts custom surfaces)

## Tech Stack

Kotlin, Gradle 8.4, AGP 8.2.0, OpenGL ES 2.0, `androidx.car.app:app:1.4.0`, API 34 (min 29)

## License

MIT
