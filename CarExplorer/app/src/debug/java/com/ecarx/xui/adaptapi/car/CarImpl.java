package com.ecarx.xui.adaptapi.car;

import android.content.Context;

import com.ecarx.xui.adaptapi.car.base.ICarFunction;
import com.ecarx.xui.adaptapi.car.sensor.ISensor;

/** Fake for the emulator (debug build only). Private constructor, like the real one. */
public class CarImpl implements ICar {
    private CarImpl(Context context) {
    }

    @Override
    public ICarFunction getICarFunction() {
        return FakeCar.INSTANCE;
    }

    @Override
    public ISensor getSensorManager() {
        return FakeCar.INSTANCE;
    }
}
