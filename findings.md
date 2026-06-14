# AA Mirror — Testing & Debugging Findings

**Date:** 2026-06-14
**Device:** Samsung Galaxy S23+ (SM-S916U1), Android 14, One UI 6.x
**AA Host:** Google Play Services `com.google.android.projection.gearhead` v16.9.662034-release
**Branch:** `master` (commit `4b19bea`)

---

## Environment Setup

| Step | Result |
|------|--------|
| JDK 17 (Temurin 17.0.19+10) | ✅ Installed via winget |
| Android SDK (cmdline-tools) | ✅ Downloaded & extracted |
| `platform-tools` (adb) | ✅ r37.0.0 |
| `build-tools;34.0.0` | ✅ |
| `platforms;android-34` | ✅ |
| `ANDROID_HOME` | `%LOCALAPPDATA%\Android\Sdk` |
| `JAVA_HOME` | `C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot` |

**Note:** `gradlew.bat` had a hardcoded `JAVA_HOME` pointing to `c:\Android\jdk17\jdk-17.0.13+11` (non-existent). Fixed to point to the Temurin installation.

---

## Build

| Step | Result |
|------|--------|
| `gradlew :app:assembleDebug` | ✅ BUILD SUCCESSFUL (7m 38s first build, ~25s incremental) |
| APK | `app-debug.apk` — 9.96 MB |
| Warnings | 2 deprecation warnings (`getParcelableExtra`, `Notification.Builder.addAction`) |

---

## Installation

| Step | Result |
|------|--------|
| First install attempt | ❌ `INSTALL_FAILED_UPDATE_INCOMPATIBLE` — existing package with different signature |
| Fix | `adb uninstall com.aamirror` then `adb install` → ✅ Success |
| Subsequent reinstalls | `adb install -r` works fine |

---

## Permission Debugging

### SYSTEM_ALERT_WINDOW
- **Status:** ✅ `allow` — granted from the start

### POST_NOTIFICATIONS
- **Status:** Was `ignore` (blocked!), fixed to `allow`
- **Symptom:** `NotificationService: Suppressing notification from package com.aamirror by user request.`
- **Impact:** Without the foreground notification, Android kills `ScreenCaptureService` within seconds
- **Fix:** `appops set com.aamirror POST_NOTIFICATION allow`
- **Note:** `cmd notification set-preference` is not available; used `appops` instead

### BIND_ACCESSIBILITY_SERVICE (TouchInjectService)
- **Status:** Was in **"Crashed services"** list → appeared as "not enabled" in app
- **Symptom:** `Crashed services:{{com.aamirror/...TouchInjectService}}` in `dumpsys accessibility`
- **Impact:** App redirected to Accessibility Settings on "Start Capture"
- **Fix:** Toggle accessibility off → kill app → re-enable:
  ```
  settings put secure enabled_accessibility_services ''
  settings put secure accessibility_enabled 0
  am force-stop com.aamirror
  settings put secure enabled_accessibility_services com.aamirror/com.aamirror.TouchInjectService
  settings put secure accessibility_enabled 1
  ```
- **Result:** `Crashed services:{}` (clean)

### Battery Optimization
- **Status:** Added to whitelist via `dumpsys deviceidle whitelist +com.aamirror`
- **Samsung FreecessController:** Aggressively freezes/unfreezes `com.aamirror` with reason `Bg [cached:X]`

---

## ScreenCaptureService Issues

### MediaProjection "stopped by system"
- **Observed:** `ScreenCaptureService: MediaProjection stopped by system` multiple times
- **Root cause:** Notifications were blocked (`POST_NOTIFICATION: ignore`) → foreground service notification suppressed → Android kills the service
- **Fix:** Enabled notifications (see POST_NOTIFICATIONS above)

### Service lifecycle observations
```
18:59:11 Service created → Phone: 1080x2340 @540dpi → Capture: 1080x2340
19:00:38 OpenGLRenderer: Renderer stopped
19:00:38 MediaProjection stopped by system
19:00:43 Service created (restart)
19:00:51 MediaProjection stopped by system (again)
```
After notification fix: service remains stable.

---

## Android Auto Integration

### Car Service Registration
- **`MirrorCarAppService` IS registered** in the system resolver:
  ```
  com.aamirror/.car.MirrorCarAppService
  ```
- Listed in `pm query-services --components -a androidx.car.app.CarAppService`

### AA Projection State
- **AA services running:** `GearheadCarStartupService`, `WirelessSetupSharedService`, `SharedService`, `RecoveryLifecycleService`, `CarChimeraService`, `GhLifecycleService`, `ProjectionStateService`, `AppDecorService`, `GearheadService`
- **`GearheadService`** — the main projection service — only appears when fully connected to car
- **`mCarModeEnabled=false`** — even when AA is projecting, this flag is not set on this Samsung device

### USB Connection
- USB state cycles observed: `CONNECTED → CONFIGURED → DISCONNECTED` (normal AA handshake)
- USB mode: `mtp,adb` (MTP + ADB). AA typically requires AOA/accessory mode initiated by the car
- When AA wasn't projecting: USB was in MTP mode, car hadn't initiated AA handshake
- **Critical:** The CAR must initiate the Android Auto handshake. Cannot be forced from phone via `setprop sys.usb.config`

