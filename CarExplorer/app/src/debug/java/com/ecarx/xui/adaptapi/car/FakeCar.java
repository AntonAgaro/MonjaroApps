package com.ecarx.xui.adaptapi.car;

import com.ecarx.xui.adaptapi.FunctionStatus;
import com.ecarx.xui.adaptapi.car.base.ICarFunction;
import com.ecarx.xui.adaptapi.car.hvac.IHvac;
import com.ecarx.xui.adaptapi.car.sensor.ISensor;
import com.ecarx.xui.adaptapi.car.sensor.ISensorEvent;
import com.ecarx.xui.adaptapi.car.vehicle.IDriveMode;
import com.ecarx.xui.adaptapi.car.vehicle.IVehicle;
import com.ecarx.xui.adaptapi.vehicle.VehicleZone;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * A small fake car for the emulator (debug build only).
 *
 * Supported: drive mode, Auto Hold, seat heating (two zones) and one float setting.
 * Lane keeping aid exists but is "notavailable". The ignition sensor switches between
 * ON and ACC every 30 seconds, so the change log has something to show.
 */
final class FakeCar implements ICarFunction, ISensor {
    static final FakeCar INSTANCE = new FakeCar();

    private static final int[] DRIVE_MODES = {
            IDriveMode.DRIVE_MODE_SELECTION_ECO,
            IDriveMode.DRIVE_MODE_SELECTION_COMFORT,
            IDriveMode.DRIVE_MODE_SELECTION_DYNAMIC,
            IDriveMode.DRIVE_MODE_SELECTION_SNOW,
    };
    private static final int[] SEAT_LEVELS = {
            0, IHvac.SEAT_HEATING_LEVEL_1, IHvac.SEAT_HEATING_LEVEL_2, IHvac.SEAT_HEATING_LEVEL_3,
    };
    private static final int[] SEAT_ZONES = {VehicleZone.ZONE_ROW_1_LEFT, VehicleZone.ZONE_ROW_1_RIGHT};

    private final Map<String, Integer> values = new HashMap<>();
    private float speedLimitOffset = 5f;

    private FakeCar() {
        values.put(key(IDriveMode.DM_FUNC_DRIVE_MODE_SELECT, 0), IDriveMode.DRIVE_MODE_SELECTION_COMFORT);
        values.put(key(IVehicle.SETTING_FUNC_AUTO_HOLD, 0), 0);
        values.put(key(IHvac.HVAC_FUNC_SEAT_HEATING, VehicleZone.ZONE_ROW_1_LEFT), 0);
        values.put(key(IHvac.HVAC_FUNC_SEAT_HEATING, VehicleZone.ZONE_ROW_1_RIGHT), IHvac.SEAT_HEATING_LEVEL_2);
    }

    private static String key(int function, int zone) {
        return function + "/" + zone;
    }

    // ---- ICarFunction ----

    @Override
    public synchronized FunctionStatus isFunctionSupported(int function) {
        switch (function) {
            case IDriveMode.DM_FUNC_DRIVE_MODE_SELECT:
            case IVehicle.SETTING_FUNC_AUTO_HOLD:
            case IHvac.HVAC_FUNC_SEAT_HEATING:
            case IVehicle.SETTING_FUNC_SPEED_LIMIT_WARNING_OFFSET_VALUE:
                return FunctionStatus.active;
            default:
                return FunctionStatus.notavailable;
        }
    }

    @Override
    public FunctionStatus isFunctionSupported(int function, int zone) {
        return isFunctionSupported(function);
    }

    @Override
    public int[] getSupportedFunctionZones(int function) {
        return function == IHvac.HVAC_FUNC_SEAT_HEATING ? SEAT_ZONES.clone() : new int[0];
    }

    @Override
    public int getFunctionValue(int function) {
        return getFunctionValue(function, 0);
    }

    @Override
    public synchronized int getFunctionValue(int function, int zone) {
        Integer value = values.get(key(function, zone));
        if (value == null) throw new UnsupportedOperationException("fake car: no function " + function);
        return value;
    }

    @Override
    public float getCustomizeFunctionValue(int function) {
        return getCustomizeFunctionValue(function, 0);
    }

    @Override
    public synchronized float getCustomizeFunctionValue(int function, int zone) {
        if (function != IVehicle.SETTING_FUNC_SPEED_LIMIT_WARNING_OFFSET_VALUE) {
            throw new UnsupportedOperationException("fake car: no float function " + function);
        }
        return speedLimitOffset;
    }

    @Override
    public int[] getSupportedFunctionValue(int function) {
        return getSupportedFunctionValue(function, 0);
    }

    @Override
    public int[] getSupportedFunctionValue(int function, int zone) {
        switch (function) {
            case IDriveMode.DM_FUNC_DRIVE_MODE_SELECT:
                return DRIVE_MODES.clone();
            case IVehicle.SETTING_FUNC_AUTO_HOLD:
                return new int[]{0, 1};
            case IHvac.HVAC_FUNC_SEAT_HEATING:
                return SEAT_LEVELS.clone();
            default:
                return new int[0];
        }
    }

    @Override
    public boolean setFunctionValue(int function, int value) {
        return setFunctionValue(function, 0, value);
    }

    @Override
    public synchronized boolean setFunctionValue(int function, int zone, int value) {
        String key = key(function, zone);
        if (!values.containsKey(key)) return false;
        int[] allowed = getSupportedFunctionValue(function, zone);
        if (allowed.length > 0 && Arrays.stream(allowed).noneMatch(v -> v == value)) return false;
        values.put(key, value);
        return true;
    }

    @Override
    public boolean setCustomizeFunctionValue(int function, float value) {
        return setCustomizeFunctionValue(function, 0, value);
    }

    @Override
    public synchronized boolean setCustomizeFunctionValue(int function, int zone, float value) {
        if (function != IVehicle.SETTING_FUNC_SPEED_LIMIT_WARNING_OFFSET_VALUE) return false;
        speedLimitOffset = value;
        return true;
    }

    // ---- ISensor ----

    @Override
    public FunctionStatus isSensorSupported(int sensor) {
        switch (sensor) {
            case SENSOR_TYPE_CAR_SPEED:
            case SENSOR_TYPE_IGNITION_STATE:
            case SENSOR_TYPE_ENGINE_STATE:
                return FunctionStatus.active;
            default:
                return FunctionStatus.notavailable;
        }
    }

    @Override
    public int getSensorEvent(int sensor) {
        switch (sensor) {
            case SENSOR_TYPE_IGNITION_STATE:
                boolean on = (System.currentTimeMillis() / 30_000L) % 2 == 0;
                return on ? ISensorEvent.IGNITION_STATE_ON : ISensorEvent.IGNITION_STATE_ACC;
            case SENSOR_TYPE_ENGINE_STATE:
                return ISensorEvent.ENGINE_STATE_RUNNING;
            default:
                throw new UnsupportedOperationException("fake car: no event sensor " + sensor);
        }
    }

    @Override
    public float getSensorLatestValue(int sensor) {
        if (sensor == SENSOR_TYPE_CAR_SPEED) return 0f;
        return Float.NaN;
    }
}
