# Agent Bridge — Android Accessibility Service for Termux Automation

## Overview

A lightweight Android app that exposes the full Android Accessibility API to Termux via intents and a local TCP socket. This gives a CLI-based AI agent (Claude via Termux) complete control of the phone — tapping, swiping, typing, reading the screen, and navigating — without root or ADB.

## Architecture

```
Termux / Claude (brain)
   |
   |-- am broadcast ──────────► CommandReceiver (BroadcastReceiver)
   |                                    |
   |-- TCP socket (port 8765) ──► TcpCommandServer (Thread)
   |                                    |
   |                                    ▼
   |                          AgentAccessibilityService
   |                             |              |
   |                     dispatchGesture    getRootInActiveWindow
   |                     performGlobalAction  findNodesBy*
   |                     onAccessibilityEvent  performAction
   |                                    |
   |◄── file result ───────────────────-+── writes to ~/storage/shared/Documents/agent-bridge/
   |◄── TCP response ──────────────────-+── immediate JSON response over socket
```

## Tech Stack

- **Language:** Kotlin
- **Min SDK:** 24 (Android 7.0) — required for `dispatchGesture()`
- **Target SDK:** 34 (Android 14)
- **Build:** Gradle 8.5 + AGP 8.2.2 + JDK 17
- **CI/CD:** GitHub Actions (build APK on push, create release on tag)
- **Dependencies:** AndroidX Core, AppCompat, Gson

## Project Structure

```
agent-bridge/
├── .github/
│   └── workflows/
│       └── build.yml
├── app/
│   ├── src/main/
│   │   ├── java/com/agentbridge/
│   │   │   ├── AgentAccessibilityService.kt   # Core service
│   │   │   ├── CommandReceiver.kt             # BroadcastReceiver for intents
│   │   │   ├── TcpCommandServer.kt            # Local TCP socket server
│   │   │   ├── CommandProcessor.kt            # Parses & dispatches commands
│   │   │   ├── GestureHelper.kt               # Tap, swipe, long press, pinch
│   │   │   ├── UiTreeHelper.kt                # Dump UI tree, find elements
│   │   │   └── MainActivity.kt                # Status UI + enable service
│   │   ├── res/
│   │   │   ├── xml/accessibility_service_config.xml
│   │   │   ├── layout/activity_main.xml
│   │   │   └── values/strings.xml
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
├── gradle/wrapper/
│   ├── gradle-wrapper.jar
│   └── gradle-wrapper.properties
├── gradlew
├── gradlew.bat
└── plan.md
```

## Supported Commands

### Gestures (via dispatchGesture)
| Command | Extras | Description |
|---------|--------|-------------|
| `tap` | `x`, `y` | Tap at coordinates |
| `long_press` | `x`, `y`, `duration?` | Long press (default 1000ms) |
| `swipe` | `x1`, `y1`, `x2`, `y2`, `duration?` | Swipe between points |
| `pinch` | `centerX`, `centerY`, `startDist`, `endDist` | Pinch in/out |

### Element Interaction (via AccessibilityNodeInfo)
| Command | Extras | Description |
|---------|--------|-------------|
| `click_text` | `text` | Click element by visible text/label |
| `click_id` | `id` | Click element by resource ID |
| `type` | `text` | Type text into focused input field |
| `scroll` | `forward` (bool) | Scroll the first scrollable element |

### Navigation (via performGlobalAction)
| Command | Description |
|---------|-------------|
| `back` | Press back |
| `home` | Press home |
| `recents` | Open app switcher |
| `notifications` | Pull down notification shade |
| `quick_settings` | Open quick settings |
| `lock_screen` | Lock the screen (API 28+) |
| `screenshot` | Take screenshot (API 28+) |

### Screen Reading (via getRootInActiveWindow)
| Command | Returns | Description |
|---------|---------|-------------|
| `dump_ui` | JSON | Full UI tree with all elements, text, bounds, states |
| `get_text` | JSON | All visible text on screen |
| `get_foreground` | JSON | Current app package and activity |
| `find_text` | JSON | Find elements matching text query |
| `find_id` | JSON | Find elements matching resource ID |

## Communication Protocols

### 1. Intent-based (fire and forget)

Best for simple commands that don't need a response.

```bash
# Tap
am broadcast -n com.agentbridge/.CommandReceiver --es cmd tap --ef x 540.0 --ef y 1800.0

# Type text
am broadcast -n com.agentbridge/.CommandReceiver --es cmd type --es text "hello"

# Click by label
am broadcast -n com.agentbridge/.CommandReceiver --es cmd click_text --es text "Send"

# Press back
am broadcast -n com.agentbridge/.CommandReceiver --es cmd back
```

### 2. TCP Socket (bidirectional JSON)

Best for commands that return data (UI tree, foreground app, etc.) and for scripting.

```bash
# Send JSON command, receive JSON response
echo '{"cmd":"tap","x":540,"y":1800}' | nc -q1 127.0.0.1 8765

# Dump UI tree
echo '{"cmd":"dump_ui"}' | nc -q1 127.0.0.1 8765

# Get foreground app
echo '{"cmd":"get_foreground"}' | nc -q1 127.0.0.1 8765
```

