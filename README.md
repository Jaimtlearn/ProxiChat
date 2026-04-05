# ProxiChat

**Bluetooth proximity chat — no internet required.**

ProxiChat is a production-grade, cross-platform messaging app that lets users discover and chat with nearby devices using Bluetooth Low Energy (BLE). Every device acts as both a client and a server — fully decentralized, fully offline.

---

## Platforms

| Platform | Min Version | Tech Stack |
|----------|-------------|------------|
| **Android** | API 26 (Android 8.0) | Kotlin, Jetpack Compose, Material Design 3, Hilt, Room |
| **iOS** | iOS 16+ | Swift, SwiftUI, CoreBluetooth |

Both platforms use **the same BLE protocol**, so an Android device and an iOS device can discover each other and chat seamlessly.

---

## Features

### Core
- **Device Discovery** — BLE scanning + advertising with signal strength indicators
- **Peer-to-Peer Messaging** — direct Bluetooth communication, no backend/server
- **Dual Role** — every device is both a GATT server and GATT client simultaneously
- **Message Delivery Status** — Sending → Sent → Delivered → Read → Failed with retry
- **Typing Indicators** — real-time "typing..." feedback
- **Auto-Reconnect** — up to 5 attempts with progressive backoff
- **Message Persistence** — local database (Room on Android, file-based on iOS)

### Security
- **AES-256-GCM Encryption** — optional per-session message encryption
- **No External Storage** — all data stays on-device
- **No Internet** — zero network traffic, zero tracking

### UI/UX
- **Material Design 3** (Android) / **Native SwiftUI** (iOS)
- **Dark/Light Theme** — system-following or manual override
- **Dynamic Color** — Material You on Android 12+
- **Smooth Animations** — message transitions, scanning pulses, connection indicators
- **Polished Chat Bubbles** — rounded corners, timestamps, status ticks
- **Signal Strength Bars** — 4-level indicator based on RSSI
- **Empty States** — helpful messages when no devices/messages found
- **3-Step Onboarding** — welcome → permissions → profile setup

---

## Project Structure

```
ProxiChat/
├── app/                          # Android app
│   └── src/main/
│       ├── java/com/proxichat/app/
│       │   ├── data/
│       │   │   ├── bluetooth/    # BLE advertiser, scanner, GATT server/client, protocol
│       │   │   ├── db/           # Room database, entities, DAOs
│       │   │   ├── preferences/  # DataStore settings
│       │   │   └── repository/   # Repository implementations
│       │   ├── domain/
│       │   │   ├── model/        # ChatDevice, ChatMessage, ConnectionState
│       │   │   └── repository/   # Repository interfaces
│       │   ├── di/               # Hilt modules
│       │   ├── service/          # Foreground service
│       │   └── ui/
│       │       ├── theme/        # MD3 color, typography, shapes
│       │       ├── navigation/   # Compose Navigation
│       │       ├── onboarding/   # Welcome + permissions + profile
│       │       ├── discovery/    # Nearby device list
│       │       ├── chat/         # Chat screen
│       │       ├── settings/     # Settings screen
│       │       └── components/   # ChatBubble, DeviceCard, MessageInput, etc.
│       ├── res/                  # Android resources
│       └── AndroidManifest.xml
│
├── ios/ProxiChat/                # iOS app
│   ├── Models/                   # ChatDevice, ChatMessage, Enums
│   ├── Bluetooth/                # PeripheralManager, CentralManager, Controller, Protocol
│   ├── Data/                     # MessageStore, UserSettings
│   ├── ViewModels/               # MVVM ViewModels
│   ├── Views/                    # SwiftUI screens
│   │   └── Components/           # Reusable view components
│   ├── Theme/                    # AppTheme colors
│   ├── Resources/Info.plist
│   ├── ProxiChatApp.swift        # App entry point
│   └── ContentView.swift         # Root navigation
│
├── gradle/                       # Gradle wrapper + version catalog
├── build.gradle.kts              # Project-level build
├── settings.gradle.kts
└── README.md
```

---

## BLE Architecture

Both platforms implement the same protocol for cross-platform interoperability.

### UUIDs (shared)
| UUID | Purpose |
|------|---------|
| `a1b2c3d4-e5f6-7890-abcd-ef1234567890` | ProxiChat Service |
| `a1b2c3d4-e5f6-7890-abcd-ef1234567891` | Message Write Characteristic |
| `a1b2c3d4-e5f6-7890-abcd-ef1234567892` | Message Notify Characteristic |
| `a1b2c3d4-e5f6-7890-abcd-ef1234567893` | Profile Read Characteristic |

### Communication Flow
```
Device A (Client)                    Device B (Server)
     │                                      │
     │──── BLE Connect ───────────────────>│
     │──── Discover Services ─────────────>│
     │──── Subscribe to Notify Char ──────>│
     │                                      │
     │──── Write message to Write Char ───>│  (A → B)
     │<─── Notification on Notify Char ────│  (B → A)
     │                                      │
     │  Bidirectional chat established     │
```

### Message Protocol (JSON over BLE)
```json
{
  "t": "MSG",
  "i": "uuid-string",
  "s": "sender-address",
  "ts": 1712300000000,
  "p": { "text": "Hello!" },
  "e": false
}
```

**Message types:** `MSG` (text), `ACK` (delivery receipt), `TYPING`, `PROFILE`, `DISCONNECT`

### Chunking
Messages larger than the BLE MTU are split into chunks with a 5-byte header:
```
[version: 1B] [sequence: 2B LE] [flags: 1B] [index: 1B] [payload]
```
Flags: `0x01` = first chunk, `0x02` = last chunk, `0x03` = single (both).

