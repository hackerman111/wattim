package io.ronesec.android.platform.system

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import io.ronesec.android.R
import io.ronesec.android.WattimApplication
import io.ronesec.android.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FocusForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "wattim_protection_status"
        const val NOTIFICATION_ID = 1001

        private val _status = MutableStateFlow(FgsStatus.Stopped)
        val status: StateFlow<FgsStatus> = _status.asStateFlow()

        fun start(context: Context) {
            _status.value = FgsStatus.Starting
            val intent = Intent(context, FocusForegroundService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                _status.value = FgsStatus.StartFailed
                (context.applicationContext as? WattimApplication)?.permissionMonitor?.setFgsStatus(FgsStatus.StartFailed)
            }
        }

        fun stop(context: Context) {
            _status.value = FgsStatus.Stopped
            (context.applicationContext as? WattimApplication)?.permissionMonitor?.setFgsStatus(FgsStatus.Stopped)
            val intent = Intent(context, FocusForegroundService::class.java)
            context.stopService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var statusCollectorJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val initialSnapshot = (application as? WattimApplication)?.permissionMonitor?.statusFlow?.value
                ?: PermissionSnapshot()
            val notification = buildNotification(initialSnapshot)
            promoteToForeground(notification)
            _status.value = FgsStatus.Running
            (application as? WattimApplication)?.permissionMonitor?.setFgsStatus(FgsStatus.Running)

            observePermissionStatus()
        } catch (e: Exception) {
            _status.value = FgsStatus.StartFailed
            (application as? WattimApplication)?.permissionMonitor?.setFgsStatus(FgsStatus.StartFailed)
            stopSelf()
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    private fun promoteToForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun observePermissionStatus() {
        statusCollectorJob?.cancel()
        val permissionMonitor = (application as? WattimApplication)?.permissionMonitor ?: return
        statusCollectorJob = serviceScope.launch {
            permissionMonitor.statusFlow.collect { snapshot ->
                val notification = buildNotification(snapshot)
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                notificationManager?.notify(NOTIFICATION_ID, notification)
            }
        }
    }

    internal fun buildNotification(snapshot: PermissionSnapshot): Notification {
        val isDegraded = !snapshot.areRequiredPermissionsGranted ||
                !snapshot.isAccessibilityConnected ||
                !snapshot.isProtectionOperational

        val title = getString(R.string.app_name)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle(title)

        if (!isDegraded) {
            builder.setContentText(getString(R.string.fgs_status_active))
        } else {
            val missingItems = mutableListOf<String>()
            if (snapshot.accessibility != PermissionState.Granted) {
                missingItems.add(getString(R.string.fgs_status_degraded_accessibility))
            } else if (!snapshot.isAccessibilityConnected) {
                missingItems.add(getString(R.string.fgs_status_degraded_accessibility_disconnected))
            }
            if (snapshot.overlay != PermissionState.Granted) {
                missingItems.add(getString(R.string.fgs_status_degraded_overlay))
            }
            if (snapshot.batteryExemption != PermissionState.Granted) {
                missingItems.add(getString(R.string.fgs_status_degraded_battery))
            }
            if (missingItems.isEmpty()) {
                missingItems.add(getString(R.string.fgs_status_degraded))
            }

            if (missingItems.size == 1) {
                builder.setContentText(missingItems.first())
            } else {
                builder.setContentText(getString(R.string.fgs_status_degraded_multiple, missingItems.size))
                val bigText = buildString {
                    append(getString(R.string.fgs_missing_permissions_header))
                    missingItems.forEach { item ->
                        append("\n• ")
                        append(item)
                    }
                }
                builder.setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            }
        }

        val activityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (isDegraded) {
                putExtra(MainActivity.EXTRA_OPEN_PERMISSIONS, true)
            }
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            if (isDegraded) 1 else 0,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.setContentIntent(pendingIntent)

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.fgs_channel_name)
            val descriptionText = getString(R.string.fgs_channel_desc)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        statusCollectorJob?.cancel()
        _status.value = FgsStatus.Stopped
        (application as? WattimApplication)?.permissionMonitor?.setFgsStatus(FgsStatus.Stopped)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
