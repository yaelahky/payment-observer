package com.fgteam.paymentobserver.util

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/**
 * Helpers around the special "Notification access" permission required by a
 * [android.service.notification.NotificationListenerService].
 */
object NotificationAccess {

    /** True if this app is currently allowed to read notifications. */
    fun isEnabled(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)

    /** Intent that opens the system "Notification access" settings screen. */
    fun settingsIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
