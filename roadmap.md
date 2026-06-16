# AA Mirror — Roadmap

## Current Status

- **Build:** `39b56ef` (master) — 40 bugs fixed, BUILD SUCCESSFUL
- **Blocker:** AA Mirror does not appear in car app list (SurfaceCallback restriction)
- **Tested:** Screen capture on phone works, permissions work, notification works
- **Untested:** Car display mirroring, touch passthrough, disconnect/reconnect

---

## Phase 1: Car Testing (current)

**Goal:** Verify the app works end-to-end in the car.

| # | Task | Status |
|---|------|--------|
| 1 | Install new APK with all bug fixes | TODO |
| 2 | Verify screen capture (phone only) | TODO |
| 3 | Connect to car via USB | TODO |
| 4 | AA Mirror appears in car launcher | TODO |
| 5 | Launch AA Mirror on car display | TODO |
| 6 | Screen mirrors to car (OpenGL rendering) | TODO |
| 7 | Touch passthrough (tap, swipe) | TODO |
| 8 | Disconnect/reconnect cycle | TODO |
| 9 | Notification stop button | TODO |
| 10 | Edge cases (calls, rotation, app switching) | TODO |

See `next.md` for detailed testing steps.

---

## Phase 2: Fix Car Launcher Visibility (if still blocked)

**Goal:** Make AA Mirror appear in the Android Auto app list on the car display.

This is the main open issue from previous testing (findings.md).

| # | Approach | Risk | Status |
|---|----------|------|--------|
| 1 | SurfaceCallback is disabled (commented out) — should help | Low | Applied |
| 2 | `navigation` category added to `automotive_app_desc.xml` | Low | Applied |
| 3 | Force-stop AA to refresh app list after reinstall | Low | TODO |
| 4 | Test with DHU (Desktop Head Unit) to isolate car issues | Low | TODO |
| 5 | Try specific `HostValidator` instead of ALLOW_ALL_HOSTS | Medium | TODO |
| 6 | Upgrade `androidx.car.app:app` to latest version | Medium | TODO |
| 7 | Test with release-signed APK (not debug) | Medium | TODO |
| 8 | Investigate Google's app filtering logic in GearheadService | High | TODO |

**Decision point:** If AA Mirror still doesn't appear after steps 3-5, the issue is likely Google's restriction on non-system apps using `SurfaceCallback`. Options:
- A) Accept template-only mode (no surface rendering, just text/status)
- B) Investigate if there's a different API path for screen mirroring apps
- C) Try the DHU for development and defer car launcher issue

---

## Phase 3: Polish & Reliability

**Goal:** Make the app stable enough for daily use.

| # | Task | Priority |
|---|------|----------|
| 1 | Fix 2 deprecation warnings (`getParcelableExtra`, `addAction`) | Low |
| 2 | Enable `isMinifyEnabled = true` with ProGuard rules | Low |
| 3 | Add `SurfaceCallback` back once launcher visibility is resolved | High |
| 4 | Test with multiple car head units (different DPIs, resolutions) | Medium |
| 5 | Test wireless Android Auto | Medium |
| 6 | Handle `ForegroundServiceStartNotAllowedException` on Android 12+ | Medium |
| 7 | Optimize frame queue for lower latency | Medium |
| 8 | Add landscape/portrait auto-detection | Low |

---

## Phase 4: Features

| # | Feature | Complexity |
|---|---------|------------|
| 1 | Audio mirroring (requires MediaProjection audio) | High |
| 2 | Keyboard input from car display | Medium |
| 3 | Multi-touch support | Medium |
| 4 | Configurable quality/resolution settings | Low |
| 5 | Wi-Fi Direct connection (no USB needed) | High |
| 6 | App filtering (select which apps to mirror) | Medium |
| 7 | Night mode / dark theme on car display | Low |

---

## Phase 5: Distribution

| # | Task | Notes |
|---|------|-------|
| 1 | Release-signed APK | Required for Play Store or sideloading |
| 2 | Play Store listing (if allowed — AA apps have restrictions) | Research required |
| 3 | F-Droid / direct APK distribution | Alternative |
| 4 | User documentation | README update |
| 5 | Bug reporting mechanism | Crashlytics or similar |

---

## Key Files

| File | Purpose |
|------|---------|
| `next.md` | Detailed car testing steps |
| `TODO.md` | Test checklist (updated) |
| `findings.md` | Previous testing findings |
| `README.md` | Project overview |
