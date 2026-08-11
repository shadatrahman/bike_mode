# Product Requirements Document — Bike Rotation Lock

**Product Name:** Bike Rotation Lock  
**Platform:** Android  
**Primary Language:** Kotlin  
**Initial Target Device:** Nothing Phone (2a)  
**Document Version:** 1.0  
**Status:** MVP Specification

---

## 1. Product Summary

Bike Rotation Lock is a lightweight Android utility designed primarily for motorcycle riders who mount their phones horizontally for navigation.

Motorcycle vibration can cause Android's orientation sensors to repeatedly detect changes in device orientation. This may result in the interface rapidly switching between portrait and landscape while riding.

The application will allow the user to manually force Android into a specific landscape orientation while disabling accelerometer-controlled rotation.

The primary interaction should require only **one tap from Android Quick Settings**.

The application should not require:

- Root access
- ADB during normal use
- A computer after installation
- Accessibility Service
- Internet access
- User account
- Background location
- Continuous sensor monitoring

---

# 2. Problem Statement

When a smartphone is attached horizontally to a motorcycle mount, road and engine vibration can interfere with orientation detection.

On devices such as the Nothing Phone (2a), disabling Auto-rotate normally results in Android returning to portrait orientation.

Therefore, simply disabling Auto-rotate does not solve the problem.

The desired behavior is:

> Disable automatic sensor-based rotation while simultaneously forcing Android to remain in a selected landscape orientation.

Android exposes system rotation settings that an application with permission to modify system settings can control through `Settings.System`.

---

# 3. Product Goal

Provide a reliable **Bike Mode** that locks the Android UI into landscape orientation regardless of motorcycle vibration.

The ideal user journey is:

**Swipe down → Tap Bike Mode → Mount phone → Ride**

When the ride ends:

**Swipe down → Tap Bike Mode again → Normal rotation restored**

---

# 4. Goals

## Primary Goals

The MVP must:

1. Force Android into landscape orientation.
2. Disable accelerometer-controlled screen rotation.
3. Prevent motorcycle vibration from triggering orientation changes.
4. Restore normal Auto-rotate with one tap.
5. Provide a Quick Settings tile.
6. Support both landscape directions.
7. Remember the user's preferred landscape direction.
8. Require no computer after initial APK installation.
9. Operate completely offline.
10. Have negligible battery usage.

Android officially allows applications to expose custom Quick Settings controls through `TileService`.

---

# 5. Non-Goals

The MVP will NOT:

- Control physical motorcycle hardware.
- Detect motorcycle speed.
- use GPS.
- automatically detect whether the phone is mounted.
- continuously monitor accelerometer data.
- require Accessibility permissions.
- require root.
- modify individual third-party applications.
- override an application's own hard-coded orientation behavior.
- provide navigation functionality.
- collect analytics.
- require cloud infrastructure.

Some Android applications may explicitly request their own orientation. Android activities can specify or request their own orientation, so Bike Rotation Lock cannot guarantee that every third-party application will obey the system-selected rotation.

---

# 6. Target Users

## Primary User

Motorcycle riders who:

- mount their Android phone horizontally;
- use Google Maps, Waze, HERE WeGo, or similar navigation applications;
- experience unwanted UI rotation caused by vibration;
- want a simple one-tap solution.

## Secondary Users

The application may also be useful for:

- bicycle riders;
- car dashboard mounts;
- delivery riders;
- truck drivers;
- boat users;
- users with unstable device orientation sensors;
- kiosk-style horizontal phone installations.

---

# 7. Core User Stories

### US-01 — Enable Bike Mode

As a motorcycle rider, I want to tap one button so that my phone stays in landscape while riding.

### US-02 — Disable Bike Mode

As a user, I want to restore normal Android rotation after finishing my ride.

### US-03 — Choose Landscape Direction

As a user, I want to choose between:

- Landscape Left
- Landscape Right

so the screen matches my motorcycle mount orientation.

### US-04 — Quick Settings Access

As a rider, I want Bike Mode available inside Android Quick Settings so I do not need to open the application before every ride.

### US-05 — Remember Preference

As a user, I want the app to remember my preferred landscape direction.

### US-06 — Clear Status

As a user, I want to immediately know whether Bike Mode is active.

---

# 8. Main Application UI

The application should use a minimal single-screen interface.

Example:

