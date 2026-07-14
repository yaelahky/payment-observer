package com.fgteam.paymentobserver.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "observed_apps")
data class ObservedApp(
    @PrimaryKey
    @ColumnInfo(name = "package_name")
    val packageName: String,
    @ColumnInfo(name = "app_name")
    val appName: String,
    @ColumnInfo(name = "is_enabled", defaultValue = "0")
    val isEnabled: Boolean = false,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int,
    @ColumnInfo(name = "created_at")
    val createdAt: LocalDateTime,
    @ColumnInfo(name = "updated_at")
    val updatedAt: LocalDateTime
) {
    companion object {
        const val SHOPEE_PAY_PACKAGE = "com.shopeepay.id"
        const val SHOPEE_PACKAGE = "com.shopee.id"
        const val SHOPEE_PARTNER_PACKAGE = "com.shopeepay.merchant.id"
        const val BNI_MERCHANT_PACKAGE = "id.co.bni.merchant"
    }
}
