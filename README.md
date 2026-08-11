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

## Home-Screen Widget

A resizable widget toggles Bike Mode with one tap, styled to sit beside Nothing OS's own: pure black when off, Nothing red when on, monospaced uppercase with wide tracking, and the platform widget corner radius.

Dragging a corner or edge crosses real breakpoints rather than stretching one design — a one-row form shows the glyph, state and a rotation arrow; a square form adds the label; wider forms lay it all out in a row.

Every form shows which way the screen will turn, taken from the remembered preference, so the rider can check it matches the mount before tapping. Off reads as a promise (`↻ LOCKS RIGHT`), on reads as a fact (`↻ LANDSCAPE RIGHT`), and the arrow curves the way the screen will.

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
| System integration | `Settings.System`, `TileService`, foreground service, `BluetoothProfile` |
| Networking | None |

## Structure

```
app
├── MainActivity
├── ui/              MainScreen, BluetoothScreen, PermissionScreen, MainViewModel
├── rotation/        BikeModeManager, RotationController, ServiceRotationWatchdog
│                    (RotationWatchdogService + JobRotationWatchdog)
├── bluetooth/       BluetoothController, BluetoothHelmetLink, HelmetMonitor
├── media/           MediaPauseController
├── display/         DisplayController
├── companion/       HelmetPresenceService, HelmetAssociation
├── quicksettings/   BikeModeTileService
├── widget/          BikeModeWidgetProvider
├── data/            PreferencesRepository
├── system/          BootReceiver
└── util/            PermissionManager, QuickSettingsTilePrompt
```

`BikeModeManager` holds the enable/disable/restore rules and takes its collaborators as interfaces, so the app, the tile, the widget and the watchdog all toggle through one tested path.

### Staying alive

A portrait-locked app in the foreground makes Android rewrite `USER_ROTATION`, so something has to be watching the whole time Bike Mode is on. `ServiceRotationWatchdog` runs two layers for that:

- **`RotationWatchdogService`** — a foreground service started only while Bike Mode is on. It holds a `ContentObserver` on the two rotation settings and nothing else: no wake lock, no sensors, no network, no polling. The system pushes it a callback when a setting changes, so its idle cost is process residency alone. It also owns the ongoing notification, which doubles as the off switch.
- **`JobRotationWatchdog`** — the recovery path. A content-trigger job outlives the process, so if the system kills the service it fires, repairs the rotation and restarts the service.
- **`BootReceiver`** — content-trigger jobs cannot be persisted, so neither layer survives a reboot on its own. The rotation settings do survive one, so `BOOT_COMPLETED` re-applies the lock and re-arms both.

A **force stop** (from app settings or an OEM task killer) puts the app in Android's stopped state, which cancels jobs and blocks broadcasts by design; nothing can recover from it. Tapping the widget, the tile or the app brings Bike Mode back.

### Bluetooth and the helmet

Both features here are shaped by what Android withholds from ordinary apps, so it is worth being explicit about the limits.

**Turning Bluetooth on** (opt-out, toggle in the app). `BluetoothAdapter.enable()` is a no-op for apps targeting API 33+, so Bike Mode uses `ACTION_REQUEST_ENABLE`, a system dialog the rider confirms. It is hosted by an invisible `BluetoothRequestActivity` because the tile and widget have no activity of their own. There is no matching public *request disable*, so this is **one-way**: Bike Mode raises Bluetooth and never lowers it again. The dialog only appears when Bluetooth is actually off.

**Watching for the helmet** (optional; pick a paired device or type a MAC address). `BluetoothA2dp` and `BluetoothHeadset` expose `getConnectedDevices`, `getConnectionState` and `isAudioConnected` — but their `connect()` methods sit behind `BLUETOOTH_PRIVILEGED`, a signature permission. **An ordinary app cannot command an audio connection.** So `HelmetMonitor` does what it can:

1. wait 15s for Android's own auto-connect, which usually succeeds
2. if not, `nudge()` — open and immediately close an RFCOMM socket, which often prompts an intercom to bring its own audio profiles up. Best effort; some headsets ignore it entirely
3. wait 10s more, then report `Helmet not connected` in the notification with a one-tap shortcut to Bluetooth settings