---

## Getting Started

### Prerequisites

- **Android**: Android Studio Hedgehog (2023.1.1) or later, JDK 17
- **iOS**: Xcode 15+, macOS Ventura or later
- **Physical devices** for both platforms (BLE does not work on emulators/simulators)

### Android Setup

1. **Open** the project root in Android Studio
2. **Sync** Gradle — it will download all dependencies automatically
3. **Connect** a physical Android device (API 26+) with USB debugging enabled
4. **Run** the `app` configuration

```bash
# Or build from command line:
./gradlew assembleDebug
```

> **Note**: If the Gradle wrapper JAR is missing, run `gradle wrapper` once in the project root, or let Android Studio regenerate it.

### iOS Setup

1. **Open Xcode** and create a new project:
   - File → New → Project → iOS → App
   - Product Name: `ProxiChat`
   - Interface: SwiftUI
   - Language: Swift
   - Bundle Identifier: `com.proxichat.app`

2. **Delete** the auto-generated `ContentView.swift` and `ProxiChatApp.swift`

3. **Drag** all files from `ios/ProxiChat/` into the Xcode project navigator

4. **Configure Info.plist** — the one at `ios/ProxiChat/Resources/Info.plist` includes:
   - `NSBluetoothAlwaysUsageDescription`
   - `UIBackgroundModes`: `bluetooth-central`, `bluetooth-peripheral`
   - `UIRequiredDeviceCapabilities`: `bluetooth-le`

5. **Set deployment target** to iOS 16.0+

6. **Build & Run** on a physical iPhone (BLE does not work in Simulator)

### First Launch

1. The onboarding flow will guide through permissions and profile setup
2. Grant Bluetooth (and Location on Android) permissions when prompted
3. Set a display name — this is broadcast to nearby devices
4. The discovery screen will automatically start scanning

---

## How It Works

### Discovery
Each device simultaneously:
- **Advertises** a BLE service with the ProxiChat UUID + display name
- **Scans** for other devices advertising the same UUID

Discovered devices are shown with RSSI-based signal strength. Stale devices (not seen for 30s) are automatically pruned.

### Connection
When you tap a device:
1. Your device connects as a **GATT client** to the remote device's **GATT server**
2. Service discovery finds the ProxiChat characteristics
3. MTU negotiation requests 512 bytes (from the default 23)
4. Client subscribes to the notify characteristic
5. Bidirectional channel is established

### Messaging
- **Outgoing**: Serialize message to JSON → chunk if needed → write to remote's Write Characteristic
- **Incoming**: Receive notification/write → reassemble chunks → deserialize JSON → display
- **ACKs**: Automatic delivery acknowledgments update the sender's status ticks

### Background (Android)
A foreground service (`BluetoothChatService`) keeps BLE connections alive when the app is backgrounded.

### Background (iOS)
`UIBackgroundModes` with `bluetooth-central` and `bluetooth-peripheral` allows CoreBluetooth to continue operating in the background.

---

## Permissions

### Android
| Permission | Purpose | Required Since |
|---|---|---|
| `BLUETOOTH_SCAN` | Discover nearby BLE devices | Android 12 |
| `BLUETOOTH_ADVERTISE` | Make device discoverable | Android 12 |
| `BLUETOOTH_CONNECT` | Connect to discovered devices | Android 12 |
| `ACCESS_FINE_LOCATION` | BLE scanning (older Android) | Android 6 |
| `FOREGROUND_SERVICE` | Keep connections alive in background | Android 9 |
| `POST_NOTIFICATIONS` | New message notifications | Android 13 |

### iOS
| Key | Purpose |
|---|---|
| `NSBluetoothAlwaysUsageDescription` | BLE access |
| `bluetooth-central` background mode | Scan & connect in background |
| `bluetooth-peripheral` background mode | Advertise & serve in background |

---

## Configuration

Settings available in-app:

| Setting | Description | Default |
|---|---|---|
| Display Name | Your name shown to nearby users | Set during onboarding |
| Dark Mode | System / Always On / Always Off | System |
| Discoverable | Whether your device advertises | On |
| Auto-reconnect | Reconnect to known devices | On |
| Encryption | AES-256-GCM message encryption | Off |

---

## Technical Details

### Dependencies (Android)

| Library | Version | Purpose |
|---------|---------|---------|
| Jetpack Compose BOM | 2024.06.00 | UI framework |
| Material 3 | latest | Design system |
| Hilt | 2.51.1 | Dependency injection |
| Room | 2.6.1 | SQLite database |
| DataStore | 1.1.1 | Preferences storage |
| Navigation Compose | 2.7.7 | Screen navigation |
| Accompanist | 0.34.0 | Permissions handling |
| Coroutines | 1.8.1 | Async operations |
| Gson | 2.11.0 | JSON serialization |

### iOS (no external dependencies)

The iOS app uses only Apple frameworks:
- **SwiftUI** — UI
- **CoreBluetooth** — BLE
- **Combine** — Reactive streams
- **Foundation** — JSON, file I/O, UserDefaults

---

## Limitations

- **Range**: BLE range is ~10-50 meters depending on environment and device hardware
- **Throughput**: BLE is designed for low-bandwidth data; large file transfers will be slow
- **Simultaneous connections**: Practical limit of ~5-7 concurrent BLE connections per device
- **iOS Simulator**: CoreBluetooth does not work in Simulator — physical device required
- **Android Emulator**: BLE is not available in standard Android emulators

---

## License

This project is provided as-is for educational and personal use.
