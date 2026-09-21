# Wireless Keyboard Sender (Android)

A secure, background-stable Android remote keyboard client that sends encrypted keystrokes over a direct local network socket connection to a desktop receiver application.

---

## Features

* **Direct Local Connection**: Operates directly over local TCP/UDP sockets without relying on external servers, APIs, or cloud services.
* **Seamless UDP Auto-Discovery**: Automatically broadcasts on UDP port `8567` to handle dynamic receiver IP changes without manual reconfiguration.
* **Persistent Background Connection**:
  * **Foreground Service**: Keeps the app active in the background, preventing Android Doze mode from killing active socket connections.
  * **Hardware Locks**: Holds `WifiLock` and `WakeLock` to prevent Wi-Fi power-saving sleep modes while connected.
  * **Heartbeat Keep-Alive**: Transmits encrypted `PING` packets every 15 seconds to ensure socket stability across routers.
* **Hardware-Backed Security**: Encrypts and stores saved session passwords, custom ports, and host addresses using Android Jetpack Security (`EncryptedSharedPreferences`) backed by Android KeyStore (AES-256 GCM).
* **AES Encryption**: Keystrokes are encrypted using a SHA-256 key derived from your session password before over-the-air transmission.

---

## Network Architecture & Protocol

| Parameter | Default Value | Description |
| :--- | :--- | :--- |
| **TCP Data Port** | `8566` | Primary socket port for encrypted keystroke payloads |
| **UDP Discovery Port** | `8567` | Broadcast port used for automatic IP detection |
| **Heartbeat Interval** | `15 seconds` | Background ping frequency to maintain socket state |
| **Discovery Timeout** | `3000 ms` | Timeout limit for UDP broadcast responses |

### Connection Lifecycle
1. **Discovery Phase**: App broadcasts `KEYBOARD_SENDER_SEARCH` via UDP to `255.255.255.255:8567`.
2. **Handshake Phase**: Desktop receiver replies with `KEYBOARD_RECEIVER_HERE:<IP_ADDRESS>`.
3. **Authentication**: App opens a TCP socket to `<IP_ADDRESS>:8566` and transmits the encrypted payload `AUTH:<PASSWORD>`.
4. **Active Session**: The server responds with `AUTH_OK`. The foreground service begins sending encrypted key events and regular heartbeat pings.

---

## Tech Stack & Dependencies

* **Language**: Kotlin 1.9+
* **Min SDK**: 24 (Android 7.0 Nougat)
* **Target SDK**: 34 (Android 14)
* **Key Libraries**:
  * `androidx.security:security-crypto:1.1.0-alpha06` (Android KeyStore hardware encryption)
  * `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3` (Asynchronous network operations)
  * `androidx.core:core-ktx:1.12.0`
  * `androidx.appcompat:appcompat:1.6.1`
  * `com.google.android.material:material:1.11.0`

---

## Permissions

Declared permissions required for network socket operation and persistent background operation:

```xml
<!-- Network Permissions -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

<!-- Background & Lock Permissions -->
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

---

## Building from Source

### Prerequisites
* Android Studio Jellyfish (or newer) / JDK 17+
* Gradle 8.2+

### Build Steps

1. **Clone the Repository**:
   ```bash
   git clone https://github.com/nipaca/wirelesskeybordsender.git
   cd wirelesskeybordsender
   ```

2. **Assemble Release APK**:
   ```bash
   ./gradlew assembleRelease
   ```

3. **Locate Output Artifact**:
   The generated APK will be placed in:
   `app/build/outputs/apk/release/app-release-unsigned.apk`

---

## Setup & Firewall Notice

* Make sure your Android device and target desktop are connected to the same Wi-Fi network.
* Ensure inbound traffic rules on the receiver host machine permit communication through **TCP Port 8566** and **UDP Port 8567**.