```text
┌─────────────────────────────────┐
│                                 │
│       Bike Rotation Lock        │
│                                 │
│       BIKE MODE                  │
│                                 │
│          ┌─────────┐            │
│          │   OFF   │            │
│          └─────────┘            │
│                                 │
│  Rotation Direction             │
│                                 │
│  ○ Landscape Left               │
│  ● Landscape Right              │
│                                 │
│  ─────────────────────────────  │
│                                 │
│  Quick Settings                 │
│                                 │
│  Add Bike Mode to Quick         │
│  Settings for one-tap access.   │
│                                 │
│  [ Add Quick Settings Tile ]    │
│                                 │
└─────────────────────────────────┘
```

When active:

```text
┌─────────────────────────────────┐
│                                 │
│       Bike Rotation Lock        │
│                                 │
│          BIKE MODE              │
│                                 │
│        🔒 LANDSCAPE             │
│                                 │
│             ON                  │
│                                 │
│  Landscape Right               │
│                                 │
│  Auto rotation disabled        │
│                                 │
│        [ Turn Off ]             │
│                                 │
└─────────────────────────────────┘
```

---

# 9. First Launch Flow

On first launch:

### Step 1

Display a short explanation:

> Bike Rotation Lock needs permission to modify Android's rotation settings. No personal information is accessed or collected.

### Step 2

Display:

**Grant Permission**

### Step 3

Open Android's **Modify system settings** permission screen.

### Step 4

The user enables permission.

### Step 5

Return to the application.

### Step 6

Application verifies permission.

### Step 7

Enable Bike Mode controls.

If permission has not been granted, Bike Mode controls remain disabled.

---

# 10. Required Android Permission

Manifest:

```xml
<uses-permission android:name="android.permission.WRITE_SETTINGS" />
```

Runtime verification:

```kotlin
Settings.System.canWrite(context)
```

Permission screen:

```kotlin
Intent(
    Settings.ACTION_MANAGE_WRITE_SETTINGS,
    Uri.parse("package:$packageName")
)
```

The application must never attempt to silently bypass this Android permission screen.

---

# 11. Rotation Logic

## Bike Mode ON

When Bike Mode is enabled:

```text
ACCELEROMETER_ROTATION = 0
USER_ROTATION = preferred landscape direction
```

### Landscape Direction A

```kotlin
Surface.ROTATION_90
```

### Landscape Direction B

```kotlin
Surface.ROTATION_270
```

The UI should call these:

- Landscape Left
- Landscape Right

rather than exposing numeric rotation values.

---

# 12. Bike Mode OFF

When Bike Mode is disabled:

```text
ACCELEROMETER_ROTATION = 1
```

Normal Android sensor-based Auto-rotate should resume.

---

# 13. Critical Restore Behavior

Before changing rotation settings, the application should record the existing rotation state.

For example:

```text
previousAccelerometerRotation
previousUserRotation
```

When Bike Mode is disabled, the application should preferably restore the user's previous state instead of blindly enabling Auto-rotate.

Example:

User originally had:

```text
Auto Rotate = OFF
Portrait = locked
```

Bike Mode should not permanently change that preference after the ride.

Therefore:

```text
Enable Bike Mode
        ↓
Save existing Android rotation state
        ↓
Apply Bike Mode settings
        ↓
Ride
        ↓
Disable Bike Mode
        ↓
Restore original Android rotation state
```

This should be the preferred behavior.

---

# 14. Quick Settings Tile

The application must provide an Android Quick Settings Tile using:

```kotlin
TileService
```

Android's `TileService` API is specifically designed to allow applications to expose actions inside the Quick Settings panel.

Tile name:

**Bike Mode**

Suggested icon:

```text
Motorcycle + rotation lock
```

or a simpler:

```text
Landscape screen + lock
```

---

# 15. Quick Settings Tile States

## Inactive

Label:

**Bike Mode**

State:

```text
OFF
```

Icon:

Unlocked / normal orientation

---

## Active

Label:

**Bike Mode**

Secondary state if supported:

```text
Landscape Locked
```

Icon:

Locked landscape

Tile should visually use Android's active tile state.

---

# 16. Quick Settings Interaction

### Tap while OFF

```text
Bike Mode OFF
     ↓
Tap tile
     ↓
Read preferred landscape
     ↓
Save current rotation settings
     ↓
Disable accelerometer rotation
     ↓
Apply landscape rotation
     ↓
Tile becomes ACTIVE
```

### Tap while ON

```text
Bike Mode ON
     ↓
Tap tile
     ↓
Restore previous rotation settings
     ↓
Tile becomes INACTIVE
```

No application UI should need to open.

---

# 17. Landscape Preference

User can select:

```text
Landscape Left
```

or:

```text
Landscape Right
```

Preference should be stored locally using:

```text
DataStore Preferences
```

Suggested key:

```text
preferred_rotation
```

