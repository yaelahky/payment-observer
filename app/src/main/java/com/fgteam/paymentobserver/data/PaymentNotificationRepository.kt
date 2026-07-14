package com.fgteam.paymentobserver.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

interface PaymentNotificationRepository {
    fun observePayments(packageName: String?): Flow<List<IncomingPayment>>
    fun observeTotalBetween(
        todayStart: LocalDateTime,
        tomorrowStart: LocalDateTime
    ): Flow<Long>

    fun observeApps(): Flow<List<ObservedApp>>
    fun observePendingCount(): Flow<Int>
    suspend fun getObservedApp(packageName: String): ObservedApp?
    suspend fun add(payment: IncomingPayment): Boolean
    suspend fun getPending(): List<IncomingPayment>
    suspend fun getSyncedIds(): List<String>
    suspend fun recordSyncAttempt(id: String, attemptedAt: LocalDateTime, error: String?)
    suspend fun markSynced(
        id: String,
        syncedAt: LocalDateTime,
        matchStatus: String,
        orderId: String?,
        matchedAt: LocalDateTime?
    )
    suspend fun updateRemoteStatus(
        id: String,
        isSynced: Boolean,
        matchStatus: String?,
        orderId: String?,
        matchedAt: LocalDateTime?
    )
    suspend fun setAppEnabled(packageName: String, enabled: Boolean, now: LocalDateTime)
}

class RoomPaymentNotificationRepository(
    private val paymentDao: PaymentDao,
    private val observedAppDao: ObservedAppDao
) : PaymentNotificationRepository {
    override fun observePayments(packageName: String?): Flow<List<IncomingPayment>> =
        packageName?.let(paymentDao::observeByPackage) ?: paymentDao.observeAll()

    override fun observeTotalBetween(
        todayStart: LocalDateTime,
        tomorrowStart: LocalDateTime
    ): Flow<Long> = paymentDao.observeTotalBetween(todayStart, tomorrowStart)

    override fun observeApps(): Flow<List<ObservedApp>> = observedAppDao.observeAll()

    override fun observePendingCount(): Flow<Int> = paymentDao.observePendingCount()

    override suspend fun getObservedApp(packageName: String): ObservedApp? =
        observedAppDao.get(packageName)

    override suspend fun add(payment: IncomingPayment): Boolean = paymentDao.insert(payment) != -1L

    override suspend fun getPending(): List<IncomingPayment> = paymentDao.getPending()

    override suspend fun getSyncedIds(): List<String> = paymentDao.getSyncedIds()

    override suspend fun recordSyncAttempt(id: String, attemptedAt: LocalDateTime, error: String?) =
        paymentDao.recordSyncAttempt(id, attemptedAt, error)

    override suspend fun markSynced(
        id: String,
        syncedAt: LocalDateTime,
        matchStatus: String,
        orderId: String?,
        matchedAt: LocalDateTime?
    ) = paymentDao.markSynced(id, syncedAt, matchStatus, orderId, matchedAt)

    override suspend fun updateRemoteStatus(
        id: String,
        isSynced: Boolean,
        matchStatus: String?,
        orderId: String?,
        matchedAt: LocalDateTime?
    ) = paymentDao.updateRemoteStatus(id, isSynced, matchStatus, orderId, matchedAt)

    override suspend fun setAppEnabled(
        packageName: String,
        enabled: Boolean,
        now: LocalDateTime
    ) {
        observedAppDao.setEnabled(packageName, enabled, now)
    }

    companion object {
        @Volatile
        private var instance: RoomPaymentNotificationRepository? = null

        fun getInstance(context: Context): RoomPaymentNotificationRepository =
            instance ?: synchronized(this) {
                instance ?: PaymentObserverDatabase.getInstance(context).let { database ->
                    RoomPaymentNotificationRepository(
                        paymentDao = database.paymentDao(),
                        observedAppDao = database.observedAppDao()
                    )
                }.also { instance = it }
            }
    }
}
