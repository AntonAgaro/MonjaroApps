package app.monjaro.drivemode

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import android.util.Log

/**
 * Sets the chosen drive mode one time after each engine start, then does nothing until the next start.
 *
 *  - "Armed" (a new trip) is set when the head unit boots, when ignition goes OFF/ACC/LOCK,
 *    or when the head unit wakes up after deep sleep.
 *  - When armed and the engine is running, the service sets the mode once and disarms.
 *    There is no delay and no second attempt, so a manual change is never undone.
 *
 * Engine auto start-stop does not start a new trip, because the ignition stays ON.
 */
class DriveModeService : Service() {

    /** Live values for the screen. */
    data class Status(
        val connected: Boolean = false,
        val error: String? = null,
        val currentMode: Int? = null,
        val supportedModes: List<Int> = emptyList(),
    )

    private lateinit var thread: HandlerThread
    private lateinit var handler: Handler

    // Fields below are used only on the handler thread.
    private var connectedAt = 0L
    private var lastElapsed = 0L
    private var lastUptime = 0L
    private var lastIgnition: Int? = null
    private var lastEngine: Int? = null
    private var runningSince: Long? = null
    private var supportedModes: List<Int> = emptyList()
    private var noSensorsLogged = false
    private var lastNotificationText: String? = null

    private val tick = object : Runnable {
        override fun run() {
            try {
                onTick()
            } catch (t: Throwable) {
                Log.e(TAG, "tick failed", t)
            }
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.notification_starting)))
        thread = HandlerThread("drive-mode").apply { start() }
        handler = Handler(thread.looper)
        handler.post(tick)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        thread.quitSafely()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun onTick() {
        val now = SystemClock.elapsedRealtime()
        detectDeepSleep(now)

        if (!EcarxCar.connect(this)) {
            publish(Status(error = EcarxCar.lastError))
            return
        }
        if (connectedAt == 0L) connectedAt = now
        if (supportedModes.isEmpty()) {
            supportedModes = EcarxCar.getSupportedValues(EcarxCar.FUNC_DRIVE_MODE)
                ?.filter { DriveModes.isSelectable(it) }
                .orEmpty()
            if (supportedModes.isNotEmpty()) log("Car supports modes: $supportedModes")
        }

        val ignition = EcarxCar.getSensorEvent(EcarxCar.SENSOR_IGNITION)?.takeIf { it in CarStates.IGNITION_KNOWN }
        val engine = EcarxCar.getSensorEvent(EcarxCar.SENSOR_ENGINE)?.takeIf { it in CarStates.ENGINE_KNOWN }
        val currentMode = EcarxCar.getFunctionValue(EcarxCar.FUNC_DRIVE_MODE)

        if (ignition != lastIgnition) {
            log("Ignition: ${CarStates.ignitionName(lastIgnition)} -> ${CarStates.ignitionName(ignition)}")
            lastIgnition = ignition
            if (ignition in CarStates.TRIP_END_IGNITION) arm("ignition ${CarStates.ignitionName(ignition)}")
        }
        if (engine != lastEngine) {
            log("Engine: ${CarStates.engineName(lastEngine)} -> ${CarStates.engineName(engine)}")
            lastEngine = engine
        }

        if (Store.isArmed(this) && isEngineRunning(now, ignition, engine)) {
            applyOnce(now, currentMode)
        } else {
            runningSince = null
        }

        publish(Status(connected = true, currentMode = currentMode, supportedModes = supportedModes))
    }

    /** uptimeMillis() stops in deep sleep and elapsedRealtime() does not. A big difference means the unit slept. */
    private fun detectDeepSleep(now: Long) {
        val uptime = SystemClock.uptimeMillis()
        if (lastElapsed != 0L) {
            val slept = (now - lastElapsed) - (uptime - lastUptime)
            if (slept > SLEEP_GAP_MS) arm("head unit woke up after ${slept / 1000} s of sleep")
        }
        lastElapsed = now
        lastUptime = uptime
    }

    private fun arm(reason: String) {
        if (!Store.isArmed(this)) log("Ready for next engine start ($reason)")
        Store.setArmed(this, true)
        runningSince = null
    }

    private fun isEngineRunning(now: Long, ignition: Int?, engine: Int?): Boolean = when {
        engine != null -> engine == CarStates.ENGINE_RUNNING || ignition == CarStates.IGNITION_DRIVING
        ignition != null -> ignition == CarStates.IGNITION_ON || ignition == CarStates.IGNITION_DRIVING
        // Sensors often report nothing in the first seconds after connecting.
        now - connectedAt < SENSOR_GRACE_MS -> false
        else -> {
            // No ignition or engine data at all: fall back to "head unit started".
            if (!noSensorsLogged) {
                log("Ignition and engine state are not available. Using head unit start.")
                noSensorsLogged = true
            }
            true
        }
    }

    /** Sets the mode one time for this trip. Never repeats, so a manual change is kept. */
    private fun applyOnce(now: Long, current: Int?) {
        val since = runningSince ?: now.also { runningSince = it }

        // Right after start the car may not report a mode yet. The set call needs a ready car.
        if (current == null || !DriveModes.isSelectable(current)) {
            if (now - since > NOT_READY_LIMIT_MS) {
                Store.setArmed(this, false)
                log("Drive mode is not available after engine start. Skipped for this trip.")
            }
            return
        }

        Store.setArmed(this, false)
        runningSince = null

        val target = Store.targetMode(this)
        if (target == DriveModes.OFF) {
            log("Engine started. Start mode is off.")
            return
        }
        val speed = EcarxCar.getSensorValue(EcarxCar.SENSOR_SPEED)
        if (speed != null && speed > MAX_SPEED) {
            log("Engine started, but the car is moving (speed $speed). Mode not changed.")
            return
        }
        if (current == target) {
            log("Engine started. Mode $target is already active.")
            return
        }
        val ok = EcarxCar.setFunctionValue(EcarxCar.FUNC_DRIVE_MODE, target)
        log("Engine started. Set mode $current -> $target: ${if (ok) "accepted" else "rejected"}")
    }

    private fun log(message: String) = Log.i(TAG, message)

    private fun publish(status: Status) {
        Companion.status = status
        val text = if (status.connected) {
            getString(R.string.notification_status, DriveModes.label(this, Store.targetMode(this)))
        } else {
            getString(R.string.notification_no_sdk, status.error ?: "?")
        }
        if (text != lastNotificationText) {
            lastNotificationText = text
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text))
        }
    }

    private fun buildNotification(text: String): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) },
        )
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "DriveModeAuto"
        private const val CHANNEL_ID = "drive_mode"
        private const val NOTIFICATION_ID = 7301

        private const val TICK_MS = 1_000L

        /** How long to wait for ignition/engine data before using the "head unit started" fallback. */
        private const val SENSOR_GRACE_MS = 20_000L

        /** If the car does not report a drive mode this long after engine start, skip this trip. */
        private const val NOT_READY_LIMIT_MS = 30_000L

        /** Deep sleep longer than this starts a new trip. */
        private const val SLEEP_GAP_MS = 15_000L

        /** Do not change the mode if the car is already moving. */
        private const val MAX_SPEED = 3f

        @Volatile
        var status: Status = Status()
            private set

        fun start(context: Context) {
            context.startForegroundService(Intent(context, DriveModeService::class.java))
        }
    }
}
