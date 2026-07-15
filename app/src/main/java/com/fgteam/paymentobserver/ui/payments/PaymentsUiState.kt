package com.fgteam.paymentobserver.ui.payments

import com.fgteam.paymentobserver.data.IncomingPayment
import com.fgteam.paymentobserver.data.ObservedApp
import com.fgteam.paymentobserver.util.OemPowerGuide

data class AdminIdentity(val fullName: String, val role: String)

data class PaymentsUiState(
    val hasNotificationAccess: Boolean = false,
    val isForegroundServiceRunning: Boolean = false,
    val isListenerConnected: Boolean = false,
    val canPostNotifications: Boolean = false,
    val isIgnoringBatteryOptimizations: Boolean = false,
    val oemPowerGuide: OemPowerGuide? = null,
    val isOemSetupAcknowledged: Boolean = false,
    val totalToday: Long = 0,
    val observedApps: List<ObservedApp> = emptyList(),
    val selectedPackageName: String? = null,
    val payments: List<IncomingPayment> = emptyList(),
    val pendingSyncCount: Int = 0,
    val session: AdminIdentity? = null,
    val isRestoringSession: Boolean = true,
    val isAuthenticating: Boolean = false,
    val authError: String? = null,
    val isSyncing: Boolean = false,
    val syncMessage: String? = null
)
