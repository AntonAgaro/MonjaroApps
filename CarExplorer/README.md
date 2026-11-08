# Car Explorer

Developer tool for the Geely Monjaro head unit (ECARX, Android 11).
It shows which car functions and sensors this car really supports, their current values,
and which function changes when you press a button or change a setting in the car.

Install and test steps: [INSTALL.md](INSTALL.md).

## What it does

- **Functions tab**: every ECARX car function with its id, status (`active`, `notactive`,
  `notavailable`, `error`), current value (per zone, for example per seat) and supported values.
  "Only supported" hides functions the car does not have.
- **Sensors tab**: ignition, engine, speed, gear and the other ECARX sensors.
- **Changes tab**: with **Watch: ON**, the app reads all supported functions and sensors every second
  and logs every change (`old → new`). Turn it on, then change something in the car
  (for example Auto Hold in the car settings) and see which function changed.
  Watch keeps running while you use other apps.
- **Search**: by name (`auto hold`, `seat heat`) or by number. A number that is not in the list
  (decimal or `0x…`) still gets a row, so any id can be read and written.
- **Set value**: tap a function → Set value → choose a zone (if any) → choose a supported value or type one
  (`1`, `570491139`, `0x22010103`, `2.5` for float functions). Every write asks for confirmation.
  Writes are blocked while the car moves faster than 3 km/h.
- **Save report**: writes everything (supported functions, sensors, changes, not supported functions)
  to `/sdcard/Download/CarExplorer/car_explorer_<date>.txt`.

## How it works

- Car access: the same `CarImpl(Context)` reflection as Start Drive Mode (see `EcarxCar.kt`),
  extended with zones, function status, float values and sensor status.
- **Names are read at runtime** from the ECARX interfaces on the head unit (`Catalog.kt`).
  Nothing from the SDK is bundled, and the names always match the ECARX version on this car.
  A value constant is its function id plus a small number, so values are named by matching
  them against all known constants. Values 0, 1, 253, 254, 255 use the common names
  OFF, ON, ERROR, NONE, UNKNOWN.
- The explorer state lives for the whole process (`Explorer.kt`); one worker thread does all car calls.

## Two builds

| Build | Package | For |
|---|---|---|
| `release` | `app.monjaro.carexplorer` | The car. |
| `debug` | `app.monjaro.carexplorer.fake` | The emulator. Bundles a small fake car (`src/debug`): drive mode, Auto Hold, seat heating with two zones, one float setting, and an ignition sensor that switches every 30 s. |

The fake car has the same class names as the real ECARX classes, so the real reflection code runs
end to end on the emulator. Install only the release build on the car.

## Logs

```sh
adb logcat -s CarExplorer
```

Every write and every change is logged.
