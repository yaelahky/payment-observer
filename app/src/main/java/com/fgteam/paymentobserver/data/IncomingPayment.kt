package com.fgteam.paymentobserver.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(
    tableName = "incoming_payments",
    indices = [
        Index(value = ["created_at"], name = "index_payments_created_at"),
        Index(
            value = ["package_name", "created_at"],
            name = "index_payments_package_created"
        )
    ]
)
data class IncomingPayment(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "source_notification_key")
    val sourceNotificationKey: String,
    val amount: Long,
    @ColumnInfo(name = "raw_amount")
    val rawAmount: String,
    val sender: String,
    val title: String,
    val body: String,
    @ColumnInfo(name = "app_name")
    val appName: String,
    @ColumnInfo(name = "package_name")
    val packageName: String,
    @ColumnInfo(name = "created_at")
    val createdAt: LocalDateTime,
    @ColumnInfo(name = "updated_at")
    val updatedAt: LocalDateTime,
    @ColumnInfo(name = "is_sync_to_db", defaultValue = "0")
    val isSyncToDb: Boolean = false,
    @ColumnInfo(name = "synced_at")
    val syncedAt: LocalDateTime? = null,
    @ColumnInfo(name = "sync_attempt_count", defaultValue = "0")
    val syncAttemptCount: Int = 0,
    @ColumnInfo(name = "last_sync_attempt_at")
    val lastSyncAttemptAt: LocalDateTime? = null,
    @ColumnInfo(name = "last_sync_error")
    val lastSyncError: String? = null,
    @ColumnInfo(name = "remote_match_status")
    val remoteMatchStatus: String? = null,
    @ColumnInfo(name = "remote_order_id")
    val remoteOrderId: String? = null,
    @ColumnInfo(name = "remote_matched_at")
    val remoteMatchedAt: LocalDateTime? = null
)
