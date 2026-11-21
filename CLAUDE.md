# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**EvilSpins NFC Debug** - A Capacitor-based Android app for debugging NFC tags, light sensor readings, and file storage operations. The app consists of a web UI (www/index.html) communicating with four custom Capacitor plugins via JavaScript bridges.

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      Web UI (www/index.html)                │
│  ┌─────────────┐  ┌──────────────┐  ┌────────────────────┐  │
│  │ Status Panel│  │ Action Buttons│ │     Log Panel      │  │
│  │  (NFC/LUX/  │  │ (Create/Delete│ │  (NFC, LUX, File)  │  │
│  │   Files)    │  │    File)     │ │                    │  │
│  └──────┬──────┘  └──────┬───────┘  └─────────┬──────────┘  │
└─────────┼────────────────┼────────────────────┼─────────────┘
          │                │                    │
    ┌─────▼─────┐    ┌─────▼─────┐        ┌────▼─────┐
    │ EvilSpins │    │ EvilSpins │        │ EvilSpins│
    │    NFC    │    │ LightSensor│       │ FileStorage│
    │  Plugin   │    │  Plugin   │        │  Plugin  │
    └─────┬─────┘    └─────┬─────┘        └────┬─────┘
          │                │                    │
    ┌─────▼────────────────▼────────────────────▼─────┐
    │              Android Native Layer                │
    │  NfcAdapter | SensorManager | Storage APIs      │
    └──────────────────────────────────────────────────┘
```

## Key Components

### Web UI (`www/index.html`)
Single-page debug interface with:
- **Status Panel**: NFC tag content, light sensor (lux), file permission status
- **Action Buttons**: Create/Delete test files
- **Log Panel**: Real-time logs filtered by type (NFC, LUX, File, Info, Warning, Error)

### Capacitor Plugins (Android Java)
| Plugin | Class | Purpose |
|--------|-------|---------|
| EvilSpinsNfc | `NfcPlugin.java` | NFC tag detection via Reader Mode + OnTagRemovedListener |
| EvilSpinsLightSensor | `LightSensorPlugin.java` | Ambient light sensor readings (periodic logging) |
| EvilSpinsFileStorage | `FileStoragePlugin.java` | Shared external storage read/write/list/delete |
| EvilSpinsPermissions | `PermissionsPlugin.java` | Query/request standard Android permissions |

All plugins auto-start listeners in `load()` method.

### MainActivity
Registers all four plugins before `super.onCreate()` to ensure they're available immediately.

## Development Commands

### Build & Deploy
```bash
# Build debug APK
npm run build
# Or directly:
ANDROID_HOME=/home/user/Android/Sdk \
PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools" \
JAVA_HOME=/usr/lib/jvm/java-17-openjdk \
PATH="$JAVA_HOME/bin:$PATH" \
cd android && ./gradlew assembleDebug

# Install to device
npm run install
# Or: adb install -r android/app/build/outputs/apk/debug/app-debug.apk

# Launch app
npm run launch
# Or: adb shell am start -n com.evilspins.app/.MainActivity

# Full rebuild cycle
npm run rebuild  # uninstall → build → install → launch

# View logs
npm run logs     # adb logcat filtered for app

# Screen mirroring
npm run scrcpy   # Requires scrcpy installed

# Dev mode (rebuild + scrcpy)
npm run dev
```

### Gradle Commands (from `android/`)
```bash
./gradlew assembleDebug          # Build debug APK
./gradlew installDebug           # Build + install
./gradlew clean                  # Clean build
./gradlew :app:dependencies      # Show dependencies
```

### Android SDK Path
The project expects:
- `ANDROID_HOME=/home/user/Android/Sdk`
- Java 17 at `/usr/lib/jvm/java-17-openjdk`

## Project Structure

```
virages/app/
├── www/
│   ├── index.html          # Main web UI (self-contained HTML/JS/CSS)
│   └── lib/                # Empty (Capacitor generated files at runtime)
├── android/
│   ├── app/
│   │   └── src/main/java/com/evilspins/app/
│   │       ├── MainActivity.java              # Plugin registration
│   │       ├── nfc/NfcPlugin.java             # NFC Reader Mode
│   │       ├── sensor/LightSensorPlugin.java  # Light sensor
│   │       ├── storage/FileStoragePlugin.java # File I/O
│   │       └── permissions/PermissionsPlugin.java # Permission bridge
│   ├── build.gradle            # App module config
│   ├── variables.gradle        # Version constants
│   ├── settings.gradle         # Module includes
│   ├── capacitor.settings.gradle # Capacitor Android module
│   └── gradle.properties       # Gradle settings
├── package.json                # npm scripts & Capacitor deps
├── capacitor.config.json       # Capacitor config (appId, plugins)
└── .gitignore
```

## Capacitor Configuration

`capacitor.config.json`:
```json
{
  "appId": "com.evilspins.app",
  "appName": "evilspins-app",
  "webDir": "www",
  "plugins": {
    "EvilSpinsPermissions": {},
    "EvilSpinsNfc": {},
    "EvilSpinsLightSensor": {},
    "EvilSpinsFileStorage": {}
  }
}
```

## Plugin Communication Pattern

All plugins use `notifyListeners(eventName, JSObject)` to send events to JS:

**JS side** (in index.html):
```javascript
plugin.addListener('nfcEvent', (event) => { ... });
plugin.addListener('lightSensorEvent', (event) => { ... });
plugin.addListener('fileStorageEvent', (event) => { ... });
```

**Java side** (all plugins):
```java
private void logEvent(String type, String message) {
    JSObject event = new JSObject();
    event.put("event", "log");
    event.put("type", type);
    event.put("message", message);
    notifyListeners("nfcEvent", event);  // or lightSensorEvent, fileStorageEvent
}
```

## Testing

No automated tests configured. Manual testing via:
1. `npm run rebuild` - full cycle
2. `npm run logs` - watch logcat
3. `npm run scrcpy` - visual debugging

## Common Issues

- **NFC not working**: Ensure device has NFC hardware, NFC enabled in settings, and app has NFC permission
- **File storage permission denied**: Android 11+ requires MANAGE_EXTERNAL_STORAGE (special permission via Settings)
- **Light sensor unavailable**: Some devices lack ambient light sensor
- **Gradle build fails**: Check ANDROID_HOME and JAVA_HOME paths in package.json scripts