package app.monjaro.carexplorer

import android.content.Context
import android.os.SystemClock
import android.util.Log
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * Access to the head unit's ECARX "adaptapi" through reflection.
 *
 * The ECARX classes are already on the head unit, so the app does not bundle them.
 * `Car.create()` runs `Authority.verifyAuthority()`, which rejects apps without a
 * system signature. Like Monji, we call the private `CarImpl(Context)` constructor
 * directly, which skips that check.
 *
 * Every call returns a [Result]: a failure means the car (or this head unit's ECARX
 * version) does not support that call.
 */
object EcarxCar {
    private const val TAG = "CarExplorer"

    private const val CAR_IMPL = "com.ecarx.xui.adaptapi.car.CarImpl"
    private const val I_CAR = "com.ecarx.xui.adaptapi.car.ICar"
    private const val I_CAR_FUNCTION = "com.ecarx.xui.adaptapi.car.base.ICarFunction"
    private const val I_SENSOR = "com.ecarx.xui.adaptapi.car.sensor.ISensor"

    /** ISensor.SENSOR_TYPE_CAR_SPEED (value sensor) */
    const val SENSOR_SPEED = 1048832

    private const val RECONNECT_INTERVAL_MS = 5_000L

    private val INT = Int::class.javaPrimitiveType!!
    private val FLOAT = Float::class.javaPrimitiveType!!

    @Volatile private var carFunction: Any? = null
    @Volatile private var sensor: Any? = null
    @Volatile private var lastConnectAttempt = 0L
    private val methods = ConcurrentHashMap<String, Method>()

    @Volatile var lastError: String? = null
        private set

    val isConnected: Boolean get() = carFunction != null

    /** Connects once. After a failure, it retries at most every 5 seconds. */
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
            sensor = runCatching { call(car, I_CAR, "getSensorManager") }
                .onFailure { Log.w(TAG, "getSensorManager failed: ${describe(it)}") }
                .getOrNull()
            lastError = if (carFunction == null) "getICarFunction() returned null" else null
            Log.i(TAG, "connect: carFunction=$carFunction sensor=$sensor")
            carFunction != null
        } catch (t: Throwable) {
            lastError = describe(t)
            Log.e(TAG, "connect failed: $lastError")
            false
        }
    }

    // ---- ICarFunction ----

    /** `isFunctionSupported`: "active", "notactive", "notavailable" or "error". */
    fun functionStatus(funcId: Int, zone: Int? = null): Result<String> = function { f ->
        if (zone == null) call(f, I_CAR_FUNCTION, "isFunctionSupported", arrayOf(INT), funcId)
        else call(f, I_CAR_FUNCTION, "isFunctionSupported", arrayOf(INT, INT), funcId, zone)
    }.mapCatching { it.toString() }

    fun zones(funcId: Int): Result<IntArray> = function { f ->
        call(f, I_CAR_FUNCTION, "getSupportedFunctionZones", arrayOf(INT), funcId)
    }.mapCatching { it as? IntArray ?: IntArray(0) }

    fun intValue(funcId: Int, zone: Int? = null): Result<Int> = function { f ->
        if (zone == null) call(f, I_CAR_FUNCTION, "getFunctionValue", arrayOf(INT), funcId)
        else call(f, I_CAR_FUNCTION, "getFunctionValue", arrayOf(INT, INT), funcId, zone)
    }.mapCatching { it as Int }

    fun floatValue(funcId: Int, zone: Int? = null): Result<Float> = function { f ->
        if (zone == null) call(f, I_CAR_FUNCTION, "getCustomizeFunctionValue", arrayOf(INT), funcId)
        else call(f, I_CAR_FUNCTION, "getCustomizeFunctionValue", arrayOf(INT, INT), funcId, zone)
    }.mapCatching { it as Float }

    fun supportedValues(funcId: Int, zone: Int? = null): Result<IntArray> = function { f ->
        if (zone == null) call(f, I_CAR_FUNCTION, "getSupportedFunctionValue", arrayOf(INT), funcId)
        else call(f, I_CAR_FUNCTION, "getSupportedFunctionValue", arrayOf(INT, INT), funcId, zone)
    }.mapCatching { it as? IntArray ?: IntArray(0) }

    fun setIntValue(funcId: Int, zone: Int?, value: Int): Result<Boolean> = function { f ->
        if (zone == null) call(f, I_CAR_FUNCTION, "setFunctionValue", arrayOf(INT, INT), funcId, value)
        else call(f, I_CAR_FUNCTION, "setFunctionValue", arrayOf(INT, INT, INT), funcId, zone, value)
    }.mapCatching { it as? Boolean ?: false }
        .also { Log.i(TAG, "setFunctionValue($funcId, zone=$zone, $value) -> ${it.describe()}") }

    fun setFloatValue(funcId: Int, zone: Int?, value: Float): Result<Boolean> = function { f ->
        if (zone == null) call(f, I_CAR_FUNCTION, "setCustomizeFunctionValue", arrayOf(INT, FLOAT), funcId, value)
        else call(f, I_CAR_FUNCTION, "setCustomizeFunctionValue", arrayOf(INT, INT, FLOAT), funcId, zone, value)
    }.mapCatching { it as? Boolean ?: false }
        .also { Log.i(TAG, "setCustomizeFunctionValue($funcId, zone=$zone, $value) -> ${it.describe()}") }

    // ---- ISensor ----

    fun sensorStatus(sensorId: Int): Result<String> = sensor { s ->
        call(s, I_SENSOR, "isSensorSupported", arrayOf(INT), sensorId)
    }.mapCatching { it.toString() }

    fun sensorEvent(sensorId: Int): Result<Int> = sensor { s ->
        call(s, I_SENSOR, "getSensorEvent", arrayOf(INT), sensorId)
    }.mapCatching { it as Int }

    fun sensorValue(sensorId: Int): Result<Float> = sensor { s ->
        call(s, I_SENSOR, "getSensorLatestValue", arrayOf(INT), sensorId)
    }.mapCatching { it as Float }

    // ---- helpers ----

    private inline fun function(block: (Any) -> Any?): Result<Any?> {
        val target = carFunction ?: return Result.failure(IllegalStateException("not connected"))
        return runCatching { block(target) }
    }

    private inline fun sensor(block: (Any) -> Any?): Result<Any?> {
        val target = sensor ?: return Result.failure(IllegalStateException("no sensor manager"))
        return runCatching { block(target) }
    }

    /** Calls a method through its public interface, with a fallback to the object's own class. */
    private fun call(
        target: Any,
        interfaceName: String,
        methodName: String,
        types: Array<Class<*>> = emptyArray(),
        vararg args: Any?,
    ): Any? {
        val key = "$interfaceName#$methodName#${types.size}"
        val method = methods.getOrPut(key) {
            try {
                Class.forName(interfaceName).getMethod(methodName, *types)
            } catch (e: ReflectiveOperationException) {
                target.javaClass.getMethod(methodName, *types)
            }
        }
        return try {
            method.invoke(target, *args)
        } catch (e: InvocationTargetException) {
            throw e.targetException ?: e
        }
    }

    fun describe(t: Throwable?): String {
        val cause = (t as? InvocationTargetException)?.targetException ?: t ?: return "unknown error"
        return listOfNotNull(cause.javaClass.simpleName, cause.message).joinToString(": ")
    }

    private fun Result<Boolean>.describe(): String =
        fold({ if (it) "accepted" else "rejected" }, { "error: ${describe(it)}" })
}
