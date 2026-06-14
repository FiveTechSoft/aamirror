# AA Mirror — Car Test Checklist

## Pre-flight

- [ ] Notifications enabled for AA Mirror (Settings → Apps → AA Mirror → Notifications → Allow)
- [ ] Display over other apps granted (Settings → Apps → AA Mirror → Display over other apps)
- [ ] Accessibility Service enabled (Settings → Accessibility → AA Mirror Touch → ON)
- [ ] Android Auto Developer Mode enabled (AA app → Settings → tap Version 10× → Developer settings → Unknown sources)
- [ ] Phone not in battery saver mode

## Screen Capture

- [ ] Open AA Mirror app → no crash
- [ ] Tap "Start Capture" → system dialog appears "AA Mirror will start capturing..."
- [ ] Tap "Start now" → notification appears: "Screen sharing active"
- [ ] Status text changes to "Screen capture active. Connect to Android Auto."
- [ ] Tap "Stop Capture" → notification disappears, status back to idle
- [ ] Logcat: `ScreenCaptureService: Phone: NNNxNNN @NNNdpi → Capture: NNNxNNN` (no errors)

## Android Auto Connection (USB)

- [ ] Connect phone to car via USB cable
- [ ] Car head unit shows Android Auto interface
- [ ] Navigate to app list on car display
- [ ] **AA Mirror appears in car app list**
- [ ] Launch AA Mirror from car display
- [ ] Car display shows phone screen content
- [ ] Logcat: `MirrorCarSession: AA Surface available: NNNxNNN`
- [ ] Logcat: `ScreenCaptureService: Surface ready: NNNxNNN`
- [ ] Logcat: `OpenGLRenderer: Renderer started: NNNxNNN`

## Screen Mirroring Quality

- [ ] Phone screen content visible on car display (not black)
- [ ] Content updates in real-time (open/close apps, scroll, type)
- [ ] Colors are correct (no green/purple tint)
- [ ] No flickering or tearing
- [ ] FPS acceptable (~25-30fps visual)
- [ ] Works in both portrait and landscape
- [ ] Home screen mirrors correctly
- [ ] Third-party apps mirror correctly (maps, messages, etc.)
- [ ] Video playback mirrors (YouTube, etc.) — note: DRM content will be black

## Touch Passthrough

- [ ] Tap on car display → tap registers on phone (check phone screen)
- [ ] Tap accuracy: tap top-left → hits top-left area on phone
- [ ] Tap accuracy: tap bottom-right → hits bottom-right area on phone
- [ ] Swipe up/down on car display → scrolls on phone
- [ ] Swipe left/right on car display → works on phone
- [ ] Long press on car display → registers on phone
- [ ] Touch latency acceptable (< 200ms feel)
- [ ] Logcat: `TouchInjectService: Inject: car(N,N) → phone(N,N) action=N`

## Disconnect / Reconnect

- [ ] Unplug USB → AA Mirror disappears from car, app still capturing on phone
- [ ] Replug USB → AA Mirror reappears in car app list
- [ ] Launch AA Mirror again → mirroring resumes, no crash
- [ ] Stop Capture while connected → mirroring stops cleanly, no crash on car side

## Edge Cases

- [ ] Incoming call while mirroring → AA handles call, mirror continues after
- [ ] Screen rotation while mirroring → handles correctly
- [ ] Screen timeout on phone → mirroring continues (phone stays awake)
- [ ] Switch to other AA app (Maps, Music) → mirror pauses cleanly
- [ ] Switch back to AA Mirror → mirror resumes cleanly
- [ ] Phone reboot → app works after reboot (permissions persist)
- [ ] App update (reinstall APK) → permissions persist, works

## Known Issues (Expected)

- [ ] DRM content (Netflix, Disney+) shows black screen on car → **expected**
- [ ] Screen recording consent dialog every app launch → **expected**, Android security
- [ ] ~50-100ms touch latency → **expected**, AccessibilityService limitation
- [ ] Audio not mirrored → **expected**, phone audio uses car Bluetooth

## Debug Commands

```bash
# Watch app logs
adb logcat -s ScreenCaptureService:D OpenGLRenderer:D TouchInjectService:D CarBridge:D MirrorCarSession:D MirrorCarAppService:D

# Check services
adb shell dumpsys activity services com.aamirror

# Check permissions
adb shell appops get com.aamirror SYSTEM_ALERT_WINDOW
adb shell settings get secure enabled_accessibility_services

# Force stop
adb shell am force-stop com.aamirror

# Reinstall
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
