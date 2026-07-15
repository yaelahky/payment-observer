package com.fgteam.paymentobserver.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.service.notification.NotificationListenerService
import androidx.core.content.ContextCompat
import com.fgteam.paymentobserver.MainActivity
import com.fgteam.paymentobserver.R
import com.fgteam.paymentobserver.util.NotificationAccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Keeps the process foreground. It deliberately has no payment/parser/repository dependency. */
class ObserverForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        promoteToForeground(buildNotification(ObserverRuntime.state.value))
        ObserverRuntime.setForegroundServiceRunning(true)
        serviceScope.launch {
            ObserverRuntime.state.collectLatest { state ->
                notificationManager()
                    .notify(NOTIFICATION_ID, buildNotification(state))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            NotificationAccess.isEnabled(this) &&
            !ObserverRuntime.state.value.isListenerConnected
        ) {
            NotificationListenerService.requestRebind(
                ComponentName(this, ShopeePayNotificationListenerService::class.java)
            )
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        ObserverRuntime.setForegroundServiceRunning(false)
        super.onDestroy()
    }

    private fun promoteToForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.observer_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.observer_channel_description)
            setShowBadge(false)
        }
        notificationManager().createNotificationChannel(channel)
    }

    private fun notificationManager(): NotificationManager =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun buildNotification(state: ObserverRuntimeState): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val message = when {
            !NotificationAccess.isEnabled(this) -> getString(R.string.observer_notification_access_required)
            state.isListenerConnected -> getString(R.string.observer_notification_connected)
            else -> getString(R.string.observer_notification_reconnecting)
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setSmallIcon(R.drawable.ic_stat_observer)
            .setContentTitle(getString(R.string.observer_notification_title))
            .setContentText(message)
            .setContentIntent(openApp)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(Notification.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "payment-observer-runtime"
        private const val NOTIFICATION_ID = 10_042

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context.applicationContext,
                Intent(context.applicationContext, ObserverForegroundService::class.java)
            )
        }
    }
}
