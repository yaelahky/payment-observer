package com.fgteam.paymentobserver.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
interface PaymentDao {
    @Query("SELECT * FROM incoming_payments ORDER BY created_at DESC")
    fun observeAll(): Flow<List<IncomingPayment>>

    @Query(
        "SELECT * FROM incoming_payments " +
            "WHERE package_name = :packageName ORDER BY created_at DESC"
    )
    fun observeByPackage(packageName: String): Flow<List<IncomingPayment>>

    @Query(
        "SELECT COALESCE(SUM(amount), 0) FROM incoming_payments " +
            "WHERE created_at >= :todayStart AND created_at < :tomorrowStart"
    )
    fun observeTotalBetween(
        todayStart: LocalDateTime,
        tomorrowStart: LocalDateTime
    ): Flow<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(payment: IncomingPayment): Long

    @Query("SELECT COUNT(*) FROM incoming_payments WHERE is_sync_to_db = 0")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM incoming_payments WHERE is_sync_to_db = 0 ORDER BY created_at ASC")
    suspend fun getPending(): List<IncomingPayment>

    @Query("SELECT id FROM incoming_payments WHERE is_sync_to_db = 1 ORDER BY created_at DESC")
    suspend fun getSyncedIds(): List<String>

    @Query(
        "UPDATE incoming_payments SET sync_attempt_count = sync_attempt_count + 1, " +
            "last_sync_attempt_at = :attemptedAt, last_sync_error = :error WHERE id = :id"
    )
    suspend fun recordSyncAttempt(id: String, attemptedAt: LocalDateTime, error: String?)

    @Query(
        "UPDATE incoming_payments SET is_sync_to_db = 1, synced_at = :syncedAt, " +
            "last_sync_error = NULL, remote_match_status = :matchStatus, " +
            "remote_order_id = :orderId, remote_matched_at = :matchedAt WHERE id = :id"
    )
    suspend fun markSynced(
        id: String,
        syncedAt: LocalDateTime,
        matchStatus: String,
        orderId: String?,
        matchedAt: LocalDateTime?
    )

    @Query(
        "UPDATE incoming_payments SET is_sync_to_db = :isSynced, " +
            "remote_match_status = :matchStatus, remote_order_id = :orderId, " +
            "remote_matched_at = :matchedAt WHERE id = :id"
    )
    suspend fun updateRemoteStatus(
        id: String,
        isSynced: Boolean,
        matchStatus: String?,
        orderId: String?,
        matchedAt: LocalDateTime?
    )
}
