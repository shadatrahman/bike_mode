# Bike Mode — Bike Rotation Lock

**One tap to make a horizontally mounted Android phone stay horizontal.**

Bike Mode is a tiny Android utility for motorcycle riders who mount their phone in landscape for navigation. Road and engine vibration constantly retrigger Android's orientation sensor, so the screen flips between portrait and landscape while riding. Simply turning off Auto-rotate doesn't help — Android falls back to portrait.

Bike Mode disables sensor-based rotation *and* pins the system to a chosen landscape direction, in one tap from Quick Settings.

---

## Mission

Eliminate unwanted screen rotation on a vibrating motorcycle mount, without asking the rider for anything more than a single tap — no root, no ADB, no computer, no account, no internet.

## Vision

A utility that feels like a native Android setting rather than an app. After first-run setup, the rider's only interface is:

**Quick Settings → Bike Mode.**

The app stays small and focused on one problem. It will not grow into a phone-customization suite.

## Target

**Primary users** — motorcycle riders who mount an Android phone horizontally and run Google Maps, Waze, or HERE WeGo.

**Also useful for** — bicycle and delivery riders, car dashboard mounts, truck drivers, boat users, kiosk-style horizontal installs, and anyone with a flaky orientation sensor.

**Target device** — Nothing Phone (2a) on current Nothing OS. No Nothing-specific private APIs.

**Android support** — `minSdk 33` (Android 13), `targetSdk 36`. Android 13 is the floor because the app uses `StatusBarManager.requestAddTileService()` to offer the Quick Settings tile with a single prompt.

---

## How It Works

Bike Mode writes two values via `Settings.System`, which requires the `WRITE_SETTINGS` permission the user grants once from Android's *Modify system settings* screen.

```
ON   save current rotation state
     ACCELEROMETER_ROTATION = 0
     USER_ROTATION          = ROTATION_90 (Landscape Left) or ROTATION_270 (Landscape Right)

OFF  restore the previously saved rotation state
```

Turning Bike Mode off restores whatever the user had before — it does not blindly re-enable Auto-rotate. A rider who kept portrait locked before the ride gets portrait locked back after it.

The app changes settings and exits. No foreground service, no polling, no accelerometer reads, no wake locks — effectively zero ongoing battery cost.

### Keeping the lock

Android rewrites `USER_ROTATION` back to 0 when a portrait-locked app takes the foreground — pressing home is enough. Left alone, Bike Mode would still report itself as on while the screen no longer held landscape.

While Bike Mode is active, a JobScheduler content trigger watches the two rotation settings. The system wakes the app only when one of them actually changes; the app re-pins the rider's direction if it drifted, re-arms the trigger, and exits. Nothing polls and nothing stays resident. The trigger is cancelled the moment Bike Mode goes off. Returning to the app or opening Quick Settings repairs drift the same way.

A rotation that is already correct is never rewritten, so the watchdog cannot fight an app that owns the screen.

## Ride Flow

```
Swipe down → Tap Bike Mode → Mount phone → Ride
Swipe down → Tap Bike Mode → Normal rotation restored
```

## Privacy

Collects nothing. No analytics, no ads, no account, no location, no network access. Works fully offline.

## Permission

One special permission only:

```xml
<uses-permission android:name="android.permission.WRITE_SETTINGS" />
```

Used solely to toggle Android's auto-rotate and set a fixed screen orientation. The app never tries to bypass the system permission screen.

## Known Limitation

An Android activity can request its own orientation. Apps that hard-code their orientation will ignore the system rotation lock. Bike Mode does not fight them — this is expected platform behavior.

---

## Tech Stack

| Area | Choice |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| State | MVVM / lightweight unidirectional state |
| Storage | Jetpack DataStore Preferences |
| System integration | `Settings.System`, `TileService` |
| Networking | None |

## Planned Structure

```
app
├── MainActivity
├── ui/              MainScreen, PermissionScreen
├── rotation/        RotationController
├── quicksettings/   BikeModeTileService
├── data/            PreferencesRepository
└── util/            PermissionManager
```

## Roadmap

**MVP (P0)** — permission flow, enable/disable Bike Mode, landscape 90°/270°, previous-state restore, Quick Settings tile with active/inactive states, DataStore-persisted landscape preference, offline operation, Nothing Phone (2a) road testing.

**P1** — home-screen widget, persistent notification while active, restore after reboot, haptic feedback, dynamic shortcut, Material You theming, AMOLED dark mode.

**P2 (exploratory)** — auto-enable on motorcycle Bluetooth connect; launch a preferred navigation app on activation.

## Success Metric

Not downloads, not engagement:

> **Zero unwanted orientation changes during a normal motorcycle ride.**

---

## Build

```bash
./gradlew assembleDebug
./gradlew installDebug
```

Full specification: [`docs/Product Requirements Document — Bike Rotation Lock.md`](docs/Product%20Requirements%20Document%20—%20Bike%20Rotation%20Lock.md)
