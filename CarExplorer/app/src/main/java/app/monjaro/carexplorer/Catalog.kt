package app.monjaro.carexplorer

import java.lang.reflect.Modifier

/**
 * Names of car functions, sensors, values and zones, read at runtime from the ECARX
 * interfaces on the head unit. Nothing from the SDK is bundled in the APK, and the names
 * always match the ECARX version installed on this car.
 *
 * ECARX numbering: a value constant is its function (or sensor) id plus a small number,
 * so most values are named by matching them against all known constants.
 */
class Catalog private constructor(
    val functions: List<Item>,
    val sensors: List<Item>,
    private val valueNames: Map<Int, String>,
    private val zoneNames: Map<Int, String>,
    val loadedClasses: List<String>,
    val missingClasses: List<String>,
) {
    data class Item(val id: Int, val name: String, val group: String)

    private val byId: Map<Int, Item> = (functions + sensors).associateBy { it.id }

    fun item(id: Int): Item? = byId[id]

    /** For example "DRIVE_MODE_SELECTION_DYNAMIC (570491139)" or "ON (1)". */
    fun valueLabel(value: Number?): String = when (value) {
        null -> "—"
        is Float -> value.toString()
        else -> {
            val int = value.toInt()
            val name = if (int in 0..255) COMMON_VALUES[int] else valueNames[int]
            if (name == null) int.toString() else "$name ($int)"
        }
    }

    fun zoneLabel(zone: Int?): String = when (zone) {
        null -> "global"
        else -> zoneNames[zone]?.removePrefix("ZONE_") ?: "zone $zone"
    }

    companion object {
        private const val ROOT = "com.ecarx.xui.adaptapi"

        private val FUNCTION_CLASSES = listOf(
            "car.base.ICarFunction",
            "car.vehicle.IVehicle",
            "car.vehicle.IDriveMode",
            "car.vehicle.IADAS",
            "car.vehicle.ISafety",
            "car.vehicle.IBcm",
            "car.vehicle.ILamp",
            "car.vehicle.IAmbienceLight",
            "car.vehicle.ISeat",
            "car.vehicle.IAudio",
            "car.vehicle.IHUD",
            "car.vehicle.IPAS",
            "car.vehicle.IHybrid",
            "car.vehicle.IDayMode",
            "car.vehicle.ISceneMode",
            "car.vehicle.IWpc",
            "car.hvac.IHvac",
            "car.hvac.IFragrance",
            "car.hev.ICharging",
            "car.hev.ITripData",
            "car.userprofile.Profile",
            "car.vendor.IVendorFuncs",
            "diminteraction.IClimateInteraction",
            "dvr.forp.ISettings",
        )
        private val SENSOR_CLASSES = listOf("car.sensor.ISensor", "car.sensor.ISensorEvent")
        private const val ZONE_CLASS = "vehicle.VehicleZone"

        private val COMMON_VALUES = mapOf(0 to "OFF", 1 to "ON", 253 to "ERROR", 254 to "NONE", 255 to "UNKNOWN")

        fun load(): Catalog {
            val functions = LinkedHashMap<Int, Item>()
            val sensors = LinkedHashMap<Int, Item>()
            val values = HashMap<Int, String>()
            val zones = HashMap<Int, String>()
            val loaded = mutableListOf<String>()
            val missing = mutableListOf<String>()

            for (name in FUNCTION_CLASSES + SENSOR_CLASSES + ZONE_CLASS) {
                val constants = intConstants("$ROOT.$name")
                if (constants == null) {
                    missing += name
                    continue
                }
                loaded += name
                val group = name.substringAfterLast('.')
                for ((constName, value) in constants) {
                    when {
                        name == ZONE_CLASS -> zones.putIfAbsent(value, constName)
                        "_FUNC_" in constName -> functions.putIfAbsent(value, Item(value, constName, group))
                        constName.startsWith("SENSOR_TYPE_") -> sensors.putIfAbsent(value, Item(value, constName, group))
                        value !in 0..255 -> values.putIfAbsent(value, constName)
                    }
                }
            }
            return Catalog(
                functions = functions.values.sortedBy { it.name },
                sensors = sensors.values.sortedBy { it.name },
                valueNames = values,
                zoneNames = zones,
                loadedClasses = loaded,
                missingClasses = missing,
            )
        }

        /** All `public static final int` fields of a class, or null if the class is not on this device. */
        private fun intConstants(className: String): List<Pair<String, Int>>? {
            val cls = try {
                Class.forName(className)
            } catch (t: Throwable) {
                return null
            }
            return cls.fields
                .filter { Modifier.isStatic(it.modifiers) && it.type == Int::class.javaPrimitiveType }
                .mapNotNull { field -> runCatching { field.name to field.getInt(null) }.getOrNull() }
        }
    }
}
