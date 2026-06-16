package com.example.paymentobserver.data

import kotlinx.coroutines.delay

/**
 * Source of payment data. Implementations can be swapped (network, database, etc.)
 * without changing the ViewModel — this is the seam that keeps MVVM testable.
 */
interface PaymentRepository {
    suspend fun getPayments(): List<Payment>
}

/**
 * Placeholder in-memory implementation. Replace with a real data source when available.
 */
class InMemoryPaymentRepository : PaymentRepository {

    override suspend fun getPayments(): List<Payment> {
        // Simulate I/O latency so the loading state is observable in the UI.
        delay(800)
        return SAMPLE_PAYMENTS
    }

    private companion object {
        val SAMPLE_PAYMENTS = listOf(
            Payment("1", "Spotify", 4.99, "USD", PaymentStatus.COMPLETED, 1_718_000_000_000),
            Payment("2", "Amazon", 129.50, "USD", PaymentStatus.PENDING, 1_718_100_000_000),
            Payment("3", "Netflix", 15.49, "USD", PaymentStatus.COMPLETED, 1_718_200_000_000),
            Payment("4", "Steam", 59.99, "USD", PaymentStatus.FAILED, 1_718_300_000_000)
        )
    }
}
