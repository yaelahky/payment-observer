package com.fgteam.paymentobserver.data

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
interface ObservedAppDao {
    @Query("SELECT * FROM observed_apps ORDER BY sort_order ASC")
    fun observeAll(): Flow<List<ObservedApp>>

    @Query("SELECT * FROM observed_apps WHERE package_name = :packageName LIMIT 1")
    suspend fun get(packageName: String): ObservedApp?

    @Query(
        "UPDATE observed_apps SET is_enabled = :enabled, updated_at = :currentDateTime " +
            "WHERE package_name = :packageName"
    )
    suspend fun setEnabled(
        packageName: String,
        enabled: Boolean,
        currentDateTime: LocalDateTime
    ): Int
}
