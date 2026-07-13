package com.example.paymentobserver.ui.payments

import com.example.paymentobserver.data.IncomingPayment

/**
 * Immutable snapshot of everything the payments screen needs to render.
 */
data class PaymentsUiState(
    /** Whether the app currently has notification-listening access. */
    val hasNotificationAccess: Boolean = false,
    val payments: List<IncomingPayment> = emptyList()
)
