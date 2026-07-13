package com.example.paymentobserver.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Holds the payments observed from notifications. The [ShopeePayNotificationListenerService] writes
 * to it and the UI observes it — both live in the same process, so a singleton bridges them.
 */
interface PaymentNotificationRepository {
    val payments: StateFlow<List<IncomingPayment>>
    fun add(payment: IncomingPayment)
    fun clearAll()
}

/**
 * In-memory implementation. State survives while the process is alive; swap for a Room-backed
 * implementation when persistence across process death is needed.
 */
object InMemoryPaymentNotificationRepository : PaymentNotificationRepository {

    private val _payments = MutableStateFlow<List<IncomingPayment>>(emptyList())
    override val payments: StateFlow<List<IncomingPayment>> = _payments.asStateFlow()

    override fun add(payment: IncomingPayment) {
        // Newest first.
        _payments.update { current -> listOf(payment) + current }
    }

    override fun clearAll() {
        _payments.value = emptyList()
    }
}
