package com.example.paymentobserver.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.paymentobserver.data.InMemoryPaymentNotificationRepository
import com.example.paymentobserver.data.ShopeePayNotificationParser

/**
 * Listens to notifications posted by other apps and forwards ShopeePay "balance received" events
 * to the repository.
 *
 * Binding is performed by the system once the user grants "Notification access" in Settings; there
 * is no runtime permission dialog for [NotificationListenerService].
 */
class ShopeePayNotificationListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val sbn = sbn ?: return
        val extras = sbn.notification?.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        // Prefer the expanded text, falling back to the collapsed text.
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString()

        val payment = ShopeePayNotificationParser.parse(
            packageName = sbn.packageName,
            title = title,
            text = text,
            timestamp = sbn.postTime
        ) ?: return

        InMemoryPaymentNotificationRepository.add(payment)
    }
}
