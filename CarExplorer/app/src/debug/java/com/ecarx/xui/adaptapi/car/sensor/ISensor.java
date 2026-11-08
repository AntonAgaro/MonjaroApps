package com.ecarx.xui.adaptapi.car.sensor;

import com.ecarx.xui.adaptapi.FunctionStatus;

/** Fake for the emulator (debug build only). */
public interface ISensor {
    int SENSOR_TYPE_CAR_SPEED = 1048832;
    int SENSOR_TYPE_IGNITION_STATE = 2097408;
    int SENSOR_TYPE_ENGINE_STATE = 2102784;
    int SENSOR_TYPE_GEAR = 2097664;

    FunctionStatus isSensorSupported(int sensor);

    int getSensorEvent(int sensor);

    float getSensorLatestValue(int sensor);
}
