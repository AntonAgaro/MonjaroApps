package app.monjaro.drivemode

import android.content.Context
import android.content.SharedPreferences

/** Saved settings. */
object Store {
    private const val PREFS = "drive_mode"
    private const val KEY_TARGET = "target_mode"
    private const val KEY_ARMED = "armed"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The mode to set after engine start, or [DriveModes.OFF]. */
    fun targetMode(context: Context): Int = prefs(context).getInt(KEY_TARGET, DriveModes.OFF)

    fun setTargetMode(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_TARGET, value).apply()
    }

    /**
     * True when a new trip started and the mode was not set yet.
     * It is saved, so a service restart in the middle of a trip does not set the mode again.
     */
    fun isArmed(context: Context): Boolean = prefs(context).getBoolean(KEY_ARMED, false)

    fun setArmed(context: Context, armed: Boolean) {
        prefs(context).edit().putBoolean(KEY_ARMED, armed).apply()
    }
}
