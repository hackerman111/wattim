package io.ronesec.android.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.ronesec.android.service.FocusForegroundService

class BootReceiver : BroadcastReceiver {
    constructor() : super()

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            FocusForegroundService.start(context)
        }
    }
}
