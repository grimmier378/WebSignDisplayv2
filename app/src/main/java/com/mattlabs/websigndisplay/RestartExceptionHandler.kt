package com.mattlabs.websigndisplay

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Catches uncaught exceptions and schedules an automatic app restart via AlarmManager.
 *
 * This ensures the sign display recovers from crashes without manual intervention —
 * essential for an unattended kiosk running 24/7.
 *
 * Usage: Call Thread.setDefaultUncaughtExceptionHandler(RestartExceptionHandler(this))
 * in FullscreenActivity.onCreate().
 */
class RestartExceptionHandler(private val context: Context) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        // Log the exception before restarting — readable via ADB logcat for field diagnostics
        Log.e("RestartExceptionHandler", "Uncaught exception — restarting app", throwable)

        // Build restart intent using the package launcher rather than hardcoding
        // FullscreenActivity::class.java — safer if the package is ever refactored.
        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?: run {
                // Cannot resolve launch intent — exit without scheduling a restart
                if (context is Activity) context.finish()
                System.exit(0)
                return
            }

        // FLAG_IMMUTABLE is required on API 31+ (Android 12 / Fire OS 8).
        // FLAG_ONE_SHOT ensures the alarm fires exactly once.
        val restartIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager?
        // Schedule restart 100ms from now. RTC_WAKEUP ensures the alarm fires even if the device is in a sleep/doze state.
        alarmManager?.set(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + 100,
            restartIntent
        )

        // Finish the current activity if the context is an Activity
        if (context is Activity) {
            context.finish()
        }

        // Force-stop the process so the AlarmManager restart launches a clean instance
        System.exit(0)
    }
}
