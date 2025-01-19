package com.focuszone.domain.services.app

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.focuszone.R
import com.focuszone.data.preferences.PreferencesManager
import com.focuszone.data.preferences.entities.BlockedApp
import com.focuszone.domain.NotificationManager
import com.focuszone.util.DialogHelper
class AppMonitorService : AccessibilityService() {

    private lateinit var preferencesManager: PreferencesManager
    private var monitoredApps: List<BlockedApp> = emptyList()
    private var activeAppStartTime: Long = 0
    private var lastActivePackage: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        Log.d("AppMonitorService", "Service created")
        preferencesManager = PreferencesManager(this)
        monitoredApps = preferencesManager.getLimitedApps().filter { it.isLimitSet }
        Log.d("AppMonitorService", "Initial monitored apps: $monitoredApps")
        if (monitoredApps.isEmpty()) {
            Log.d("AppMonitorService", "No monitored apps found, stopping service")
            stopSelf()
        }
        startPolling()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString()
            Log.d("AppMonitorService", "Window state changed: $packageName")

            if (packageName != null) {
                handlePackageChange(packageName)
            }
        }
    }

    private fun handlePackageChange(newPackage: String) {
        Log.d("AppMonitorService", "Handling package change to: $newPackage")

        // Save time spent in previous app if it was monitored
        lastActivePackage?.let { lastPackage ->
            Log.d("AppMonitorService", "Previous package was: $lastPackage")
            monitoredApps.find { it.id == lastPackage }?.let { app ->
                Log.d("AppMonitorService", "Saving time for previous app: ${app.id}")
                saveTimeSpentInApp(app)
            }
        }

        // Reset tracking and start monitoring new package
        lastActivePackage = newPackage
        activeAppStartTime = System.currentTimeMillis()

        // Check if new package is monitored
        val monitoredApp = monitoredApps.find { it.id == newPackage }
        Log.d("AppMonitorService", "Found monitored app: $monitoredApp")

        if (monitoredApp != null) {
            Log.d("AppMonitorService", "Starting to monitor app: ${monitoredApp.id}")
            startMonitoringApp(monitoredApp)
        } else {
            Log.d("AppMonitorService", "New package is not monitored, clearing monitoring")
            handler.removeCallbacksAndMessages(null)
        }
    }

    private fun saveTimeSpentInApp(app: BlockedApp) {
        val timeSpentMinutes = ((System.currentTimeMillis() - activeAppStartTime) / 1000 / 60).toInt()
        Log.d("AppMonitorService", "Calculating time spent in ${app.id}: $timeSpentMinutes minutes")

        if (timeSpentMinutes > 0) {
            val updatedApp = app.copy(
                currentTimeUsage = (app.currentTimeUsage ?: 0) + timeSpentMinutes
            )
            preferencesManager.addOrUpdateLimitedApp(updatedApp)
            Log.d("AppMonitorService", "Saved time for ${app.id}: +$timeSpentMinutes minutes, total: ${updatedApp.currentTimeUsage}")
        }
    }

    private fun startMonitoringApp(app: BlockedApp) {
        Log.d("AppMonitorService", "Starting to monitor app: ${app.id}")
        handler.removeCallbacksAndMessages(null)

        // First check if we should block immediately based on previously saved time
        if (app.isLimitSet && (app.currentTimeUsage ?: 0) >= app.limitMinutes!!) {
            Log.d("AppMonitorService", "App ${app.id} already exceeded limit, blocking immediately")
            blockApp(app.id)
            return
        }

        // If not blocked, start monitoring
        checkAppTimeLimit(app)
    }

    private fun checkAppTimeLimit(app: BlockedApp) {
        val currentTimeSpent = ((System.currentTimeMillis() - activeAppStartTime) / 1000 / 60).toInt()
        val totalTimeSpent = (app.currentTimeUsage ?: 0) + currentTimeSpent

        Log.d("AppMonitorService", "Checking time limit for ${app.id}")
        Log.d("AppMonitorService", "Current session time: $currentTimeSpent minutes")
        Log.d("AppMonitorService", "Total time spent: $totalTimeSpent minutes")
        Log.d("AppMonitorService", "Time limit: ${app.limitMinutes} minutes")

        if (app.isLimitSet && totalTimeSpent >= app.limitMinutes!!) {
            Log.d("AppMonitorService", "Time limit exceeded for ${app.id}")
            blockApp(app.id)
            showUserMessageDialog()
        } else {
            // Continue monitoring every second
            handler.postDelayed({
                if (app.id == lastActivePackage) {
                    checkAppTimeLimit(app)
                }
            }, 1000)
        }
    }

    private fun startPolling() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                Log.d("AppMonitorService", "Polling for changes in monitored apps")
                val newMonitoredApps = preferencesManager.getLimitedApps().filter { it.isLimitSet }
                if (newMonitoredApps != monitoredApps) {
                    Log.d("AppMonitorService", "Detected changes in monitored apps")
                    monitoredApps = newMonitoredApps
                    Log.d("AppMonitorService", "Updated monitored apps: $monitoredApps")
                } else {
                    Log.d("AppMonitorService", "No changes detected in monitored apps")
                }
                handler.postDelayed(this, 5000) // Poll every 5 seconds
            }
        }, 5000)
    }

    private fun blockApp(packageName: String) {
        // Save time before blocking
        monitoredApps.find { it.id == packageName }?.let { app ->
            Log.d("AppMonitorService", "Saving final time before blocking for: ${app.id}")
            saveTimeSpentInApp(app)
        }

        Log.d("AppMonitorService", "Blocking app: $packageName")
        Toast.makeText(this, getString(R.string.app_blocked), Toast.LENGTH_LONG).show()

        NotificationManager(this).showBlockedAppNotification(packageName)
        performGlobalAction(GLOBAL_ACTION_HOME)

        lastActivePackage = null
        activeAppStartTime = 0
    }

    private fun showUserMessageDialog() {
        Log.d("AppMonitorService", "Showing dialog: $monitoredApps")

        preferencesManager.getUserMessage()?.let {
            DialogHelper.showBlockingAlert(
                this,
                it
            )
        }
    }

    override fun onInterrupt() {
        handler.removeCallbacksAndMessages(null)
        Log.d("AppMonitorService", "Service interrupted")
        Toast.makeText(this, "Service interrupted", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        Log.d("AppMonitorService", "Service being destroyed")
        // Save time for last active app before destroying service
        lastActivePackage?.let { lastPackage ->
            Log.d("AppMonitorService", "Saving final time for last active app: $lastPackage")
            monitoredApps.find { it.id == lastPackage }?.let { app ->
                saveTimeSpentInApp(app)
            }
        }
        stopForeground(true)
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
        Log.d("AppMonitorService", "Service destroyed")
    }

    @SuppressLint("ForegroundServiceType")
    override fun onServiceConnected() {
        preferencesManager = PreferencesManager(this)
        notificationManager = NotificationManager(this)

        Log.d("AppMonitorService", "Service connected")
        startForeground(1, notificationManager.showAppMonitorServiceRunningNotificationF())

        monitoredApps = preferencesManager.getLimitedApps().filter { it.isLimitSet }
        Log.d("AppMonitorService", "Initial monitored apps: $monitoredApps")
    }
}