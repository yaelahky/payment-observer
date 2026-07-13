package com.example.paymentobserver.data

import java.util.UUID

/**
 * Turns a raw ShopeePay notification (title + text) into an [IncomingPayment].
 *
 * Detection is content-based rather than purely package-based so it keeps working even if
 * ShopeePay ships notifications under a different package: the text must mention "ShopeePay",
 * indicate money was received ("diterima"), and contain a rupiah amount.
 */
object ShopeePayNotificationParser {

    // "Rp10", "Rp 1.500", "Rp10.000" → captures the digit/separator group.
    private val amountRegex = Regex("""Rp\s?([0-9][0-9.,]*)""", RegexOption.IGNORE_CASE)

    // "... diterima dari ANANDA RIZKY YULIANSYAH. Klik ..." → captures the name up to the period.
    private val senderRegex = Regex("""dari\s+(.+?)\.""", RegexOption.IGNORE_CASE)

    fun parse(
        packageName: String,
        title: String?,
        text: String?,
        timestamp: Long
    ): IncomingPayment? {
        val combined = listOfNotNull(title, text).joinToString(" ").trim()
        if (combined.isEmpty()) return null

        val mentionsShopeePay = combined.contains("shopeepay", ignoreCase = true)
        val isIncoming = combined.contains("diterima", ignoreCase = true)
        if (!mentionsShopeePay || !isIncoming) return null

        val amountMatch = amountRegex.find(combined) ?: return null
        val amount = amountMatch.groupValues[1]
            .filter(Char::isDigit)
            .toLongOrNull() ?: return null

        val sender = senderRegex.find(text ?: combined)
            ?.groupValues?.get(1)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: "Tidak diketahui"

        return IncomingPayment(
            id = UUID.randomUUID().toString(),
            amount = amount,
            rawAmount = amountMatch.value.trim(),
            sender = sender,
            title = title.orEmpty(),
            text = text.orEmpty(),
            packageName = packageName,
            timestamp = timestamp
        )
    }
}