The watch is bounded — polling stops once it settles, and an `ACTION_ACL_CONNECTED` receiver (free while idle) corrects the notification if the helmet turns up later.

### Pausing media on dismount

Ending a ride pauses whatever is playing, so a podcast does not carry on in a helmet that is coming off. Opt-out, like the Bluetooth prompt.

Sending an `ACTION_MEDIA_BUTTON` broadcast — still the top answer in most search results — has done nothing for ordinary apps since Android 5.0: `MediaSession` honours only media-button events the system itself dispatched. The supported equivalent is `AudioManager.dispatchMediaKeyEvent`, which needs no permission. It is sent as an ACTION_DOWN/ACTION_UP pair, because some players read a lone DOWN as a key being held, and skipped entirely when `isMusicActive()` is false.

It cannot be aimed. Android routes the key to whichever app holds the active media session, with no way to scope it to one audio output, so this pauses everything rather than just the helmet's audio — which is what the preference is there for.

The hook sits in `BikeModeManager.disable()`, the one path the app button, the tile, the widget and the notification's Turn off action all already converge on.

### Screen timeout and brightness

Two settings a mounted phone wants changed, both opt-out/opt-in switches of their own. A landscape lock is no use if the screen sleeps at a red light, and an automatic brightness level is unreadable in direct sun.

`SCREEN_OFF_TIMEOUT`, `SCREEN_BRIGHTNESS` and `SCREEN_BRIGHTNESS_MODE` all live in `Settings.System`, so **WRITE_SETTINGS — already held for rotation — covers them.** No new permission and no new prompt.

They follow the same save-and-restore contract as rotation, with one refinement: `SavedDisplayState` holds each field as nullable, and null means *Bike Mode never touched this*. So a rider who dims the screen by hand mid-ride keeps that, and only the settings Bike Mode actually changed are handed back. Timeout goes to 30 minutes rather than never, so a Bike Mode left on in a pocket still eventually sleeps. Display writes are best effort — a device that refuses one still gets its landscape lock, because rotation is the feature and this is comfort on top.

Brightness is gated on the **light sensor**, not on the switch alone. Full brightness is right in direct sun and actively hazardous after dark, so `DaylightGate` decides from a `TYPE_LIGHT` reading, and `RotationWatchdogService` keeps following it for the whole ride — a commute that sets off in sun and finishes at night gives the brightness back on the way. The two thresholds are deliberately far apart (boost above 5000 lux, release below 2000) so that riding under a bridge or a line of trees cannot flicker the screen. A phone with no light sensor has nothing to ask, so there the switch stands on its own.

### Starting and stopping with the helmet

Opt-in. `CompanionDeviceManager` association plus `CompanionDeviceService.onDeviceAppeared` / `onDeviceDisappeared`, so putting the helmet on starts the ride and taking it off ends it — restoring rotation and display, and pausing media, with no tap.

This is the only mechanism that can do it. A manifest-registered `ACL_CONNECTED` receiver would not fire while the app is not running, since that broadcast is not on the implicit-broadcast exemption list; the system binds a `CompanionDeviceService` regardless. It is also what makes starting the watchdog's foreground service legal from the background, via `REQUEST_COMPANION_START_FOREGROUND_SERVICES_FROM_BACKGROUND`.

Association is a one-time system dialog, filtered by `BluetoothDeviceFilter.setAddress()` to the helmet already chosen in the app, so the rider does not pick twice. Changing or clearing the helmet disassociates the old one, so nothing stale can keep waking the app.

## Roadmap

**MVP (P0)** — permission flow, enable/disable Bike Mode, landscape 90°/270°, previous-state restore, Quick Settings tile with active/inactive states, DataStore-persisted landscape preference, offline operation, Nothing Phone (2a) road testing.

**P1** — haptic feedback, dynamic shortcut, Material You theming, AMOLED dark mode. *(Home-screen widget, persistent notification and reboot restore shipped.)*

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
