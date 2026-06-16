package com.example.paymentobserver.ui.payments

import com.example.paymentobserver.data.Payment

/**
 * Immutable snapshot of everything the payments screen needs to render.
 * The ViewModel is the single source of truth that produces these states.
 */
data class PaymentsUiState(
    val isLoading: Boolean = false,
    val payments: List<Payment> = emptyList(),
    val errorMessage: String? = null
)
