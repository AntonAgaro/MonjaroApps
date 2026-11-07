package app.monjaro.drivemode

import android.content.Context
import android.os.SystemClock
import android.util.Log
import java.lang.reflect.InvocationTargetException

/**
 * Access to the head unit's ECARX "adaptapi" through reflection.
 *
 * The ECARX classes are already on the head unit, so the app does not bundle them.
 * `Car.create()` runs `Authority.verifyAuthority()`, which rejects apps without a
 * system signature. Like Monji, we call the private `CarImpl(Context)` constructor
 * directly, which skips that check.
 *
 * Every call returns null/false on failure instead of throwing.
 */
object EcarxCar {
    private const val TAG = "DriveModeCar"

    private const val CAR_IMPL = "com.ecarx.xui.adaptapi.car.CarImpl"
    private const val I_CAR = "com.ecarx.xui.adaptapi.car.ICar"
    private const val I_CAR_FUNCTION = "com.ecarx.xui.adaptapi.car.base.ICarFunction"
    private const val I_SENSOR = "com.ecarx.xui.adaptapi.car.sensor.ISensor"

    /** IDriveMode.DM_FUNC_DRIVE_MODE_SELECT */
    const val FUNC_DRIVE_MODE = 570491136

    /** ISensor.SENSOR_TYPE_IGNITION_STATE (event sensor) */
    const val SENSOR_IGNITION = 2097408

    /** ISensor.SENSOR_TYPE_ENGINE_STATE (event sensor) */
    const val SENSOR_ENGINE = 2102784

    /** ISensor.SENSOR_TYPE_CAR_SPEED (value sensor) */
    const val SENSOR_SPEED = 1048832

    private const val RECONNECT_INTERVAL_MS = 10_000L

    @Volatile private var carFunction: Any? = null
    @Volatile private var sensor: Any? = null
    @Volatile private var lastConnectAttempt = 0L

    @Volatile var lastError: String? = null
        private set

    val isConnected: Boolean get() = carFunction != null

    /** Connects once. After a failure, it retries at most every 10 seconds. */
    @Synchronized
    fun connect(context: Context): Boolean {
        if (carFunction != null) return true
        val now = SystemClock.elapsedRealtime()
        if (lastConnectAttempt != 0L && now - lastConnectAttempt < RECONNECT_INTERVAL_MS) return false
        lastConnectAttempt = now

        return try {
            val constructor = Class.forName(CAR_IMPL).getDeclaredConstructor(Context::class.java)
            constructor.isAccessible = true
            val car = constructor.newInstance(context.applicationContext)
            carFunction = call(car, I_CAR, "getICarFunction")
            sensor = try {
                call(car, I_CAR, "getSensorManager")
            } catch (t: Throwable) {
                Log.w(TAG, "getSensorManager failed: ${describe(t)}")
                null
            }
            lastError = if (carFunction == null) "getICarFunction() returned null" else null
            Log.i(TAG, "connect: carFunction=$carFunction sensor=$sensor")
            carFunction != null
        } catch (t: Throwable) {
            lastError = describe(t)
            Log.e(TAG, "connect failed: $lastError", t)
            false
        }
    }

    fun getFunctionValue(funcId: Int): Int? {
        val target = carFunction ?: return null
        return try {
            call(target, I_CAR_FUNCTION, "getFunctionValue", arrayOf(Int::class.javaPrimitiveType!!), funcId) as? Int
        } catch (t: Throwable) {
            Log.w(TAG, "getFunctionValue($funcId): ${describe(t)}")
            null
        }
    }

    fun setFunctionValue(funcId: Int, value: Int): Boolean {
        val target = carFunction ?: return false
        return try {
            val int = Int::class.javaPrimitiveType!!
            val result = call(target, I_CAR_FUNCTION, "setFunctionValue", arrayOf(int, int), funcId, value)
            Log.i(TAG, "setFunctionValue($funcId, $value) -> $result")
            result as? Boolean ?: false
        } catch (t: Throwable) {
            Log.e(TAG, "setFunctionValue($funcId, $value): ${describe(t)}", t)
            false
        }
    }

    fun getSupportedValues(funcId: Int): IntArray? {
        val target = carFunction ?: return null
        return try {
            call(target, I_CAR_FUNCTION, "getSupportedFunctionValue", arrayOf(Int::class.javaPrimitiveType!!), funcId) as? IntArray
        } catch (t: Throwable) {
            Log.w(TAG, "getSupportedFunctionValue($funcId): ${describe(t)}")
            null
        }
    }

    fun getSensorEvent(sensorId: Int): Int? {
        val target = sensor ?: return null
        return try {
            call(target, I_SENSOR, "getSensorEvent", arrayOf(Int::class.javaPrimitiveType!!), sensorId) as? Int
        } catch (t: Throwable) {
            null
        }
    }

    fun getSensorValue(sensorId: Int): Float? {
        val target = sensor ?: return null
        return try {
            (call(target, I_SENSOR, "getSensorLatestValue", arrayOf(Int::class.javaPrimitiveType!!), sensorId) as? Float)
                ?.takeUnless { it.isNaN() }
        } catch (t: Throwable) {
            null
        }
    }

    /** Calls a method through its public interface, with a fallback to the object's own class. */
    private fun call(
        target: Any,
        interfaceName: String,
        methodName: String,
        types: Array<Class<*>> = emptyArray(),
        vararg args: Any?,
    ): Any? {
        val method = try {
            Class.forName(interfaceName).getMethod(methodName, *types)
        } catch (e: ReflectiveOperationException) {
            target.javaClass.getMethod(methodName, *types)
        }
        return try {
            method.invoke(target, *args)
        } catch (e: InvocationTargetException) {
            throw e.targetException ?: e
        }
    }

    private fun describe(t: Throwable): String {
        val cause = (t as? InvocationTargetException)?.targetException ?: t
        return "${cause.javaClass.simpleName}: ${cause.message}"
    }
}
