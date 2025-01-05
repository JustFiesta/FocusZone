package com.focuszone.domain.services.app

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.focuszone.data.preferences.PreferencesManager
import com.focuszone.data.preferences.entities.BlockedApp
import com.focuszone.domain.NotificationManager
import com.focuszone.util.DialogHelper

/** Monitor time spent in applications
 *
 * HOW TO USE
 *
 * On first app start:
 * val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
 * startActivity(intent)
 *
 * It will start automatically as it is now System Service
 * **/

class AppMonitorService : AccessibilityService() {

    private var preferencesManager = PreferencesManager(this)
    private var monitoredApps: List<BlockedApp> = emptyList()
    private var activeAppStartTime: Long = 0
    private var lastActivePackage: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var notificationManager: NotificationManager

    // on creation of this service set listener for changes in sharedPreferences
    @SuppressLint("ForegroundServiceType")
    override fun onCreate() {
        super.onCreate()
        preferencesManager = PreferencesManager(this)
        notificationManager = NotificationManager(this)

        startForeground(1, notificationManager.showAppMonitorServiceRunningNotificationF())


        monitoredApps = preferencesManager.getLimitedApps()

        preferencesManager.addLimitedAppsChangedListener(object : PreferencesManager.OnLimitedAppsChangedListener {
            override fun onLimitedAppsChanged(newLimitedApps: List<BlockedApp>) {
                monitoredApps = newLimitedApps
            }
        })
    }

    /** On event run monitor
     * check its limit time and current usage
     * current usage is from where?
     * if current usage is >= limit blockApp()
     * monitor changes in current usage - update on add/delete
     * monitor changes in Preferences - update on add/delete
     * TODO
     * **/
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString()

            if (packageName != null && packageName != lastActivePackage) {
                lastActivePackage = packageName
                activeAppStartTime = System.currentTimeMillis()

                val monitoredApp = monitoredApps.find { it.id == packageName }

                if (monitoredApp != null) {
                    preferencesManager.getUserMessage()?.let {
                        DialogHelper.showBlockingAlert(
                            this,
                            it
                        )
                    }
                    performGlobalAction(GLOBAL_ACTION_BACK)
                }
            }
        }
    }

    private fun startMonitoringApp(app: BlockedApp) {
        handler.removeCallbacksAndMessages(null) // Clear any pending monitors
        monitorApp(app.id, app)
    }

    override fun onInterrupt() {
        handler.removeCallbacksAndMessages(null)
    }

    /** Monitor time spent in each app BlockedApp
     * get app ID/name/package name/etc
     * if app is opened count its usage time in Foreground state
     * if app limit is stopped stop counter but remember its state
     * if app limit is exceeded block app
     * handle app switching
     * */
    private fun monitorApp(packageName: String, app: BlockedApp) {
        val currentTimeUsage = app.currentTimeUsage ?: 0
        val timeSpentMinutes = (System.currentTimeMillis() - activeAppStartTime) / 1000 / 60

        if (app.isLimitSet && currentTimeUsage + timeSpentMinutes >= app.limitMinutes!!) {
            blockApp(packageName)
        } else {
            handler.postDelayed({
                monitorApp(packageName, app)
            }, 1000) // Check every second
        }
    }

    private fun updateAppUsageTime(app: BlockedApp) {
        val timeSpentMinutes = (System.currentTimeMillis() - activeAppStartTime) / 1000 / 60
        val updatedApp = app.copy(
            currentTimeUsage = (app.currentTimeUsage ?: 0) + timeSpentMinutes.toInt()
        )
        preferencesManager.addOrUpdateLimitedApp(updatedApp)
    }

    /** Block given app to the end of the day
     * get app ID/name/package name/etc
     * show fullscreen message
     * tell system to go back to previous screen
    * */
    fun blockApp(packageName: String){
        Toast.makeText(this, "Aplikacja $packageName została zablokowana", Toast.LENGTH_LONG).show()

        performGlobalAction(GLOBAL_ACTION_BACK)

        handler.removeCallbacksAndMessages(null)
        lastActivePackage = null

        // Show notification via notification manager TODO
        NotificationManager(this).showBlockedAppNotification(packageName)
        // Show fullscreen message TODO
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        // Remove listener
        preferencesManager.removeLimitedAppsChangedListener(object : PreferencesManager.OnLimitedAppsChangedListener {
            override fun onLimitedAppsChanged(newLimitedApps: List<BlockedApp>) {
                // Do nothing
            }
        })
        stopForeground(true)
        super.onDestroy()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        monitoredApps = preferencesManager.getLimitedApps()
    }
}