package com.fgteam.paymentobserver.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.fgteam.paymentobserver.data.RoomPaymentNotificationRepository
import com.fgteam.paymentobserver.data.PaymentNotificationParser
import com.fgteam.paymentobserver.sync.PaymentSyncScheduler
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ShopeePayNotificationListenerService : NotificationListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val receivedAt = LocalDateTime.now()
        val notification = sbn ?: return
        val extras = notification.notification?.extras ?: return
        val packageName = notification.packageName
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val body = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString()

        serviceScope.launch {
            val repository = RoomPaymentNotificationRepository.getInstance(applicationContext)
            val observedApp = repository.getObservedApp(packageName)
                ?.takeIf { it.isEnabled }
                ?: return@launch
            val payment = PaymentNotificationParser.parse(
                packageName = packageName,
                appName = observedApp.appName,
                notificationKey = notification.key,
                postTime = notification.postTime,
                title = title,
                body = body,
                receivedAt = receivedAt
            ) ?: return@launch

            if (repository.add(payment)) {
                PaymentSyncScheduler.enqueue(applicationContext)
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
