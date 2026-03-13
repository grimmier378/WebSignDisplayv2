package com.mattlabs.websigndisplay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.preference.PreferenceManager

/**
 * Receives the BOOT_COMPLETED broadcast and launches FullscreenActivity
 * if the "Auto Start on Boot" setting is enabled.
 *
 * KNOWN ISSUE: Auto-start is non-functional on the latest Fire OS release
 * due to Amazon platform restrictions introduced in a recent update.
 * Aggressive Restart (keeping the app in focus while running) is unaffected.
 * This is tracked as a known issue for a future release.
 *
 * Users can work around this by leaving the remote at the sign location —
 * once launched manually, Aggressive Restart keeps the app running.
 */
class BootUpReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Only handle BOOT_COMPLETED — ignore any other broadcasts
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val autoStartEnabled = prefs.getBoolean("autoStartToggle", false)

        if (autoStartEnabled) {
            context.startActivity(
                Intent(context, FullscreenActivity::class.java).apply {
                    // NEW_TASK is required when starting an Activity from a BroadcastReceiver
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }
}
