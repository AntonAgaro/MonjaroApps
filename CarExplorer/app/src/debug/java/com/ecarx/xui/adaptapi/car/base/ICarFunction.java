package com.ecarx.xui.adaptapi.car.base;

import com.ecarx.xui.adaptapi.FunctionStatus;

/** Fake for the emulator (debug build only). Only the methods Car Explorer calls. */
public interface ICarFunction {
    int COMMON_VALUE_OFF = 0;
    int COMMON_VALUE_ON = 1;
    int COMMON_VALUE_UNKNOWN = 255;
    int CAR_MODULE_DRIVE_MODE = 570425344;

    FunctionStatus isFunctionSupported(int function);

    FunctionStatus isFunctionSupported(int function, int zone);

    int[] getSupportedFunctionZones(int function);

    int getFunctionValue(int function);

    int getFunctionValue(int function, int zone);

    float getCustomizeFunctionValue(int function);

    float getCustomizeFunctionValue(int function, int zone);

    int[] getSupportedFunctionValue(int function);

    int[] getSupportedFunctionValue(int function, int zone);

    boolean setFunctionValue(int function, int value);

    boolean setFunctionValue(int function, int zone, int value);

    boolean setCustomizeFunctionValue(int function, float value);

    boolean setCustomizeFunctionValue(int function, int zone, float value);
}
