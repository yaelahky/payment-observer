package com.example.paymentobserver.ui.payments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.paymentobserver.data.InMemoryPaymentNotificationRepository
import com.example.paymentobserver.data.PaymentNotificationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Owns the [PaymentsUiState]. It combines the observed payments (written by the notification
 * listener service) with the current notification-access status reported by the UI.
 */
class PaymentsViewModel(
    private val repository: PaymentNotificationRepository
) : ViewModel() {

    private val accessGranted = MutableStateFlow(false)

    val uiState: StateFlow<PaymentsUiState> =
        combine(repository.payments, accessGranted) { payments, granted ->
            PaymentsUiState(hasNotificationAccess = granted, payments = payments)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PaymentsUiState()
        )

    /** Called by the UI (on resume) after checking the system setting. */
    fun setAccessGranted(granted: Boolean) {
        accessGranted.value = granted
    }

    fun clearPayments() {
        repository.clearAll()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PaymentsViewModel(repository = InMemoryPaymentNotificationRepository)
            }
        }
    }
}