Values:

```text
ROTATION_90
ROTATION_270
```

---

# 18. Persisted Data

No database is required.

Use Android DataStore for:

```text
preferredLandscapeDirection
previousAccelerometerRotation
previousUserRotation
bikeModeActive
firstLaunchCompleted
```

No user-identifiable information should be stored.

---

# 19. Architecture

Recommended architecture:

```text
app
│
├── MainActivity
│
├── ui
│   ├── MainScreen
│   └── PermissionScreen
│
├── rotation
│   └── RotationController
│
├── quicksettings
│   └── BikeModeTileService
│
├── data
│   └── PreferencesRepository
│
└── util
    └── PermissionManager
```

---

# 20. Technology Stack

### Language

Kotlin

### UI

Jetpack Compose

### Minimum Architecture

MVVM or lightweight unidirectional state management.

Because the application is very small, excessive architecture should be avoided.

### Storage

Jetpack DataStore Preferences

### System Integration

```text
Settings.System
TileService
WRITE_SETTINGS
```

### Networking

None.

---

# 21. Suggested Android Support

Initial development should prioritize:

```text
Android 13+
```

Primary testing should be performed on:

```text
Nothing Phone (2a)
Current Nothing OS version installed on test device
```

The architecture should avoid Nothing-specific private APIs unless absolutely necessary.

---

# 22. Battery Requirements

Bike Rotation Lock should have effectively zero ongoing battery impact.

The application should NOT:

- keep a foreground service running;
- continuously poll rotation;
- continuously read accelerometer data;
- hold wake locks;
- use GPS;
- maintain network connections.

Bike Mode simply changes Android settings and exits.

---

# 23. Privacy Requirements

The app must collect:

**Nothing.**

No:

- analytics;
- advertising;
- account;
- crash tracking in the initial private build;
- location;
- contacts;
- storage;
- microphone;
- camera;
- Bluetooth;
- network communication.

The app should work completely offline.

---

# 24. Permission Requirements

Only one special permission should be required:

```text
Modify system settings
```

The application should clearly explain why this permission is required.

Example:

> Bike Rotation Lock needs this permission only to enable or disable Android's automatic rotation and select a fixed screen orientation.

---

# 25. Error Handling

## Permission Missing

If:

```kotlin
Settings.System.canWrite(context) == false
```

Show:

> Permission required to control screen rotation.

Button:

**Grant Permission**

---

## System Rejects Setting

If changing the setting fails:

Show:

> Your device prevented Bike Rotation Lock from changing the screen orientation.

Provide:

**Try Again**

---

## App Forces Its Own Orientation

If a third-party application does not follow system rotation:

Bike Rotation Lock should not continuously fight that application.

Optional informational message:

> Some apps enforce their own screen orientation and may ignore the system rotation lock.

Android activities are able to declare/request their own orientation, so this limitation should be treated as expected platform behavior rather than an application bug.

---

# 26. Motorcycle Safety UX

The application should minimize interaction while riding.

The intended behavior is:

> Configure before moving.

Do not design features requiring repeated taps while riding.

The Quick Settings tile exists specifically to minimize interaction.

No animated UI, complicated menus, or configuration flow should appear while Bike Mode is active.

---

# 27. MVP Feature List

## P0 — Required

- [ ] Kotlin Android project
- [ ] Jetpack Compose UI
- [ ] WRITE_SETTINGS permission flow
- [ ] Detect WRITE_SETTINGS permission
- [ ] Enable Bike Mode
- [ ] Disable Bike Mode
- [ ] Landscape 90°
- [ ] Landscape 270°
- [ ] Disable sensor Auto-rotate
- [ ] Restore previous rotation settings
- [ ] Quick Settings tile
- [ ] Tile active/inactive states
- [ ] DataStore preferences
- [ ] Remember preferred landscape
- [ ] Nothing Phone (2a) testing
- [ ] Offline operation

---

# 28. P1 Features

After MVP validation:

- [ ] Home-screen widget
- [ ] Persistent notification while Bike Mode is active
- [ ] Automatically restore rotation after reboot
- [ ] Configurable default landscape direction
- [ ] Haptic feedback when Bike Mode activates
- [ ] Optional notification saying “Bike Mode Active”
- [ ] Android Dynamic Shortcut
- [ ] Material You theming
- [ ] AMOLED dark mode

---

# 29. Potential P2 Feature — Automatic Bike Mode

Future versions could optionally activate Bike Mode when:

```text
Specific Bluetooth device connects
```

Example:

```text
Motorcycle Bluetooth / helmet intercom connected
        ↓
Enable Bike Mode
```