### 3. File-based (fallback for broadcast results)

Results written to `/storage/emulated/0/Documents/agent-bridge/` — accessible from Termux at `~/storage/shared/Documents/agent-bridge/`.

## Key Implementation Details

### AccessibilityService Config (res/xml/accessibility_service_config.xml)

```xml
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:description="@string/accessibility_service_description"
    android:accessibilityEventTypes="typeAllMask"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagDefault|flagReportViewIds|flagRetrieveInteractiveWindows|flagIncludeNotImportantViews"
    android:canRetrieveWindowContent="true"
    android:canPerformGestures="true"
    android:notificationTimeout="50"
    android:settingsActivity="com.agentbridge.MainActivity" />
```

### Gesture Dispatch

```kotlin
// Tap — short duration path at a point
fun tap(x: Float, y: Float) {
    val path = Path().apply { moveTo(x, y) }
    val stroke = GestureDescription.StrokeDescription(path, 0L, 100L)
    val gesture = GestureDescription.Builder().addStroke(stroke).build()
    dispatchGesture(gesture, null, null)
}

// Swipe — path between two points
fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, duration: Long = 300L) {
    val path = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
    val stroke = GestureDescription.StrokeDescription(path, 0L, duration)
    val gesture = GestureDescription.Builder().addStroke(stroke).build()
    dispatchGesture(gesture, null, null)
}

// Long press — tap with long duration
fun longPress(x: Float, y: Float, duration: Long = 1000L) {
    val path = Path().apply { moveTo(x, y) }
    val stroke = GestureDescription.StrokeDescription(path, 0L, duration)
    val gesture = GestureDescription.Builder().addStroke(stroke).build()
    dispatchGesture(gesture, null, null)
}
```

### UI Tree Dump

Recursive traversal of `getRootInActiveWindow()` returning JSON with:
- `className`, `text`, `contentDescription`, `viewId`
- `bounds` (screen coordinates)
- `isClickable`, `isScrollable`, `isEnabled`, `isChecked`, `isFocused`, `isVisibleToUser`
- `children` (nested)

### Click by Text (with parent walk)

`findAccessibilityNodeInfosByText()` returns matching nodes. If the node itself isn't clickable, walk up to find the nearest clickable parent and click it.

### Click by Resource ID

`findAccessibilityNodeInfosByViewId()` with full resource ID format: `com.package.name:id/view_id`

### Type Text

Find the input-focused node via `findFocus(FOCUS_INPUT)`, then `performAction(ACTION_SET_TEXT)` with a Bundle containing the text.

### Foreground App Detection

Track `TYPE_WINDOW_STATE_CHANGED` events in `onAccessibilityEvent()` — store `event.packageName` and `event.className`.

## Build Steps

1. Create GitHub repo
2. Push the Android project
3. GitHub Actions builds APK on push
4. Download APK from Actions artifacts or GitHub Releases (on tag)
5. Install on phone
6. Enable accessibility service in Settings > Accessibility > Agent Bridge
7. Send commands from Termux

## GitHub Actions Workflow (.github/workflows/build.yml)

```yaml
name: Build APK

on:
  push:
    branches: [main]
    tags: ['v*']
  pull_request:
    branches: [main]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'zulu'
          cache: 'gradle'

      - name: Build debug APK
        run: chmod +x ./gradlew && ./gradlew assembleDebug

      - name: Upload APK
        uses: actions/upload-artifact@v4
        with:
          name: agent-bridge-debug
          path: app/build/outputs/apk/debug/app-debug.apk

  release:
    needs: build
    if: startsWith(github.ref, 'refs/tags/v')
    runs-on: ubuntu-latest
    permissions:
      contents: write
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'zulu'
          cache: 'gradle'
      - run: chmod +x ./gradlew && ./gradlew assembleRelease
      - uses: softprops/action-gh-release@v2
        with:
          files: app/build/outputs/apk/release/app-release-unsigned.apk
```

## Setup After Install

1. Install APK on phone
2. Go to **Settings > Accessibility > Agent Bridge** → enable
3. Run `termux-setup-storage` if not already done (for file-based results)
4. Test from Termux:
   ```bash
   # Test tap
   am broadcast -n com.agentbridge/.CommandReceiver --es cmd tap --ef x 540.0 --ef y 1200.0

   # Test UI dump via socket
   echo '{"cmd":"dump_ui"}' | nc -q1 127.0.0.1 8765
   ```

## Future Enhancements

- **Screenshot capture** via `takeScreenshot()` (API 30+) for vision model integration
- **Notification listener** to read all notifications (including RCS messages)
- **Macro recording** — record a sequence of actions and replay them
- **Wrapper scripts** for Termux: `agent-tap 540 1800`, `agent-type "hello"`, `agent-dump`, etc.
- **Multi-gesture support** — complex gestures with multiple simultaneous touch points
- **Wait-for-element** — block until a specific element appears on screen
