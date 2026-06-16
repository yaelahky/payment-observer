package com.example.paymentobserver.ui.payments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.paymentobserver.data.InMemoryPaymentRepository
import com.example.paymentobserver.data.PaymentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns and exposes the [PaymentsUiState] for the payments screen.
 *
 * The repository is injected via the constructor so tests can pass a fake; production
 * code obtains an instance through [Factory].
 */
class PaymentsViewModel(
    private val repository: PaymentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PaymentsUiState(isLoading = true))
    val uiState: StateFlow<PaymentsUiState> = _uiState.asStateFlow()

    init {
        loadPayments()
    }

    fun loadPayments() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val payments = repository.getPayments()
                _uiState.update { it.copy(isLoading = false, payments = payments) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = e.message ?: "Unknown error")
                }
            }
        }
    }

    companion object {
        /** Default factory wiring the production [PaymentRepository]. */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PaymentsViewModel(repository = InMemoryPaymentRepository())
            }
        }
    }
}
