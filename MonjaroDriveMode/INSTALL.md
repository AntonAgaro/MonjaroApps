# Start Drive Mode: install, build and test

This guide has three parts:

1. [Install the app on the car](#1-install-the-app-on-the-car) with the Monji flash drive.
2. [Build the app](#2-build-the-app) on the Mac.
3. [Test the app on the Mac](#3-test-the-app-on-the-mac): what you can test without the car.

App name on the head unit: **"Режим при старте"** (Start Drive Mode). Package: `app.monjaro.drivemode`.

---

## 1. Install the app on the car

You need:

- The flash drive prepared with the Monji instructions (FAT32, with the `GeelyApps` folder).
- The USB cable, Type-A to Type-A.
- A Mac with `adb` (`brew install android-platform-tools`).

### Step 1. Copy the APK to the flash drive

1. Copy the file `StartDriveMode-1.1.0.apk` into the **`GeelyApps/apk_force`** folder on the flash drive.
   - A ready copy is here: `Monjaro_DEV_172_primal/GeelyApps/apk_force/StartDriveMode-1.1.0.apk`.
   - A new build is here: `MonjaroDriveMode/app/build/outputs/apk/release/app-release.apk` (see part 2).
2. Delete the old `Logs_XXXXX-XXXXX` folders in the root of the flash drive (if they exist).

Why `apk_force`: menu item **11** installs only the APKs from this folder.
Menu item 2 installs everything from the `apk` folder. On this Mac there is no `aapt` tool, so item 2
cannot check versions and reinstalls all apps (RuStore alone is 92 MB).

### Step 2. Open ADB on the head unit

ADB closes after every head unit restart. Repeat this step every time you want to install something.

1. Start the car. Open the engineering menu → **Customization** → **ADB**.
2. Set the top ADB switch to **OPEN**.
3. Put the flash drive into the **left** USB port.
4. Wait for **"Android - OK, QNX - OK"**. Press the button and take out the flash drive.
   **Do not close the QR code.**
5. Put the flash drive into the Mac. In Terminal:

   ```sh
   cd /Volumes/<FLASH_NAME>/GeelyApps
   python3 QR_mac.py
   ```

6. Enter the 6-digit code in the answer field under the QR code and press the left button.
   The QR code closes and ADB is open.

### Step 3. Install the app

1. Connect the Mac to the **left** USB port of the head unit with the Type-A to Type-A cable.
2. In Terminal (still in the `GeelyApps` folder):

   ```sh
   python3 install_mac.py
   ```

3. Choose in this order:
   - **1**: check the connection. You must see the head unit serial number.
   - **11**: install from `apk_force` (installs only Start Drive Mode).
   - **3**: give permissions to the apps.
   - **0**: exit.

Or install without the menu:

```sh
adb install -r -g StartDriveMode-1.1.0.apk
```

### Step 4. Turn off battery saving for the app (recommended)

While the cable is still connected:

```sh
adb shell dumpsys deviceidle whitelist +app.monjaro.drivemode
```

This stops Android from closing the background part of the app.

### Step 5. Choose the mode

1. Open the GInputBridge launcher in the same way as for the other apps
   (long press on the media icon in the dock → minus → blue icon).
2. Find the speedometer icon **"Режим при старте"** and open it.
3. Tap **Спорт** (Sport). The choice is saved at once.
4. Check the line under the list:
   - **"Текущий режим: …"**: the app is connected to the car. Good.
   - **"Нет связи с машиной: …"**: the app cannot talk to the car. Send the full text to the developer.

### Step 6. First test in the car

1. Change the mode with the car's own control. Check that the app shows the same name
   (especially Sport and Smart).
2. Turn the car off. Wait until the screen is off. Start the engine again.
   The car must switch to Sport right after the start.
3. Change the mode while driving. The app must not change it back.
4. If the car goes back to Comfort a moment after the start, tell the developer.
   The fix is a short delay (1–2 seconds) before the switch.

To see what the app does, keep the cable connected and run:

```sh
adb logcat -s DriveModeAuto DriveModeCar
```

Typical lines:

| Log line | Meaning |
|---|---|
| `Head unit started. Ready for engine start.` | The head unit booted. The next engine start will set the mode. |
| `Ignition: ON -> OFF` / `Engine: STOP -> RUNNING` | The app reads the car state. |
| `Ready for next engine start (...)` | A new trip begins. |
| `Engine started. Set mode ... -> ...: accepted` | The mode was set. |
| `connect failed: ...` | No connection to the car. Send this line to the developer. |

### Update and remove

- **Update**: install the new APK in the same way. Your chosen mode stays.
  This works only if the new APK is built on the same Mac (same signing key, see part 2).
- **Remove**: `adb uninstall app.monjaro.drivemode`, or menu item **12** in `install_mac.py`.

---

## 2. Build the app

### One-time setup

```sh
# Java 21
brew install openjdk@21

# Android SDK command line tools
brew install --cask android-commandlinetools
brew install android-platform-tools

export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=$HOME/Library/Android/sdk

sdkmanager --sdk_root=$ANDROID_HOME "platforms;android-37.0" "build-tools;36.0.0" "platform-tools"
```

The project already has `local.properties` with `sdk.dir=/Users/antonagaronov/Library/Android/sdk`.

### Build

```sh
cd /Users/antonagaronov/Documents/MonjaroApps/MonjaroDriveMode
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home

./gradlew clean assembleRelease
```

The APK is here: `app/build/outputs/apk/release/app-release.apk`.

Optional code check (lint):

```sh
./gradlew lintRelease
```

### Before a new release

1. In `app/build.gradle.kts`, increase `versionCode` by 1 and change `versionName`
   (for example `versionCode = 3`, `versionName = "1.2.0"`).
2. Build.
3. Copy the APK to the flash drive with the version in the name, for example
   `StartDriveMode-1.2.0.apk`, into `GeelyApps/apk_force`. Remove the old APK from that folder.

### Signing key

The APK is signed with the debug key of this Mac: `~/.android/debug.keystore`.

- **Keep a backup of this file.** If you build on another Mac (or lose the file), the key is different.
- With a different key, the car refuses the update. Then you must remove the old app first
  (`adb uninstall app.monjaro.drivemode`) and choose the mode again.

---

## 3. Test the app on the Mac

### What you can test and what you cannot

The app talks to the car through ECARX system classes. These classes exist **only on the head unit**.
No emulator has them. So on the Mac:

| Part | Test on the Mac? |
|---|---|
| App installs, icon, app name | Yes |
| Screen, mode list, the choice is saved | Yes |
| Background part starts, notification, start after reboot | Yes |
| Connection to the car, reading the current mode | **No**: shows "No connection to the car" |
| Setting the mode at engine start | **No**: only in the car |

### Run the app in the Android emulator

The head unit runs Android 11, so use an Android 11 (API 30) emulator.

One-time setup (about 2 GB download):

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=$HOME/Library/Android/sdk

sdkmanager --sdk_root=$ANDROID_HOME "cmdline-tools;latest" "emulator" "system-images;android-30;google_apis;arm64-v8a"

echo no | $ANDROID_HOME/cmdline-tools/latest/bin/avdmanager create avd -n monjaro_test -k "system-images;android-30;google_apis;arm64-v8a"
```

Use `avdmanager` from the SDK folder, as above. The Homebrew `avdmanager` looks for system images
only in its own folder and fails with `Error: Package path is not valid`.

Start the emulator (keep this Terminal window open):

```sh
$HOME/Library/Android/sdk/emulator/emulator -avd monjaro_test
```

In a second Terminal window, install and open the app:

```sh
cd /Users/antonagaronov/Documents/MonjaroApps/MonjaroDriveMode
adb install -r -g app/build/outputs/apk/release/app-release.apk
adb shell am start -n app.monjaro.drivemode/.MainActivity
```

If you have the car (by cable) and the emulator connected at the same time, add `-e` to `adb`
for the emulator (`adb -e install ...`).

### What to check in the emulator

1. **Icon**: the app "Start Drive Mode" is in the app list (the emulator is in English, so the English name is shown).
2. **Screen**: you see the mode list. The line under it says
   `No connection to the car: ClassNotFoundException: com.ecarx...`. This is expected on the Mac.
3. **Save**: choose Sport, close the app, open it again. Sport must still be selected.
4. **Notification**: pull down the notification panel. You see the app notification.
5. **Start after reboot**:

   ```sh
   adb logcat -c
   adb reboot
   # wait until the emulator is up again
   adb logcat -s DriveModeAuto DriveModeCar
   ```

   You must see `Head unit started. Ready for engine start.` and then `connect failed: ...`
   (expected, there is no car).

To test the Russian names, change the emulator language: Settings → System → Languages → Русский.

### Test in the car without driving

The real test is in the car, but the car can stand still:

1. Install the app (part 1), connect the cable, run `adb logcat -s DriveModeAuto DriveModeCar`.
2. Start the engine and watch the log and the drive mode on the screen.
3. Turn the engine off and on again to test the next "trip".

### Possible later: a test mode with a fake car

The app can get a hidden "fake car" mode for the emulator, where you press buttons
("ignition off", "engine start", "driver changes mode") and see how the app reacts.
This is not built yet. Ask if you want it.
