package app.monjaro.carexplorer

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Explorer state and the worker thread that talks to the car.
 *
 * Lives for the whole process, so a watch keeps running while the driver changes
 * settings in another app. The screen polls [version] and redraws when it changes.
 */
object Explorer {
    private const val TAG = "CarExplorer"
    private const val WATCH_INTERVAL_MS = 1_000L
    private const val MAX_CHANGES = 1_000
    private const val MAX_SPEED_FOR_WRITE = 3f
    private const val REPORT_DIR = "CarExplorer"

    /** What one function returned during the last scan. */
    data class Probe(
        val status: String?,
        val zones: List<Int>,
        val values: Map<Int?, Number>,
        val floatValue: Float?,
        val supported: List<Int>,
        val error: String?,
    ) {
        /** The car says the function exists (active or temporarily not active). */
        val isSupported: Boolean
            get() = when (status) {
                "active", "notactive" -> true
                null -> values.isNotEmpty()
                else -> false
            }
    }

    data class SensorProbe(val status: String?, val event: Int?, val value: Float?) {
        val isSupported: Boolean
            get() = status == "active" || status == "notactive" || (status == null && (event != null || value != null))
    }

    data class Change(val time: Long, val id: Int, val zone: Int?, val old: Number?, val new: Number?)

    @Volatile var catalog: Catalog? = null
        private set
    @Volatile var scanDone = 0
        private set
    @Volatile var scanTotal = 0
        private set
    @Volatile var watching = false
        private set
    @Volatile var version = 0
        private set

    val probes = ConcurrentHashMap<Int, Probe>()
    val sensorProbes = ConcurrentHashMap<Int, SensorProbe>()
    private val changeLog = ArrayDeque<Change>()

    private val handler: Handler by lazy { Handler(HandlerThread("car").apply { start() }.looper) }
    private lateinit var appContext: Context

    val isScanning: Boolean get() = scanTotal > 0 && scanDone < scanTotal

    fun changes(): List<Change> = synchronized(changeLog) { changeLog.toList() }

    /** Connects, reads the names and runs the first scan. Safe to call many times. */
    fun start(context: Context) {
        appContext = context.applicationContext
        handler.post {
            if (catalog == null) catalog = Catalog.load().also { Log.i(TAG, "catalog: ${it.functions.size} functions, ${it.sensors.size} sensors, missing ${it.missingClasses}") }
            if (EcarxCar.connect(appContext) && probes.isEmpty()) scanNow()
            bump()
        }
    }

    fun rescan() = handler.post {
        if (EcarxCar.connect(appContext)) scanNow()
        bump()
    }

    fun setWatching(on: Boolean) {
        watching = on
        bump()
        if (on) handler.post(watchTick)
    }

    /** Reads one function again (also any id typed by hand). */
    fun probe(id: Int, done: () -> Unit) = handler.post {
        if (EcarxCar.connect(appContext)) probes[id] = probeFunction(id)
        bump()
        done()
    }

    /** Writes a value, then reads the function again. [done] gets a short result text. */
    fun write(id: Int, zone: Int?, value: Number, done: (String) -> Unit) = handler.post {
        val speed = EcarxCar.sensorValue(EcarxCar.SENSOR_SPEED).getOrNull()
        val result = when {
            speed != null && speed > MAX_SPEED_FOR_WRITE -> "speed"
            value is Float -> EcarxCar.setFloatValue(id, zone, value).describe()
            else -> EcarxCar.setIntValue(id, zone, value.toInt()).describe()
        }
        Log.i(TAG, "write ${name(id)} zone=$zone value=$value -> $result")
        handler.postDelayed({
            val before = probes[id]
            val after = probeFunction(id)
            probes[id] = after
            logDiff(id, before?.values.orEmpty(), after.values)
            bump()
            done(result)
        }, 500L)
    }

    /** Saves everything to a text file and returns its path. */
    fun saveReport(done: (Result<String>) -> Unit) = handler.post {
        done(runCatching { writeReport() })
    }

    // ---- worker ----

    private fun scanNow() {
        val cat = catalog ?: return
        scanDone = 0
        scanTotal = cat.functions.size + cat.sensors.size
        for (item in cat.functions) {
            probes[item.id] = probeFunction(item.id)
            scanDone++
            if (scanDone % 25 == 0) bump()
        }
        for (item in cat.sensors) {
            sensorProbes[item.id] = probeSensor(item.id)
            scanDone++
        }
        Log.i(TAG, "scan: ${probes.values.count { it.isSupported }} of ${probes.size} functions supported, " +
            "${sensorProbes.values.count { it.isSupported }} of ${sensorProbes.size} sensors")
    }

    private fun probeFunction(id: Int): Probe {
        val status = EcarxCar.functionStatus(id).getOrNull()
        val zones = EcarxCar.zones(id).getOrNull()?.toList().orEmpty()
        val values = LinkedHashMap<Int?, Number>()
        var error: String? = null
        for (zone in zones.ifEmpty { listOf(null) }) {
            EcarxCar.intValue(id, zone)
                .onSuccess { values[zone] = it }
                .onFailure { error = EcarxCar.describe(it) }
        }
        val floatValue = EcarxCar.floatValue(id, zones.firstOrNull()).getOrNull()?.takeUnless { it.isNaN() }
        val supported = EcarxCar.supportedValues(id, zones.firstOrNull()).getOrNull()?.toList().orEmpty()
        return Probe(status, zones, values, floatValue, supported, error)
    }

    private fun probeSensor(id: Int) = SensorProbe(
        status = EcarxCar.sensorStatus(id).getOrNull(),
        event = EcarxCar.sensorEvent(id).getOrNull(),
        value = EcarxCar.sensorValue(id).getOrNull()?.takeUnless { it.isNaN() },
    )

