package com.fgteam.paymentobserver.ui.payments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fgteam.paymentobserver.data.PaymentNotificationRepository
import com.fgteam.paymentobserver.data.RoomPaymentNotificationRepository
import com.fgteam.paymentobserver.auth.ApiException
import com.fgteam.paymentobserver.auth.ObserverAccessDeniedException
import com.fgteam.paymentobserver.auth.SessionManager
import com.fgteam.paymentobserver.network.PaymentObserverApi
import com.fgteam.paymentobserver.sync.PaymentSyncRepository
import com.fgteam.paymentobserver.sync.PaymentSyncScheduler
import com.fgteam.paymentobserver.sync.SyncOutcome
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PaymentsViewModel(
    private val repository: PaymentNotificationRepository,
    private val sessionManager: SessionManager,
    private val api: PaymentObserverApi,
    private val syncRepository: PaymentSyncRepository,
    private val scheduleSync: () -> Unit
) : ViewModel() {
    private val accessGranted = MutableStateFlow(false)
    private val selectedPackageName = MutableStateFlow<String?>(null)
    private val currentDay = MutableStateFlow(LocalDate.now())
    private val transientState = MutableStateFlow(TransientState())

    private val payments = selectedPackageName.flatMapLatest(repository::observePayments)
    private val totalToday = currentDay.flatMapLatest { day ->
        repository.observeTotalBetween(day.atStartOfDay(), day.plusDays(1).atStartOfDay())
    }

    private val paymentsState = combine(
        payments,
        totalToday,
        repository.observeApps(),
        accessGranted,
        selectedPackageName
    ) { paymentList, total, apps, granted, selectedPackage ->
        PaymentsUiState(
            hasNotificationAccess = granted,
            totalToday = total,
            observedApps = apps,
            selectedPackageName = selectedPackage,
            payments = paymentList
        )
    }

    val uiState: StateFlow<PaymentsUiState> = combine(
        paymentsState,
        repository.observePendingCount(),
        sessionManager.session,
        transientState
    ) { base, pending, session, transient ->
        base.copy(
            pendingSyncCount = pending,
            session = session?.let { AdminIdentity(it.fullName, it.role) },
            isRestoringSession = transient.isRestoringSession,
            isAuthenticating = transient.isAuthenticating,
            authError = transient.authError,
            isSyncing = transient.isSyncing,
            syncMessage = transient.syncMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PaymentsUiState()
    )

    init {
        viewModelScope.launch {
            if (sessionManager.current() != null) {
                runCatching { api.validateProfile() }
                    .onFailure { error ->
                        if (error is ObserverAccessDeniedException) {
                            transientState.value = transientState.value.copy(authError = error.message)
                        }
                    }
            }
            transientState.value = transientState.value.copy(isRestoringSession = false)
        }
    }

    fun onResume(hasNotificationAccess: Boolean) {
        accessGranted.value = hasNotificationAccess
        currentDay.value = LocalDate.now()
    }

    fun selectPackage(packageName: String?) {
        selectedPackageName.value = packageName
    }

    fun setAppEnabled(packageName: String, enabled: Boolean) {
        viewModelScope.launch {
            repository.setAppEnabled(packageName, enabled, LocalDateTime.now())
        }
    }

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            transientState.value = transientState.value.copy(authError = "Email dan password wajib diisi")
            return
        }
        viewModelScope.launch {
            transientState.value = transientState.value.copy(isAuthenticating = true, authError = null)
            try {
                api.login(email.trim(), password)
                transientState.value = transientState.value.copy(
                    isAuthenticating = false,
                    authError = null,
                    syncMessage = "Login berhasil. Pembayaran tertunda dijadwalkan."
                )
                scheduleSync()
            } catch (error: Exception) {
                val message = when (error) {
                    is ObserverAccessDeniedException -> error.message
                    is ApiException -> error.message ?: "Login gagal"
                    else -> error.message ?: "Login gagal"
                }
                transientState.value = transientState.value.copy(
                    isAuthenticating = false,
                    authError = message
                )
            }
        }
    }

    fun logout() {
        sessionManager.clear()
        transientState.value = transientState.value.copy(
            authError = null,
            syncMessage = null,
            isSyncing = false
        )
    }

    fun syncAndReconcile() {
        viewModelScope.launch {
            transientState.value = transientState.value.copy(isSyncing = true, syncMessage = null)
            val outcome = syncRepository.reconcile()
            transientState.value = transientState.value.copy(
                isSyncing = false,
                syncMessage = when (outcome) {
                    SyncOutcome.COMPLETE -> "Sinkronisasi dan pengecekan status selesai"
                    SyncOutcome.AUTH_REQUIRED -> "Sesi habis. Silakan login kembali."
                    SyncOutcome.RETRY -> "Sinkronisasi gagal. Akan dicoba kembali saat jaringan tersedia."
                }
            )
            if (outcome == SyncOutcome.RETRY) scheduleSync()
        }
    }

    private data class TransientState(
        val isRestoringSession: Boolean = true,
        val isAuthenticating: Boolean = false,
        val authError: String? = null,
        val isSyncing: Boolean = false,
        val syncMessage: String? = null
    )

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = checkNotNull(
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                )
                PaymentsViewModel(
                    repository = RoomPaymentNotificationRepository.getInstance(application),
                    sessionManager = SessionManager.getInstance(application),
                    api = PaymentObserverApi.getInstance(application),
                    syncRepository = PaymentSyncRepository.getInstance(application),
                    scheduleSync = { PaymentSyncScheduler.enqueue(application) }
                )
            }
        }
    }
}
