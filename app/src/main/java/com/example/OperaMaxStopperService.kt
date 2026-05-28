package com.example

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import java.util.ArrayDeque

class OperaMaxStopperService : AccessibilityService() {

    private var lastPackage: String = ""
    private val CHANNEL_ID = "opera_max_stopper_channel"
    private val NOTIFICATION_ID = 81372

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        val eventType = event.eventType

        // 1. Detect when com.opera.max.global is opened
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (packageName == "com.opera.max.global" && lastPackage != "com.opera.max.global") {
                Log.d("OperaMaxStopperService", "com.opera.max.global has been launched!")
                showForceStopNotification()
            }
            if (packageName != "com.opera.max.global") {
                lastPackage = packageName
            }
        }

        // 2. Automate force stop click when settings page or confirmation dialog is displayed
        val prefs = getSharedPreferences("stopper_prefs", Context.MODE_PRIVATE)
        val isPending = prefs.getBoolean("is_auto_kill_pending", false)
        val pendingTime = prefs.getLong("pending_time", 0L)

        // Check if a force stop request is pending within a sensible time window (15 seconds)
        if (isPending && (System.currentTimeMillis() - pendingTime < 15000)) {
            // Traverse active window to find "Force stop" button or confirmation "OK" button
            val rootNode = rootInActiveWindow ?: return
            
            // Try to find "Force stop" button first (or its local equivalents)
            val stopButton = findNodeByText(rootNode, listOf(
                "force stop", 
                "force_stop", 
                "принудительно остановить", 
                "остановить принудительно", 
                "завершить принудительно", 
                "завершить", 
                "остановить",
                "force close"
            ))

            if (stopButton != null) {
                if (stopButton.isEnabled) {
                    Log.d("OperaMaxStopperService", "Found enabled 'Force stop' button! Clicked.")
                    stopButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                } else {
                    Log.d("OperaMaxStopperService", "Found 'Force stop' button, but it is disabled (already stopped). Clearing flag.")
                    prefs.edit().putBoolean("is_auto_kill_pending", false).apply()
                    // Go back to where user was
                    performGlobalAction(GLOBAL_ACTION_BACK)
                }
                return
            }

            // If "Force stop" button is not found or is already clicked, look for confirmation "OK" / "Force stop" in the dialog
            val okButton = findNodeByText(rootNode, listOf(
                "ok", 
                "ок", 
                "да", 
                "остановить", 
                "принудительно остановить", 
                "согласен",
                "да, остановить"
            ))

            if (okButton != null) {
                Log.d("OperaMaxStopperService", "Found confirmation 'OK/Stop' button! Clicked.")
                okButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                
                // Done! Clear the flag
                prefs.edit().putBoolean("is_auto_kill_pending", false).apply()
                Log.d("OperaMaxStopperService", "Force stopped successfully. Autokill cleared.")
                
                // Delayed go-back to allow click animation to propagate
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    performGlobalAction(GLOBAL_ACTION_BACK)
                }, 100)
            }
        }
    }

    override fun onInterrupt() {
        Log.d("OperaMaxStopperService", "Service Interrupted")
    }

    private fun findNodeByText(root: AccessibilityNodeInfo, targets: List<String>): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            
            val text = node.text?.toString()?.lowercase()
            val contentDesc = node.contentDescription?.toString()?.lowercase()
            
            // Match with text
            if (text != null) {
                for (target in targets) {
                    if (text == target || text.contains(target)) {
                        // Traverse up to find a clickable container if needed
                        var clickableNode: AccessibilityNodeInfo? = node
                        while (clickableNode != null) {
                            if (clickableNode.isClickable) {
                                return clickableNode
                            }
                            clickableNode = clickableNode.parent
                        }
                    }
                }
            }
            
            // Match with content description
            if (contentDesc != null) {
                for (target in targets) {
                    if (contentDesc == target || contentDesc.contains(target)) {
                        var clickableNode: AccessibilityNodeInfo? = node
                        while (clickableNode != null) {
                            if (clickableNode.isClickable) {
                                return clickableNode
                            }
                            clickableNode = clickableNode.parent
                        }
                    }
                }
            }

            // Search children
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    queue.add(child)
                }
            }
        }
        return null
    }

    private fun showForceStopNotification() {
        val intent = Intent(this, ForceStopReceiver::class.java).apply {
            action = "com.example.ACTION_FORCE_STOP"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_close_clear_cancel)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .addAction(
                android.R.drawable.ic_delete,
                getString(R.string.notification_action),
                pendingIntent
            )
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.channel_name)
            val descriptionText = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
