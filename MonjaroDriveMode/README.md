# Start Drive Mode (Режим при старте)

Android app for the Geely Monjaro head unit (ECARX, Android 11).
Open the app, choose a mode (for example Sport). After each engine start the car is switched to that mode **one time**.
After that the app does nothing, so the driver can change the mode as usual.

Step-by-step guide for install, build and test on the Mac: [INSTALL.md](INSTALL.md).

## How it works

- A foreground service polls the car once per second through the ECARX `adaptapi` that is already on the head unit (reflection, the same `CarImpl(Context)` method as Monji).
- **New trip** ("armed"): the head unit boots, the ignition goes `OFF`/`ACC`/`LOCK`, or the head unit wakes up after deep sleep.
- **Engine start**: engine state `RUNNING` (or ignition `DRIVING`). The service sets the mode at once, one time, and disarms.
  No delay, no second attempt, no watching afterwards.
- Engine auto start-stop is not a new trip (the ignition stays ON).
- The mode is not changed if the car is already moving, or if the car does not report a drive mode within 30 s.

| ECARX constant | Value |
|---|---|
| `DM_FUNC_DRIVE_MODE_SELECT` | `570491136` |
| `DRIVE_MODE_SELECTION_ECO` | `570491137` |
| `DRIVE_MODE_SELECTION_COMFORT` | `570491138` |
| `DRIVE_MODE_SELECTION_DYNAMIC` (Sport) | `570491139` |
| `DRIVE_MODE_SELECTION_SNOW` | `570491145` |
| `DRIVE_MODE_SELECTION_SAND` | `570491149` |
| `DRIVE_MODE_SELECTION_OFFROAD` | `570491155` |
| `DRIVE_MODE_SELECTION_ADAPTIVE` (Smart?) | `570491158` |
| `SENSOR_TYPE_IGNITION_STATE` | `2097408` |
| `SENSOR_TYPE_ENGINE_STATE` | `2102784` |

## Build

Needs JDK 21 and the Android SDK (`~/Library/Android/sdk`, platform `android-37.0`).

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./gradlew assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```

The release APK is signed with this Mac's debug key (`~/.android/debug.keystore`).
Keep that file: an update signed with a different key must be uninstalled first.

## Install

Open ADB on the head unit (see `_ПРОЧТИ_МЕНЯ.txt` in the GeelyApps kit), connect the cable, then:

Copy the APK into `GeelyApps/apk/` and install it with `install.bat` / `install_mac.py` like the other apps.
It then shows up in the GInputBridge (GLauncher) app list. Or install it directly:

```sh
adb install -r -g app/build/outputs/apk/release/app-release.apk
```

Open the app once and choose a mode. Opening it also starts the background service.

## First test in the car

1. Open the app. The line under the list must show "Current mode: …", not "No connection to the car".
2. Change the mode with the car's own control. Check that the app shows the right name
   (especially Sport and Smart).
3. Choose **Sport**. Turn the car off, wait until the screen is off, start the engine again.
   The car must switch to Sport right after the start.
4. Change the mode while driving. The app must not change it back.

Live logs over ADB:

```sh
adb logcat -s DriveModeAuto DriveModeCar
```
