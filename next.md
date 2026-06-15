# AA Mirror — Next Steps: Car Testing

**Date:** 2026-06-15
**Build:** `39b56ef` — 40 bugs fixed across 8 source files

---

## 1. Install the new build

```bash
adb uninstall com.aamirror
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 2. Pre-flight permissions

```bash
# Overlay
adb shell appops set com.aamirror SYSTEM_ALERT_WINDOW allow

# Notifications (critical — without this Android kills the service)
adb shell appops set com.aamirror POST_NOTIFICATION allow

# Accessibility
adb shell settings put secure enabled_accessibility_services com.aamirror/com.aamirror.TouchInjectService
adb shell settings put secure accessibility_enabled 1

# Battery whitelist
adb shell dumpsys deviceidle whitelist +com.aamirror
```

Verify in-app: all three permissions should show "granted".

## 3. Verify screen capture works (phone only)

1. Open AA Mirror → tap "Start Capture"
2. Confirm: system dialog → "Start now" → notification appears
3. Check logcat for correct phone dimensions (no longer hardcoded to 1440x3088):
   ```
   adb logcat -s ScreenCaptureService:D | grep "Phone:"
   ```
4. Confirm `isRunning` is true, status text says "Screen capture active"
5. Tap "Stop Capture" → notification disappears cleanly

## 4. Connect to car (USB)

1. Connect phone via USB to car head unit
2. Wait for AA to project on car display
3. Open AA Mirror app list on car display
4. **Key question: does AA Mirror appear in the car launcher?**
   - If YES → launch it and proceed to step 5
   - If NO → see troubleshooting section below

## 5. Test screen mirroring

1. Launch AA Mirror on car display
2. Verify phone screen content appears on car display (not black/blank)
3. Open different apps on phone → confirm they render on car
4. Check logcat:
   ```
   adb logcat -s MirrorCarSession:D OpenGLRenderer:D ScreenCaptureService:D
   ```
   Expected: `AA Surface available: NNNxNNN` → `Surface ready: NNNxNNN` → `Renderer started: NNNxNNN`

## 6. Test touch passthrough

1. Tap on car display → verify tap registers on phone screen
2. Test tap accuracy: top-left, center, bottom-right
3. Swipe up/down on car → scroll on phone
4. Swipe left/right on car → works on phone
5. Check logcat:
   ```
   adb logcat -s TouchInjectService:D
   ```
   Expected: `Inject: car(X,Y) → phone(X,Y) action=N` (no longer spamming on every move)

## 7. Test disconnect/reconnect

1. Unplug USB → car display shows AA Mirror disappears, phone keeps capturing
2. Replug USB → AA Mirror reappears in car app list
3. Launch again → mirroring resumes, no crash
4. Verify no context leak warnings in logcat (WeakReference fix)

## 8. Test notification stop button

1. While capturing, tap "Stop" in the notification
2. Verify: service stops cleanly, no misleading "Missing MediaProjection result" error in logcat
3. Verify: notification disappears, UI returns to idle

## 9. Test edge cases

- [ ] Incoming call while mirroring
- [ ] Screen rotation while mirroring
- [ ] Switch to other AA app (Maps, Music) → switch back
- [ ] Phone reboot → app works after reboot

---

## Troubleshooting

### AA Mirror not in car launcher

The main open issue from previous testing. Steps to try:

1. **Force AA to refresh app list:**
   ```bash
   adb shell am force-stop com.google.android.projection.gearhead
   ```
   Then reconnect to car.

2. **Verify app is registered:**
   ```bash
   adb shell pm query-services --components -a androidx.car.app.CarAppService
   # Should show: com.aamirror/.car.MirrorCarAppService
   ```

3. **Check AA developer settings:** Unknown sources must be enabled.

4. **If still not appearing:** The SurfaceCallback restriction may be blocking it. The app currently has SurfaceCallback disabled (commented out) — this should help visibility. If it still doesn't appear, the `navigation` category or `HostValidator` may need adjustment.

### Touch not working

1. Verify accessibility service is enabled and not in "crashed services":
   ```bash
   adb shell dumpsys accessibility | grep -E "aamirror|Crashed|Enabled"
   ```
2. If crashed: toggle off → `adb shell am force-stop com.aamirror` → re-enable

### Screen black / not rendering

1. Check logcat for OpenGL errors:
   ```
   adb logcat -s OpenGLRenderer:E
   ```
2. Verify `mediaProjection` is not null (Bug 9 fix should log errors now)
3. Check if `virtualDisplay` was created successfully

### Touch coordinates wrong

1. Check DPI is coming from SurfaceContainer (not hardcoded 160):
   ```
   adb logcat -s CarBridge:D | grep "Surface received"
   ```
   Should show `@NNNdpi` with a real DPI value.

2. Verify phone dimensions are correct (not 1440x3088):
   ```
   adb logcat -s ScreenCaptureService:D | grep "Phone:"
   ```

---

## Debug commands

```bash
# Full AA Mirror log stream
adb logcat -s ScreenCaptureService:D OpenGLRenderer:D TouchInjectService:D \
  CarBridge:D MirrorCarSession:D MirrorCarAppService:D MainActivity:D

# Watch for crashes
adb logcat -s AndroidRuntime:E | grep -A 10 "aamirror"

# Check running services
adb shell dumpsys activity services com.aamirror

# Check accessibility state
adb shell dumpsys accessibility | grep -E "aamirror|Crashed|Enabled"
```