And:

```text
Bluetooth disconnected
        ↓
Restore rotation
```

This should NOT be part of MVP because it introduces additional permissions and complexity.

---

# 30. Potential P2 Feature — Navigation App Launch

Future option:

```text
Enable Bike Mode
       ↓
Launch preferred navigation application
```

User could choose:

- Google Maps
- Waze
- HERE WeGo
- Other

Example workflow:

```text
Tap Bike Mode
      ↓
Landscape locked
      ↓
Google Maps opens
```

Again, this is not required for MVP.

---

# 31. State Machine

```text
                 ┌───────────────┐
                 │   APP START   │
                 └───────┬───────┘
                         │
                         ▼
                Check Permission
                         │
             ┌───────────┴───────────┐
             │                       │
             ▼                       ▼
        Permission               Permission
         Granted                  Missing
             │                       │
             │                  Request Access
             │                       │
             └───────────┬───────────┘
                         │
                         ▼
                 Bike Mode OFF
                         │
                         │ Enable
                         ▼
               Save Current State
                         │
                         ▼
               Disable Auto Rotate
                         │
                         ▼
                Force Landscape
                         │
                         ▼
                  Bike Mode ON
                         │
                         │ Disable
                         ▼
               Restore Saved State
                         │
                         ▼
                 Bike Mode OFF
```

---

# 32. Acceptance Criteria

## AC-01

Given Bike Mode is OFF,

when the user enables Bike Mode,

then Android Auto-rotate must be disabled.

---

## AC-02

When Bike Mode activates,

the device must immediately rotate to the selected landscape direction.

---

## AC-03

While Bike Mode is active,

physical rotation or vibration should not cause Android to switch between portrait and landscape.

---

## AC-04

When Bike Mode is disabled,

the device's previous rotation configuration must be restored.

---

## AC-05

Bike Mode must remain active after the application itself is closed.

---

## AC-06

Bike Mode must function without internet access.

---

## AC-07

The user must be able to enable Bike Mode directly from Android Quick Settings.

---

## AC-08

The Quick Settings tile must clearly indicate whether Bike Mode is active.

---

## AC-09

Changing the preferred landscape direction must be remembered after application restart.

---

## AC-10

The app must not require root access.

---

## AC-11

The app must not require an active ADB connection.

---

## AC-12

After initial setup, the user must be able to operate Bike Mode completely without a computer.

---

# 33. Testing Plan

The most important testing environment is:

```text
Nothing Phone (2a)
+
Motorcycle mount
+
Real motorcycle vibration
```

### Test 1 — Stationary

Lock landscape and physically rotate phone repeatedly.

Expected:

```text
Screen stays landscape.
```

### Test 2 — Shake Test

Place phone horizontally and manually vibrate/shake the device.

Expected:

```text
Screen does not rotate.
```

### Test 3 — Motorcycle Idle

Mount phone while motorcycle engine is running.

Expected:

```text
Screen remains locked.
```

### Test 4 — Road Test

Use Google Maps while riding over:

- smooth road;
- rough road;
- acceleration;
- braking.

Expected:

```text
No unwanted screen rotation.
```

### Test 5 — Opposite Landscape

Select opposite landscape orientation.

Expected:

```text
Phone rotates 180° to the other horizontal direction.
```

### Test 6 — Restore

Disable Bike Mode.

Expected:

```text
Original Android rotation behavior returns.
```

### Test 7 — Application Closed

Enable Bike Mode and remove the app from Recents.

Expected:

```text
Landscape remains locked.
```

### Test 8 — Quick Settings

Enable and disable Bike Mode repeatedly from Quick Settings.

Expected:

```text
No need to open the app.
```

---

# 34. MVP Success Metric

The MVP is successful if this scenario works reliably:

```text
Nothing Phone (2a)
        ↓
Mounted horizontally on motorcycle
        ↓
Bike Mode enabled
        ↓
Google Maps running
        ↓
Motorcycle vibration present
        ↓
Screen remains permanently landscape
```

The most important success criterion is not downloads, engagement, or session duration.

It is simply:

> **Zero unwanted orientation changes during a normal motorcycle ride.**

---

# 35. Product Philosophy

Bike Rotation Lock should remain extremely small and focused.

It is not intended to become a general phone-customization suite.

The product should solve one problem exceptionally well:

> **One tap to make a horizontally mounted Android phone stay horizontal.**

The ideal finished product should feel almost like a native Android setting rather than a traditional application.

After initial configuration, users should rarely need to open the application itself.

Their primary interface should be:

**Android Quick Settings → Bike Mode.**