package com.example.paymentobserver.data

/**
 * A single payment observed by the app. This is the domain model the UI renders.
 */
data class Payment(
    val id: String,
    val merchant: String,
    val amount: Double,
    val currency: String,
    val status: PaymentStatus,
    val timestamp: Long
)

enum class PaymentStatus {
    PENDING,
    COMPLETED,
    FAILED
}
