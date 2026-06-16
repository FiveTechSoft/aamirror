# AA Mirror — Test Checklist

**Build:** `39b56ef` — 40 bugs fixed

---

## Phase 1: Install & Permissions

- [ ] `adb uninstall com.aamirror` (clean install)
- [ ] `adb install -r app/build/outputs/apk/debug/app-debug.apk`
- [ ] Notifications enabled (Settings → Apps → AA Mirror → Notifications → Allow)
- [ ] Display over other apps granted
- [ ] Accessibility Service enabled (Settings → Accessibility → AA Mirror Touch → ON)
- [ ] Phone not in battery saver mode
- [ ] In-app: all three permissions show "granted"

## Phase 2: Screen Capture (phone only)

- [ ] Open AA Mirror app → no crash
- [ ] Tap "Start Capture" → system dialog appears
- [ ] Tap "Start now" → notification appears: "Screen sharing active"
- [ ] Status text: "Screen capture active. Connect to Android Auto."
- [ ] Logcat shows correct phone dimensions (not hardcoded 1440x3088):
      `adb logcat -s ScreenCaptureService:D | grep "Phone:"`
- [ ] Tap "Stop Capture" → notification disappears, status back to idle
- [ ] Notification "Stop" button works cleanly (no "Missing MediaProjection result" error)

## Phase 3: Android Auto Connection

- [ ] Connect phone to car via USB cable
- [ ] Car head unit shows Android Auto interface
- [ ] Navigate to app list on car display
- [ ] **AA Mirror appears in car app list** ← main blocker from previous testing
- [ ] Launch AA Mirror from car display

If AA Mirror does NOT appear:
- [ ] Force-stop AA: `adb shell am force-stop com.google.android.projection.gearhead`
- [ ] Reconnect to car → check again
- [ ] Verify Unknown Sources enabled in AA Developer Settings
- [ ] Verify app registered: `adb shell pm query-services --components -a androidx.car.app.CarAppService`

## Phase 4: Screen Mirroring

- [ ] Car display shows phone screen content (not black)
- [ ] Content updates in real-time (open/close apps)
- [ ] Colors correct (no green/purple tint)
- [ ] No flickering or tearing
- [ ] FPS acceptable (~25-30fps)
- [ ] Logcat:
      `MirrorCarSession: AA Surface available: NNNxNNN`
      `ScreenCaptureService: Surface ready: NNNxNNN`
      `OpenGLRenderer: Renderer started: NNNxNNN`

## Phase 5: Touch Passthrough

- [ ] Tap on car display → tap registers on phone (check phone screen)
- [ ] Tap accuracy: top-left → hits top-left on phone
- [ ] Tap accuracy: bottom-right → hits bottom-right on phone
- [ ] Swipe up/down → scrolls on phone
- [ ] Swipe left/right → works on phone
- [ ] Long press → registers on phone
- [ ] Touch latency acceptable (< 200ms)
- [ ] Logcat (no longer spamming): `TouchInjectService: Inject: car(X,Y) → phone(X,Y) action=N`

## Phase 6: Disconnect / Reconnect

- [ ] Unplug USB → AA Mirror disappears from car, app still capturing on phone
- [ ] Replug USB → AA Mirror reappears in car app list
- [ ] Launch again → mirroring resumes, no crash
- [ ] Stop Capture while connected → clean shutdown, no crash on car side

## Phase 7: Edge Cases

- [ ] Incoming call while mirroring → AA handles call, mirror continues after
- [ ] Screen rotation while mirroring → handles correctly
- [ ] Screen timeout on phone → mirroring continues (phone stays awake)
- [ ] Switch to other AA app (Maps, Music) → mirror pauses cleanly
- [ ] Switch back to AA Mirror → mirror resumes cleanly
- [ ] Phone reboot → app works after reboot (permissions persist)

## Known Issues (Expected)

- [ ] DRM content (Netflix, Disney+) shows black screen → **expected**
- [ ] Screen recording consent dialog every launch → **expected**
- [ ] ~50-100ms touch latency → **expected**, AccessibilityService limitation
- [ ] Audio not mirrored → **expected**, phone audio uses car Bluetooth

---

## Debug Commands

```bash
# Full log stream
adb logcat -s ScreenCaptureService:D OpenGLRenderer:D TouchInjectService:D \
  CarBridge:D MirrorCarSession:D MirrorCarAppService:D MainActivity:D

# Watch for crashes
adb logcat -s AndroidRuntime:E | grep -A 10 "aamirror"

# Check services
adb shell dumpsys activity services com.aamirror

# Check accessibility
adb shell dumpsys accessibility | grep -E "aamirror|Crashed|Enabled"

# Reinstall
adb uninstall com.aamirror
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
