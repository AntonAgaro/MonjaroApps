package app.monjaro.drivemode

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** Starts the service when the head unit boots or the app is updated. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, "android.intent.action.QUICKBOOT_POWERON" -> {
                // A head unit boot means a new trip.
                Store.setArmed(context, true)
                Log.i("DriveModeAuto", "Head unit started. Ready for engine start.")
            }
            Intent.ACTION_MY_PACKAGE_REPLACED -> Unit
            else -> return
        }
        DriveModeService.start(context)
    }
}
