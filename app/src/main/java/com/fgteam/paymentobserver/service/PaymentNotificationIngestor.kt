package com.fgteam.paymentobserver.service

import com.fgteam.paymentobserver.data.IncomingPayment
import com.fgteam.paymentobserver.data.ObservedApp
import com.fgteam.paymentobserver.data.PaymentNotificationParser
import java.time.LocalDateTime
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class PaymentNotificationEvent(
    val packageName: String,
    val notificationKey: String,
    val postTime: Long,
    val title: String?,
    val body: String?,
    val receivedAt: LocalDateTime
)

internal fun interface NotificationIngestGuard {
    suspend fun run(block: suspend () -> Unit)
}

/** The single serialized notification-to-Room ingest path used by the listener service. */
internal class PaymentNotificationIngestor(
    private val getObservedApp: suspend (String) -> ObservedApp?,
    private val addPayment: suspend (IncomingPayment) -> Boolean,
    private val scheduleSync: () -> Unit,
    private val processingGuard: NotificationIngestGuard = NotificationIngestGuard { it() }
) {
    private val ingestMutex = Mutex()

    suspend fun ingest(event: PaymentNotificationEvent) = ingestMutex.withLock {
        processingGuard.run {
            val observedApp = getObservedApp(event.packageName)
                ?.takeIf { it.isEnabled }
                ?: return@run
            val payment = PaymentNotificationParser.parse(
                packageName = event.packageName,
                appName = observedApp.appName,
                notificationKey = event.notificationKey,
                postTime = event.postTime,
                title = event.title,
                body = event.body,
                receivedAt = event.receivedAt
            ) ?: return@run

            // Room uses a primary key plus OnConflictStrategy.IGNORE, so only the first
            // callback/recovery scan can enqueue synchronization for this notification.
            if (addPayment(payment)) scheduleSync()
        }
    }
}
