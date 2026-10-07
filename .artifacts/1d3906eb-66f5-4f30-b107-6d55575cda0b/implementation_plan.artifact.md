# PolarenBridge AAOS Companion App Implementation Plan

We are scaffolding an Android Automotive OS (AAOS) headless companion app that runs entirely in the background, except for a one-time minimal Jetpack Compose pairing screen. It captures `CarPropertyManager` data (trip state, EV battery/charging state, 12V telemetry) and relays this as temporary outbox data to a backend which is polled by a master iOS app.

## User Review Required
> [!IMPORTANT]
> **Car API Permissions:** Access to properties like `android.car.permission.CAR_SPEED`, `CAR_POWERTRAIN`, and `CAR_ENERGY` on Android Automotive usually require system/privileged signature or OEM explicit allow-listing (especially for properties that aren't exposed cleanly to 3rd party apps). In the emulator, many are available. On a real Polestar, it may be heavily locked down. We'll add the permissions, but be aware you may need special OEM support for full integration.
>
> **Auto-Start Mechanism:** AAOS defines `android.car.intent.action.RECEIVER_AUTO_START` but standard `android.intent.action.BOOT_COMPLETED` is more robust across Android SDK levels. We will use both for safety to ensure the foreground service wakes up properly when the head unit boots.

## Proposed Module Structure and Architecture

### 1. `app` Module (The AAOS App)
We will focus on modifying the existing `app` module to become a proper AAOS application.
- **Manifest:** `<uses-feature android:name="android.hardware.type.automotive" android:required="true"/>`. Set up Foreground Service types (`connectedDevice` or `dataSync` depending on exact target SDK API rules), permissions, Boot Receiver.
- **Pairing Activity:** A minimal Jetpack Compose `ComponentActivity`.
- **Background Service:** `CarSyncService` - a Foreground Service.
- **Storage:** A Room Database for the outbox.
- **Networking:** Retrofit + OkHttp for the Relay calls.
- **Security:** Android Keystore for managing the pairing shared secret.
- **Car Connection:** Direct use of `Car` and `CarPropertyManager`.

_Note:_ The current project uses `androidx.car.app` which is the **Car App Library (Android Auto / AAOS templated apps)**. Since you want a true custom AAOS app (with your own Compose activity and a raw background service reading `CarPropertyManager`), we actually want standard Android dependencies + Jetpack Compose + the `android.car` API wrapper, rather than the restrictive Car App Library templates.

### 2. Dependency Setup
Update `libs.versions.toml`, `app/build.gradle.kts`, and `build.gradle.kts` to support:
- Kotlin version (e.g. 1.9.22+ for Compose)
- Jetpack Compose (BOM, Material3, UI)
- Room (for the local outbox)
- Retrofit (for backend relay)
- `android.car` framework classes (provided by `compileOnly` or directly via standard Android SDK since AAOS includes them).

### Execution Phases
We will break the implementation down into manageable steps:

1. **Gradle & Project Structure:** Update dependencies (Compose, Room, Coroutines, Network). Remove the currently stubbed `shared` Car App Library module if it's not needed (or just leave it unused).
2. **Permissions & Manifest:** Add necessary manifest declarations (foreground service, boot, internet, car permissions).
3. **The Foreground Service & Boot Receiver:** Create `CarSyncService` and `BootReceiver`. Establish the persistent low-priority notification to ensure it runs forever.
4. **The Pairing UI:** Implement the single `PairingActivity` in Compose that can start the service.
5. **Security & State:** Implement a basic Keystore helper and SharedPreferences to store the pairing state.
6. **Room Database Outbox:** Set up the Room entities, DAO, and database for transient event storage.
7. **CarPropertyManager Integration:** Inject the `Car` object, connect to it, and set up listeners for speed, gear, EV battery, etc.
8. **Network Relay:** Set up a Coroutine worker/flow to post data from the outbox to the backend.

## Verification Plan

### Automated Checks
- Verify gradle builds successfully.

### Manual Verification
1. Run in the Android Studio **Automotive OS Emulator (API 32/33/34)**.
2. Verify the `PairingActivity` launches and renders.
3. Simulate a reboot (or broadcast `BOOT_COMPLETED`) and verify `CarSyncService` starts automatically and shows its notification.
4. Use the Emulator's extended controls (VHAL properties injection) to simulate changing speed/battery and observe the Logcat to verify `CarPropertyManager` receives the updates.
