package com.fgteam.paymentobserver.service

import com.fgteam.paymentobserver.data.IncomingPayment
import com.fgteam.paymentobserver.data.ObservedApp
import java.time.LocalDateTime
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class PaymentNotificationIngestorTest {
    private val now = LocalDateTime.of(2026, 7, 15, 10, 0)
    private val observedApp = ObservedApp(
        packageName = ObservedApp.SHOPEE_PAY_PACKAGE,
        appName = "ShopeePay",
        isEnabled = true,
        sortOrder = 1,
        createdAt = now,
        updatedAt = now
    )

    @Test
    fun `callback and recovery scan store and schedule identical notification once`() = runBlocking {
        val storedIds = mutableSetOf<String>()
        var scheduleCount = 0
        val ingestor = ingestor(
            addPayment = { storedIds.add(it.id) },
            scheduleSync = { scheduleCount++ }
        )
        val callbackEvent = event()
        val recoveryEvent = callbackEvent.copy(receivedAt = now.plusSeconds(1))

        awaitAll(
            async { ingestor.ingest(callbackEvent) },
            async { ingestor.ingest(recoveryEvent) }
        )

        assertEquals(1, storedIds.size)
        assertEquals(1, scheduleCount)
    }

    @Test
    fun `concurrent duplicate callbacks are serialized and inserted once`() = runBlocking {
        val storedIds = mutableSetOf<String>()
        var scheduleCount = 0
        val ingestor = ingestor(
            addPayment = { storedIds.add(it.id) },
            scheduleSync = { scheduleCount++ }
        )

        List(20) { async { ingestor.ingest(event()) } }.awaitAll()

        assertEquals(1, storedIds.size)
        assertEquals(1, scheduleCount)
    }

    @Test
    fun `different notification post times remain separate payments`() = runBlocking {
        val stored = mutableListOf<IncomingPayment>()
        var scheduleCount = 0
        val ingestor = ingestor(
            addPayment = { payment ->
                stored += payment
                true
            },
            scheduleSync = { scheduleCount++ }
        )

        ingestor.ingest(event(postTime = 1_000))
        ingestor.ingest(event(postTime = 2_000))

        assertEquals(2, stored.map(IncomingPayment::id).distinct().size)
        assertEquals(2, scheduleCount)
    }

    private fun ingestor(
        addPayment: suspend (IncomingPayment) -> Boolean,
        scheduleSync: () -> Unit
    ) = PaymentNotificationIngestor(
        getObservedApp = { packageName -> observedApp.takeIf { it.packageName == packageName } },
        addPayment = addPayment,
        scheduleSync = scheduleSync
    )

    private fun event(postTime: Long = 1_000) = PaymentNotificationEvent(
        packageName = ObservedApp.SHOPEE_PAY_PACKAGE,
        notificationKey = "0|${ObservedApp.SHOPEE_PAY_PACKAGE}|1042|null|10234",
        postTime = postTime,
        title = "Saldo ShopeePay Diterima",
        body = "Rp10.000 telah diterima dari ANANDA.",
        receivedAt = now
    )
}
