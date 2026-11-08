# Car Explorer: install, build and test

The flash drive steps (open ADB with the QR code, `install_mac.py`) are the same as for
Start Drive Mode: see [../MonjaroDriveMode/INSTALL.md](../MonjaroDriveMode/INSTALL.md), part 1.

## Build

```sh
cd MonjaroApps/CarExplorer
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties   # once per clone
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home

./gradlew clean assembleRelease assembleDebug lintRelease
```

- Car APK: `app/build/outputs/apk/release/app-release.apk`
- Emulator APK (fake car): `app/build/outputs/apk/debug/app-debug.apk`

## Install on the car

1. Copy `app-release.apk` as `CarExplorer-1.0.0.apk` into `GeelyApps/apk_force/` on the flash drive.
2. Open ADB on the head unit, connect the cable, run `python3 install_mac.py`: **1 → 11 → 0**.

   Or without the menu:

   ```sh
   adb install -r CarExplorer-1.0.0.apk
   ```

3. Open **Car Explorer** in the GInputBridge launcher.

## First session in the car

Stand still (writes are blocked above 3 km/h). Keep the cable connected if you want logs.

1. Open the app. The scan runs at once; the status line shows `Supported: functions X/Y, sensors X/Y`.
   If it shows "No connection to the car", send the text to the developer.
2. Tap **Save report** right away. This is the most useful file: the full list of what this car supports.
3. Turn **Watch: ON**. Change things with the car's own controls, one at a time
   (drive mode, Auto Hold, seat heating, a setting in the car menu).
   Each change appears in the **Changes** tab with the function name.
4. Tap **Save report** again, so the changes are in the file too.
5. Copy the reports to the Mac before you turn off the car (see the next section).

Writing values: tap a function → **Set value**. Change only settings you can easily change back
with the car's own controls.

## Copy reports to the Mac (Terminal)

The app saves reports on the head unit, in `/sdcard/Download/CarExplorer/`.
Copy them over the USB cable with ADB.

1. ADB must be open on the head unit, and the Mac must be connected to the **left** USB port.
   ADB closes when the head unit restarts: if you turned the car off, open ADB again (QR code).
2. Open Terminal on the Mac and check the connection:

   ```sh
   adb devices
   ```

   You must see one line with `device` at the end. An empty list means ADB is closed or the cable is not connected.
3. Copy all reports to the Desktop:

   ```sh
   adb pull /sdcard/Download/CarExplorer/ ~/Desktop/
   ```

   The reports are now in the folder `CarExplorer` on your Desktop
   (files `car_explorer_<date>_<time>.txt`).
4. Optional: see the list of reports on the head unit without copying:

   ```sh
   adb shell ls /sdcard/Download/CarExplorer/
   ```

Old reports stay on the head unit. Every `adb pull` copies all of them again.

## Test on the Mac (emulator)

```sh
$HOME/Library/Android/sdk/emulator/emulator -avd monjaro_test     # window 1
adb install -r -g app/build/outputs/apk/debug/app-debug.apk       # window 2
adb shell am start -n app.monjaro.carexplorer.fake/app.monjaro.carexplorer.MainActivity
```

The app is named "Car Explorer (fake car)". Check:

- Functions: 4 supported (drive mode, seat heating, Auto Hold, speed limit offset); lane keeping is
  not supported (untick "Only supported" to see it).
- Watch ON: the ignition sensor changes every 30 seconds in the Changes tab.
- Set drive mode to `DRIVE_MODE_SELECTION_DYNAMIC`: "accepted", and a change row appears.
- Seat heating asks for a zone first.
- Save report, then `adb pull /sdcard/Download/CarExplorer/`.

The release build on the emulator shows "ECARX classes are not on this device". This is expected.
