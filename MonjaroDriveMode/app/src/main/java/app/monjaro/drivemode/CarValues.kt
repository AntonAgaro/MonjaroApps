package app.monjaro.drivemode

import android.content.Context

/** Drive mode values from IDriveMode.DRIVE_MODE_SELECTION_* in the ECARX SDK. */
object DriveModes {
    /** Stored target value that means "do not change the drive mode". */
    const val OFF = 0

    /** IDriveMode.DRIVE_MODE_SELECTION_UNKNOWN */
    const val UNKNOWN = 255

    const val ECO = 570491137
    const val COMFORT = 570491138
    const val SPORT = 570491139 // DRIVE_MODE_SELECTION_DYNAMIC
    const val SNOW = 570491145
    const val MUD = 570491146
    const val ROCK = 570491147
    const val SAND = 570491149
    const val OFFROAD = 570491155
    const val SMART = 570491158 // DRIVE_MODE_SELECTION_ADAPTIVE
    const val CUSTOM = 570491200

    /** Shown when the car does not report its supported modes. Matches Monji's list. */
    val DEFAULT_CHOICES = listOf(SMART, COMFORT, SPORT, ECO, SNOW, OFFROAD, SAND)

    private val labels = mapOf(
        ECO to R.string.mode_eco,
        COMFORT to R.string.mode_comfort,
        SPORT to R.string.mode_sport,
        SNOW to R.string.mode_snow,
        MUD to R.string.mode_mud,
        ROCK to R.string.mode_rock,
        SAND to R.string.mode_sand,
        OFFROAD to R.string.mode_offroad,
        SMART to R.string.mode_smart,
        CUSTOM to R.string.mode_custom,
    )

    /** SDK names for modes that have no translated label. */
    private val codes = mapOf(
        570491140 to "XC",
        570491141 to "HDC",
        570491142 to "PURE",
        570491143 to "HYBRID",
        570491144 to "POWER",
        570491148 to "PHEV",
        570491150 to "AWD",
        570491151 to "SAVE",
        570491152 to "ECO_HEV_PHEV",
        570491153 to "NORMAL",
        570491154 to "eAWD",
        570491156 to "ECO_PLUS",
        570491157 to "SPORT_PLUS",
    )

    /** Values the car can be asked to switch to: only real drive mode IDs. */
    fun isSelectable(value: Int): Boolean = value in labels || value in codes

    fun label(context: Context, value: Int?): String = when (value) {
        null -> context.getString(R.string.value_unavailable)
        OFF -> context.getString(R.string.mode_off)
        UNKNOWN -> context.getString(R.string.mode_unknown)
        else -> labels[value]?.let { context.getString(it) } ?: codes[value] ?: value.toString()
    }
}

/** Values from ISensorEvent in the ECARX SDK. */
object CarStates {
    const val IGNITION_UNDEFINED = 2097409
    const val IGNITION_LOCK = 2097410
    const val IGNITION_OFF = 2097411
    const val IGNITION_ACC = 2097412
    const val IGNITION_ON = 2097413
    const val IGNITION_START = 2097414
    const val IGNITION_DRIVING = 2097415

    const val ENGINE_STOP = 2102785
    const val ENGINE_CRANKING = 2102786
    const val ENGINE_STOPPING = 2102787
    const val ENGINE_RUNNING = 2102788
    const val ENGINE_FAULT = 2102789

    /** Ignition states that mean the trip ended. The next engine start is a new trip. */
    val TRIP_END_IGNITION = setOf(IGNITION_LOCK, IGNITION_OFF, IGNITION_ACC)

    val IGNITION_KNOWN = (IGNITION_UNDEFINED..IGNITION_DRIVING).toSet()
    val ENGINE_KNOWN = (ENGINE_STOP..ENGINE_FAULT).toSet()

    fun ignitionName(value: Int?): String = when (value) {
        null -> "?"
        IGNITION_UNDEFINED -> "UNDEFINED"
        IGNITION_LOCK -> "LOCK"
        IGNITION_OFF -> "OFF"
        IGNITION_ACC -> "ACC"
        IGNITION_ON -> "ON"
        IGNITION_START -> "START"
        IGNITION_DRIVING -> "DRIVING"
        else -> value.toString()
    }

    fun engineName(value: Int?): String = when (value) {
        null -> "?"
        ENGINE_STOP -> "STOP"
        ENGINE_CRANKING -> "CRANKING"
        ENGINE_STOPPING -> "STOPPING"
        ENGINE_RUNNING -> "RUNNING"
        ENGINE_FAULT -> "FAULT"
        else -> value.toString()
    }
}
