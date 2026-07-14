package com.fgteam.paymentobserver.data

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.LocalDateTime

/** Parses incoming-payment notifications after the package whitelist accepts them. */
object PaymentNotificationParser {
    private val amountRegex = Regex("""Rp\s*([0-9][0-9.,]*)""", RegexOption.IGNORE_CASE)
    private val shopeeSenderRegex = Regex(
        """dari\s+(.+?)(?:\.|$)""",
        RegexOption.IGNORE_CASE
    )
    private val shopeePartnerTransactionRegex = Regex(
        """transaksi\s+([A-Za-z0-9-]+)""",
        RegexOption.IGNORE_CASE
    )
    private val bniSenderRegex = Regex(
        """dari\s+(.+?)\s+telah\s+berhasil""",
        RegexOption.IGNORE_CASE
    )

    fun parse(
        packageName: String,
        appName: String,
        notificationKey: String,
        postTime: Long,
        title: String?,
        body: String?,
        receivedAt: LocalDateTime
    ): IncomingPayment? {
        val safeTitle = title.orEmpty().trim()
        val safeBody = body.orEmpty().trim()
        val combined = listOf(safeTitle, safeBody).filter(String::isNotEmpty).joinToString(" ")
        if (combined.isEmpty()) return null

        val sender = when (packageName) {
            ObservedApp.SHOPEE_PAY_PACKAGE,
            ObservedApp.SHOPEE_PACKAGE -> parseShopeePay(combined, safeBody)

            ObservedApp.SHOPEE_PARTNER_PACKAGE -> parseShopeePartner(combined, safeBody)
            ObservedApp.BNI_MERCHANT_PACKAGE -> parseBniMerchant(combined, safeBody)
            else -> null
        } ?: return null

        val amountMatch = amountRegex.find(combined) ?: return null
        val amount = amountMatch.groupValues[1]
            .filter(Char::isDigit)
            .toLongOrNull()
            ?.takeIf { it > 0 }
            ?: return null

        return IncomingPayment(
            id = deterministicId(packageName, notificationKey, postTime),
            sourceNotificationKey = notificationKey,
            amount = amount,
            rawAmount = amountMatch.value.trim(),
            sender = sender,
            title = safeTitle,
            body = safeBody,
            appName = appName,
            packageName = packageName,
            createdAt = receivedAt,
            updatedAt = receivedAt
        )
    }

    private fun parseShopeePay(combined: String, body: String): String? {
        if (!combined.contains("shopeepay", ignoreCase = true) ||
            !combined.contains("diterima", ignoreCase = true)
        ) return null
        return shopeeSenderRegex.find(body.ifEmpty { combined })
            ?.groupValues?.get(1)?.trim()?.takeIf(String::isNotEmpty)
            ?: "Tidak diketahui"
    }

    private fun parseShopeePartner(combined: String, body: String): String? {
        if (!combined.contains("pembayaran sebesar", ignoreCase = true) ||
            !combined.contains("diterima", ignoreCase = true)
        ) return null
        val transactionId = shopeePartnerTransactionRegex.find(body.ifEmpty { combined })
            ?.groupValues?.get(1)?.trim()?.takeIf(String::isNotEmpty)
        return transactionId?.let { "Transaksi $it" } ?: "Shopee Partner"
    }

    private fun parseBniMerchant(combined: String, body: String): String? {
        if (!combined.contains("transaksi sebesar", ignoreCase = true) ||
            !combined.contains("telah berhasil", ignoreCase = true)
        ) return null
        return bniSenderRegex.find(body.ifEmpty { combined })
            ?.groupValues?.get(1)?.trim()?.takeIf(String::isNotEmpty)
            ?: "Tidak diketahui"
    }

    internal fun deterministicId(
        packageName: String,
        notificationKey: String,
        postTime: Long
    ): String {
        val source = "$packageName\u0000$notificationKey\u0000$postTime"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(source.toByteArray(StandardCharsets.UTF_8))
        return digest.take(16).joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }
}