    /** Every second: read the values of all supported functions and sensors, log what changed. */
    private val watchTick = object : Runnable {
        override fun run() {
            handler.removeCallbacks(this)
            if (!watching) return
            if (EcarxCar.isConnected) {
                var changed = false
                for ((id, probe) in probes) {
                    if (!probe.isSupported) continue
                    val now = LinkedHashMap<Int?, Number>()
                    for (zone in probe.zones.ifEmpty { listOf(null) }) {
                        EcarxCar.intValue(id, zone).onSuccess { now[zone] = it }
                    }
                    if (now != probe.values) {
                        logDiff(id, probe.values, now)
                        probes[id] = probe.copy(values = now)
                        changed = true
                    }
                }
                for ((id, probe) in sensorProbes) {
                    if (!probe.isSupported) continue
                    val now = probeSensor(id)
                    if (now.event != probe.event) {
                        addChange(Change(System.currentTimeMillis(), id, null, probe.event, now.event))
                    }
                    if (now != probe) {
                        sensorProbes[id] = now
                        changed = true
                    }
                }
                if (changed) bump()
            }
            handler.postDelayed(this, WATCH_INTERVAL_MS)
        }
    }

    private fun logDiff(id: Int, old: Map<Int?, Number>, new: Map<Int?, Number>) {
        val time = System.currentTimeMillis()
        for (zone in old.keys + new.keys.filter { it !in old.keys }) {
            if (old[zone] != new[zone]) addChange(Change(time, id, zone, old[zone], new[zone]))
        }
    }

    private fun addChange(change: Change) {
        val cat = catalog
        Log.i(TAG, "change: ${name(change.id)} [${cat?.zoneLabel(change.zone)}] " +
            "${cat?.valueLabel(change.old)} -> ${cat?.valueLabel(change.new)}")
        synchronized(changeLog) {
            changeLog.addFirst(change)
            while (changeLog.size > MAX_CHANGES) changeLog.removeLast()
        }
    }

    private fun writeReport(): String {
        val time = Date()
        val name = "car_explorer_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(time)}.txt"
        val path = saveText(name, buildReport(time))
        Log.i(TAG, "report saved: $path")
        return path
    }

    /**
     * Android 10+: public Download/CarExplorer folder (no permission needed, and adb can read it;
     * adb cannot read the app's own Android/data folder on Android 11).
     */
    private fun saveText(name: String, text: String): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$REPORT_DIR")
            }
            val resolver = appContext.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("cannot create $name")
            resolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } ?: error("cannot write $name")
            // Only used to show the path to the user (for `adb pull`).
            @Suppress("DEPRECATION")
            val root = Environment.getExternalStorageDirectory().path
            return "$root/${Environment.DIRECTORY_DOWNLOADS}/$REPORT_DIR/$name"
        }
        val file = File(appContext.getExternalFilesDir(null) ?: appContext.filesDir, name)
        file.writeText(text)
        return file.absolutePath
    }

    private fun buildReport(time: Date): String {
        val cat = catalog ?: error("no catalog")
        val clock = SimpleDateFormat("HH:mm:ss", Locale.US)
        return buildString {
            val out = this
            out.appendLine("Car Explorer report ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(time)}")
            out.appendLine("Head unit: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}, build ${Build.DISPLAY}")
            out.appendLine("Connection: ${if (EcarxCar.isConnected) "ok" else EcarxCar.lastError}")
            out.appendLine("ECARX classes found: ${cat.loadedClasses.size}, missing: ${cat.missingClasses}")
            out.appendLine()

            val (supported, other) = cat.functions.partition { probes[it.id]?.isSupported == true }
            out.appendLine("== SUPPORTED FUNCTIONS (${supported.size}) ==")
            out.appendLine("name\tid\thex\tgroup\tstatus\tvalues\tfloat\tsupported values")
            for (item in supported) {
                val p = probes.getValue(item.id)
                val values = p.values.entries.joinToString("; ") { (zone, v) ->
                    if (zone == null) cat.valueLabel(v) else "${cat.zoneLabel(zone)}=${cat.valueLabel(v)}"
                }
                out.appendLine(listOf(
                    item.name, item.id, hex(item.id), item.group, p.status, values,
                    p.floatValue ?: "", p.supported.joinToString(", ") { cat.valueLabel(it) },
                ).joinToString("\t"))
            }
            out.appendLine()

            out.appendLine("== SENSORS ==")
            out.appendLine("name\tid\tstatus\tevent\tvalue")
            for (item in cat.sensors) {
                val p = sensorProbes[item.id] ?: continue
                out.appendLine(listOf(item.name, item.id, p.status, cat.valueLabel(p.event), p.value ?: "").joinToString("\t"))
            }
            out.appendLine()

            out.appendLine("== CHANGES (newest first) ==")
            for (c in changes()) {
                out.appendLine("${clock.format(Date(c.time))}\t${name(c.id)}\t${cat.zoneLabel(c.zone)}\t" +
                    "${cat.valueLabel(c.old)} -> ${cat.valueLabel(c.new)}")
            }
            out.appendLine()

            out.appendLine("== NOT SUPPORTED FUNCTIONS (${other.size}) ==")
            for (item in other) out.appendLine("${item.name}\t${item.id}\t${probes[item.id]?.status ?: "-"}")
        }
    }

    fun name(id: Int): String = catalog?.item(id)?.name ?: "ID $id"

    fun hex(id: Int): String = "0x%08X".format(Locale.US, id)

    private fun Result<Boolean>.describe(): String =
        fold({ if (it) "accepted" else "rejected" }, { "error: ${EcarxCar.describe(it)}" })

    private fun bump() {
        version++
    }
}
