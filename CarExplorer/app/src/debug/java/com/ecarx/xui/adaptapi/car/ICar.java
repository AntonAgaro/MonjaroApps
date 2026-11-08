package com.ecarx.xui.adaptapi.car;

import com.ecarx.xui.adaptapi.car.base.ICarFunction;
import com.ecarx.xui.adaptapi.car.sensor.ISensor;

/** Fake for the emulator (debug build only). */
public interface ICar {
    ICarFunction getICarFunction();

    ISensor getSensorManager();
}
