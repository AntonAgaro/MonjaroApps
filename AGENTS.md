# Agent instructions

Android apps for the owner's Geely Monjaro head unit. Each app is a standalone Gradle project in its own top-level folder. `MonjaroDriveMode/` is the working reference app: copy its setup and patterns.

## Head unit

- ECARX (Desay SV KX11-A3-L1), Android 11 (API 30). No Google services.
- Apps are installed over ADB from the owner's Monji kit (the `GeelyApps` folder, outside this repo; ask the owner for its location). Installed apps appear in the GInputBridge launcher, so every app needs a `MAIN`/`LAUNCHER` activity and an icon.
- Foreground services of type `dataSync` survive on this head unit (proven by Monji and `MonjaroDriveMode`).
- Screen text: Russian (`values-ru`) for the car, English (`values`) as the default.

## Talking to the car (ECARX adaptapi)

The ECARX `com.ecarx.xui.adaptapi` classes are already on the head unit. Load them by reflection; never bundle them in the APK.

- `Car.create()` runs `Authority.verifyAuthority()` and rejects apps without a system signature. Call the private `CarImpl(Context)` constructor by reflection instead. Confirmed in the car with our own package name (`app.monjaro.*`).
- Start from `MonjaroDriveMode/app/src/main/java/app/monjaro/drivemode/EcarxCar.kt`: connect, `ICarFunction` get/set/supported values, `ISensor` events and values. Extend it rather than writing a new wrapper.
- **Finding constants** (function IDs, values, sensor IDs): decompile the ECARX SDK bundled in the Climate Widget mod, then read the interfaces under `com/ecarx/xui/adaptapi/car/`:

  ```sh
  jadx -d <scratch>/mcw --no-res <kit>/GeelyApps/mods/Monjaro_Climate_Widget/v1.1b/MCW_v1.1b.apk
  ```

  `GeelyApps/apk/Monji.apk` shows how a working third-party app uses the API. Record every constant you use, with its SDK name, in the app's README.
- A constant's name in the SDK is a guess about the car until the owner confirms it in the car (example: Sport is `DRIVE_MODE_SELECTION_DYNAMIC`).

## Creating a new app

1. Create a top-level folder (`MonjaroXxx/`) by copying `MonjaroDriveMode/`'s Gradle files, wrapper and `.gitignore`. Package: `app.monjaro.<name>`.
2. Write the app. Keep the UI minimal: the owner wants one simple screen.
3. Build and lint with zero findings (see Build).
4. Write the app's `README.md` (behaviour, constants table) and `INSTALL.md` (flash drive install, build, emulator test), modelled on `MonjaroDriveMode/`.
5. Add a row to the root `README.md` table.

Done when the release APK builds, lint is clean, both docs exist, and the root table lists the app.

## Build

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./gradlew clean assembleRelease lintRelease
```

- Release builds are signed with `~/.android/debug.keystore`. Keep every app on that key: the car rejects an update signed with a different key.
- Raise `versionCode` and `versionName` for every APK the owner installs.

## Testing

- The car API exists only on the head unit. The emulator (`monjaro_test`, Android 11) proves install, icon, UI, saved settings and service start; the car connection always fails there with `ClassNotFoundException`, which is expected.
- Create emulators with `$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager`; the Homebrew `avdmanager` cannot see the SDK's system images.
- Car behaviour is proven only by the owner's test in the car. Give them a short checklist and the `adb logcat -s <tags>` command, and log every car read/write decision under a fixed tag.
- For a car test, copy the APK into `GeelyApps/apk_force/` on the owner's flash drive; menu item 11 of `install_mac.py` installs only that folder.

## Working with the owner

- Behaviour is exactly what the owner asked for. Timing tricks (delays, retries, watchers) come only after an in-car test shows they are needed, and only with the owner's approval.
