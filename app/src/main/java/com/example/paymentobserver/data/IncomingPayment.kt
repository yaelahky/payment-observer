package com.example.paymentobserver.data

/**
 * A balance-in event parsed from a ShopeePay notification.
 *
 * Example source notification:
 *  - title: "Saldo ShopeePay Diterima"
 *  - text : "Rp10 telah diterima dari ANANDA RIZKY YULIANSYAH. Klik untuk cek rinciannya"
 */
data class IncomingPayment(
    val id: String,
    /** Amount in whole rupiah, e.g. 10 or 10000. */
    val amount: Long,
    /** The raw amount string as it appeared, e.g. "Rp10". */
    val rawAmount: String,
    /** Name of the sender, e.g. "ANANDA RIZKY YULIANSYAH". */
    val sender: String,
    val title: String,
    val text: String,
    val packageName: String,
    val timestamp: Long
)
