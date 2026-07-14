package com.fgteam.paymentobserver.data

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PaymentNotificationParserTest {
    private val now = LocalDateTime.of(2026, 7, 13, 10, 15, 0, 123_000_000)

    @Test
    fun `parses ShopeePay incoming payment`() {
        val payment = parse(
            packageName = ObservedApp.SHOPEE_PAY_PACKAGE,
            appName = "ShopeePay",
            title = "Saldo ShopeePay Diterima",
            body = "Rp10.000 telah diterima dari ANANDA RIZKY YULIANSYAH."
        )

        assertNotNull(payment)
        requireNotNull(payment)
        assertEquals(10_000, payment.amount)
        assertEquals("Rp10.000", payment.rawAmount)
        assertEquals("ANANDA RIZKY YULIANSYAH", payment.sender)
        assertEquals(now, payment.createdAt)
        assertEquals(now, payment.updatedAt)
    }

    @Test
    fun `parses Shopee Partner payment and transaction reference`() {
        val payment = parse(
            packageName = ObservedApp.SHOPEE_PARTNER_PACKAGE,
            appName = "Shopee Partner",
            title = "Pembayaran sebesar Rp1.000 diterima",
            body = "Pembayaran sebesar Rp1.000 telah diterima pada transaksi 20820696399434593."
        )

        assertNotNull(payment)
        requireNotNull(payment)
        assertEquals(1_000, payment.amount)
        assertEquals("Rp1.000", payment.rawAmount)
        assertEquals("Transaksi 20820696399434593", payment.sender)
        assertEquals(ObservedApp.SHOPEE_PARTNER_PACKAGE, payment.packageName)
    }

    @Test
    fun `parses BNI Merchant payment and sender bank`() {
        val payment = parse(
            packageName = ObservedApp.BNI_MERCHANT_PACKAGE,
            appName = "BNI Merchant",
            title = "BNI Merchant",
            body = "Transaksi Sebesar Rp 1.000 dari BLU BCA telah berhasil"
        )

        assertNotNull(payment)
        requireNotNull(payment)
        assertEquals(1_000, payment.amount)
        assertEquals("Rp 1.000", payment.rawAmount)
        assertEquals("BLU BCA", payment.sender)
        assertEquals(ObservedApp.BNI_MERCHANT_PACKAGE, payment.packageName)
    }

    @Test
    fun `identical callback has identical id while different post time does not`() {
        val first = shopeePay()?.id
        val duplicate = shopeePay()?.id
        val later = shopeePay(postTime = 1_001)?.id

        assertEquals(first, duplicate)
        assertNotEquals(first, later)
        assertEquals(32, first?.length)
    }

    @Test
    fun `promotional and unsuccessful notifications are ignored`() {
        assertNull(
            parse(
                packageName = ObservedApp.SHOPEE_PARTNER_PACKAGE,
                appName = "Shopee Partner",
                title = "Promo pembayaran Rp1.000",
                body = "Dapatkan promo hari ini"
            )
        )
        assertNull(
            parse(
                packageName = ObservedApp.BNI_MERCHANT_PACKAGE,
                appName = "BNI Merchant",
                title = "BNI Merchant",
                body = "Transaksi Sebesar Rp 1.000 tidak berhasil"
            )
        )
    }

    private fun shopeePay(postTime: Long = 1_000): IncomingPayment? = parse(
        packageName = ObservedApp.SHOPEE_PAY_PACKAGE,
        appName = "ShopeePay",
        title = "Saldo ShopeePay Diterima",
        body = "Rp10.000 telah diterima dari ANANDA.",
        postTime = postTime
    )

    private fun parse(
        packageName: String,
        appName: String,
        title: String,
        body: String,
        postTime: Long = 1_000
    ): IncomingPayment? = PaymentNotificationParser.parse(
        packageName = packageName,
        appName = appName,
        notificationKey = "0|$packageName|1042|null|10234",
        postTime = postTime,
        title = title,
        body = body,
        receivedAt = now
    )
}
