package com.fgteam.paymentobserver.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ObserverRuntimeState(
    val isForegroundServiceRunning: Boolean = false,
    val isListenerConnected: Boolean = false
)

/** Process-local runtime state. Payment ingestion must never be added here. */
object ObserverRuntime {
    private val mutableState = MutableStateFlow(ObserverRuntimeState())
    val state: StateFlow<ObserverRuntimeState> = mutableState.asStateFlow()

    fun setForegroundServiceRunning(running: Boolean) {
        mutableState.value = mutableState.value.copy(isForegroundServiceRunning = running)
    }

    fun setListenerConnected(connected: Boolean) {
        mutableState.value = mutableState.value.copy(isListenerConnected = connected)
    }
}
