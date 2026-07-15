package com.fgteam.paymentobserver.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.fgteam.paymentobserver.data.RoomPaymentNotificationRepository
import com.fgteam.paymentobserver.sync.PaymentSyncScheduler
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ShopeePayNotificationListenerService : NotificationListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ingestor by lazy {
        val repository = RoomPaymentNotificationRepository.getInstance(applicationContext)
        PaymentNotificationIngestor(
            getObservedApp = repository::getObservedApp,
            addPayment = repository::add,
            scheduleSync = { PaymentSyncScheduler.enqueue(applicationContext) },
            processingGuard = wakeLockGuard()
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.let { enqueue(it, LocalDateTime.now()) }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        ObserverRuntime.setListenerConnected(true)
        runCatching { activeNotifications.orEmpty() }
            .getOrDefault(emptyArray())
            .forEach { enqueue(it, LocalDateTime.now()) }
    }

    override fun onListenerDisconnected() {
        ObserverRuntime.setListenerConnected(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            requestRebind(ComponentName(this, javaClass))
        }
        super.onListenerDisconnected()
    }

    private fun enqueue(notification: StatusBarNotification, receivedAt: LocalDateTime) {
        val extras = notification.notification?.extras ?: return
        val event = PaymentNotificationEvent(
            packageName = notification.packageName,
            notificationKey = notification.key,
            postTime = notification.postTime,
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            body = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString(),
            receivedAt = receivedAt
        )
        serviceScope.launch {
            ingestor.ingest(event)
        }
    }

    private fun wakeLockGuard() = NotificationIngestGuard { block ->
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "$packageName:notification-ingest"
        ).apply {
            setReferenceCounted(false)
            acquire(INGEST_WAKE_LOCK_TIMEOUT_MILLIS)
        }
        try {
            block()
        } finally {
            if (wakeLock.isHeld) wakeLock.release()
        }
    }

    override fun onDestroy() {
        ObserverRuntime.setListenerConnected(false)
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val INGEST_WAKE_LOCK_TIMEOUT_MILLIS = 30_000L
    }
}
