package io.ronesec.android.platform.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.ronesec.android.WattimApplication

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        val app = context.applicationContext as? WattimApplication ?: return

        // 1. Rehydrate durable policy store
        app.rehydrateOnBootOrUpdate()

        // 2. Check live permissions; if required permissions are intact, start FGS
        val permissionMonitor = app.permissionMonitor
        val snapshot = permissionMonitor.checkAll()
        if (snapshot.areRequiredPermissionsGranted) {
            FocusForegroundService.start(context)
        }
    }
}
