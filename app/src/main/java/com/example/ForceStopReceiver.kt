package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log

class ForceStopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.example.ACTION_FORCE_STOP") {
            Log.d("ForceStopReceiver", "Force-stop action clicked from notification.")
            
            // Set shared preferences flag indicating that we are initiating an auto force-stop
            val prefs = context.getSharedPreferences("stopper_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean("is_auto_kill_pending", true)
                .putLong("pending_time", System.currentTimeMillis())
                .apply()
            
            // Log for debugging
            Log.d("ForceStopReceiver", "Flag set, launching Settings App Details page...")

            try {
                // Launch standard Application Details settings page for com.opera.max.global
                val settingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:com.opera.max.global")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(settingsIntent)
            } catch (e: Exception) {
                Log.e("ForceStopReceiver", "Failed to launch Settings app info screen", e)
            }
        }
    }
}
