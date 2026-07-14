package com.fgteam.paymentobserver.sync

import android.content.Context
import com.fgteam.paymentobserver.auth.AuthenticationRequiredException
import com.fgteam.paymentobserver.auth.ApiException
import com.fgteam.paymentobserver.data.RoomPaymentNotificationRepository
import com.fgteam.paymentobserver.network.PaymentObserverApi
import java.io.IOException
import java.time.LocalDateTime

enum class SyncOutcome { COMPLETE, AUTH_REQUIRED, RETRY }

class PaymentSyncRepository private constructor(context: Context) {
    private val local = RoomPaymentNotificationRepository.getInstance(context)
    private val api = PaymentObserverApi.getInstance(context)

    suspend fun syncPending(): SyncOutcome {
        val pending = local.getPending()
        for (payment in pending) {
            val attemptedAt = LocalDateTime.now()
            local.recordSyncAttempt(payment.id, attemptedAt, null)
            try {
                val remote = api.postPayment(payment)
                local.markSynced(
                    id = payment.id,
                    syncedAt = LocalDateTime.now(),
                    matchStatus = remote.matchStatus,
                    orderId = remote.orderId,
                    matchedAt = remote.matchedAt
                )
            } catch (error: AuthenticationRequiredException) {
                local.recordSyncAttempt(payment.id, attemptedAt, error.message)
                return SyncOutcome.AUTH_REQUIRED
            } catch (error: IOException) {
                local.recordSyncAttempt(payment.id, attemptedAt, error.message ?: "Koneksi gagal")
                return SyncOutcome.RETRY
            } catch (error: ApiException) {
                local.recordSyncAttempt(payment.id, attemptedAt, error.message ?: "API menolak payment")
                if (error.statusCode != 409) return SyncOutcome.RETRY
            } catch (error: Exception) {
                local.recordSyncAttempt(payment.id, attemptedAt, error.message ?: "Sinkronisasi gagal")
                return SyncOutcome.RETRY
            }
        }
        return SyncOutcome.COMPLETE
    }

    suspend fun reconcile(): SyncOutcome {
        val syncOutcome = syncPending()
        if (syncOutcome != SyncOutcome.COMPLETE) return syncOutcome
        val ids = local.getSyncedIds()
        return try {
            ids.chunked(500).forEach { batch ->
                api.getStatuses(batch).forEach { remote ->
                    val localStatus = when (remote.status) {
                        "synced_unmatched" -> "unmatched"
                        "reserved" -> "reserved"
                        "matched" -> "matched"
                        else -> null
                    }
                    local.updateRemoteStatus(
                        id = remote.id,
                        isSynced = remote.status != "not_synced",
                        matchStatus = localStatus,
                        orderId = remote.orderId,
                        matchedAt = remote.matchedAt
                    )
                }
            }
            SyncOutcome.COMPLETE
        } catch (_: AuthenticationRequiredException) {
            SyncOutcome.AUTH_REQUIRED
        } catch (_: Exception) {
            SyncOutcome.RETRY
        }
    }

    companion object {
        @Volatile private var instance: PaymentSyncRepository? = null

        fun getInstance(context: Context): PaymentSyncRepository = instance ?: synchronized(this) {
            instance ?: PaymentSyncRepository(context.applicationContext).also { instance = it }
        }
    }
}
