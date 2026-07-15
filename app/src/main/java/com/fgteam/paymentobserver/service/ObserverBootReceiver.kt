package com.fgteam.paymentobserver.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.fgteam.paymentobserver.util.NotificationAccess

class ObserverBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in SUPPORTED_ACTIONS || !NotificationAccess.isEnabled(context)) return
        runCatching { ObserverForegroundService.start(context) }
    }

    companion object {
        private val SUPPORTED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED
        )
    }
}
