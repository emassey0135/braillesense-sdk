package dev.emassey0135.braillesense.session

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * An app's own activity brought to the front from the background, as the BrailleSense's Schedule
 * Manager opens itself as a reminder goes off: for what cannot wait for the person to come back to
 * the app, such as an alarm or a reminder. Android starts no activity for an app in the background
 * unless it may display over other apps (`SYSTEM_ALERT_WINDOW`, granted on Android's "Display over
 * other apps" page), which the Schedule Manager holds; root is never needed.
 *
 * A notification alone only sounds and vibrates on this device, and is read only once the person
 * opens the notification list, so what comes to the front should then be said as the device's own
 * dialogs are, with its buttons, a notification beside it giving the sound and vibration once.
 */
public object Foreground {
    /** Whether the app may come to the front from the background: "Display over other apps" allowed. */
    public fun allowed(context: Context): Boolean = Settings.canDrawOverlays(context)

    /** Android's page where the person allows the app to display over other apps, and so to come to the front. */
    public fun settings(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Wakes the device and starts [activity], which must be this app's, in front, with [extras] as
     * its intent's string extras; a `singleTask` activity already running gets them in `onNewIntent`.
     * False, starting nothing, where the app may not come to the front ([allowed]).
     */
    public fun open(context: Context, activity: Class<out Activity>, extras: Map<String, String> = emptyMap()): Boolean {
        if (!allowed(context)) return false
        wake(context)
        val intent = Intent(context, activity).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        extras.forEach { (key, value) -> intent.putExtra(key, value) }
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    /**
     * Wakes the device from sleep for a moment and lets it sleep again by its own timeout: its speech
     * and cells are silent while it sleeps. Its deeper sleep, which turns Wi-Fi off, it wakes from too.
     */
    @Suppress("DEPRECATION") // ACQUIRE_CAUSES_WAKEUP is what wakes the device from the background.
    public fun wake(context: Context) {
        val power = context.getSystemService(PowerManager::class.java) ?: return
        if (power.isInteractive) return
        runCatching {
            power.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE, WAKE_TAG)
                .acquire(WAKE_MS)
        }
    }

    private const val WAKE_TAG = "braillesense-sdk:foreground"
    private const val WAKE_MS = 3_000L
}