### AA Developer Settings
- On Samsung One UI: AA is handled by Google Play Services (`com.google.android.projection.gearhead`), NOT a standalone app
- No launcher activity for `com.google.android.projection.gearhead`
- AA settings accessed via: Settings → search "Android Auto"
- **Unknown sources** must be enabled in AA Developer Settings for sideloaded apps to appear
- Cannot query AA developer settings via `settings get global` (stored in GMS private data)

---

## App Visibility in Car Launcher

### ⚠️ PRIMARY ISSUE: AA Mirror does NOT appear in car app list

**Root cause analysis:**

1. **SurfaceCallback restriction:** Google restricts `AppManager.setSurfaceCallback()` usage in Android Auto for non-system, non-navigation apps. AA host v16.9 likely filters apps using this API from the launcher.

2. **`automotive_app_desc.xml` fix attempted:**
   ```xml
   <!-- Before -->
   <automotiveApp>
       <uses name="template" />
   </automotiveApp>
   
   <!-- After -->
   <automotiveApp>
       <uses name="template" />
       <uses name="navigation" />
   </automotiveApp>
   ```
   Added `navigation` category to bypass SurfaceCallback restrictions. **Result: UNVERIFIED** (needs reconnection to car after reinstall).

3. **`HostValidator.ALLOW_ALL_HOSTS_VALIDATOR`** — AA may block apps using this in production (only for DHU testing).

4. **Car App Library version:** `androidx.car.app:app:1.4.0` — `SurfaceCallback` deprecated in this version. Modern AA hosts (v16.9) may enforce the deprecation.

5. **Unknown Sources:** User confirmed enabled, but AA might not immediately refresh its app list. Force-stopping `com.google.android.projection.gearhead` resets the app cache but also kills the projection.

---

## Samsung-Specific Issues

| Issue | Detail |
|-------|--------|
| FreecessController | Aggressively freezes `com.aamirror` process when in background |
| HCPackageInfoUtils | Logs `NameNotFoundException for com.aamirror` (8 consecutive times) — Samsung Health Connect scanning |
| No standalone AA app | AA integrated into Google Play Services, not a separate launcher icon |
| `mCarModeEnabled` | Stays `false` even during active AA projection |

---

## Logcat Monitoring Commands

```bash
# Focused AA Mirror logs
adb logcat -s ScreenCaptureService:D OpenGLRenderer:D TouchInjectService:D \
  CarBridge:D MirrorCarSession:D MirrorCarAppService:D MainActivity:D \
  NotificationService:E

# AA launcher + projection logs
adb logcat -s GearheadCarStartupService:* GearheadService:* \
  LauncherCarAppService:* GhAppLauncherService:*
```

---

## Diagnostic Commands Reference

```bash
# Check AA projection
adb shell dumpsys activity services com.google.android.projection.gearhead

# Check registered AA car apps
adb shell pm query-services --components -a androidx.car.app.CarAppService

# Check accessibility state
adb shell dumpsys accessibility | grep -E "aamirror|Crashed|Enabled"

# Check notification block
adb shell appops get com.aamirror POST_NOTIFICATION

# Check USB state
adb shell dumpsys usb | grep USB_STATE

# Check car mode
adb shell dumpsys uimode | grep mCarModeEnabled

# Force stop AA (WARNING: breaks projection, needs car reconnect)
adb shell am force-stop com.google.android.projection.gearhead

# Trigger AA setup (may not work from adb)
adb shell am start -a com.google.android.gms.carsetup.START_DUPLEX
```

---

## Summary of Fixes Applied

| # | Issue | Fix | Status |
|---|-------|-----|--------|
| 1 | `gradlew.bat` had wrong JAVA_HOME | Pointed to Temurin JDK path | ✅ |
| 2 | Notifications blocked | `appops set POST_NOTIFICATION allow` | ✅ |
| 3 | Accessibility service crashed | Toggle off → kill app → re-enable | ✅ |
| 4 | Battery optimization | Added to deviceidle whitelist | ✅ |
| 5 | `automotive_app_desc.xml` missing navigation | Added `<uses name="navigation"/>` | ✅ Rebuilt |
| 6 | AA Mirror not in car launcher | Root cause: likely Google SurfaceCallback restriction | 🔴 Open |

---

## Open Questions / Next Steps

1. **Does adding `navigation` category fix car launcher visibility?** — Needs testing with car reconnected after APK reinstall
2. **Would removing `SurfaceCallback` entirely fix launcher visibility?** — The app could use templates-only as a first step, then add surface rendering after the app is launched
3. **Is `HostValidator.ALLOW_ALL_HOSTS_VALIDATOR` blocking the app?** — Should try `HostValidator.Builder().addAllowedHosts(...)` for the specific AA host
4. **Would upgrading car app library help?** — Latest `androidx.car.app:app` version might have different SurfaceCallback behavior
5. **Is a production-signed APK needed?** — Debug APK might be filtered differently than release
6. **Does the app work with DHU (Desktop Head Unit)?** — Worth testing with DHU to isolate car-specific issues
