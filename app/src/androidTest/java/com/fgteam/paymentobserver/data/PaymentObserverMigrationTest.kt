package com.fgteam.paymentobserver.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PaymentObserverMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        PaymentObserverDatabase::class.java
    )

    @Test
    fun migrationFrom2To3AddsDisabledMerchantAppsWithoutRemovingExistingApps() {
        helper.createDatabase(DATABASE_NAME, 2).use { database ->
            insertExistingApp(database, ObservedApp.SHOPEE_PAY_PACKAGE, "ShopeePay", 1)
            insertExistingApp(database, ObservedApp.SHOPEE_PACKAGE, "Shopee", 2)
        }

        helper.runMigrationsAndValidate(
            DATABASE_NAME,
            3,
            true,
            PaymentObserverDatabase.MIGRATION_2_3
        ).use { database ->
            database.query(
                "SELECT package_name, is_enabled FROM observed_apps ORDER BY sort_order"
            ).use { cursor ->
                val apps = buildList {
                    while (cursor.moveToNext()) {
                        add(cursor.getString(0) to cursor.getInt(1))
                    }
                }
                assertEquals(
                    listOf(
                        ObservedApp.SHOPEE_PAY_PACKAGE to 0,
                        ObservedApp.SHOPEE_PACKAGE to 0,
                        ObservedApp.SHOPEE_PARTNER_PACKAGE to 0,
                        ObservedApp.BNI_MERCHANT_PACKAGE to 0
                    ),
                    apps
                )
            }
        }
    }

    private fun insertExistingApp(
        database: SupportSQLiteDatabase,
        packageName: String,
        appName: String,
        sortOrder: Int
    ) {
        database.execSQL(
            "INSERT INTO observed_apps " +
                "(package_name, app_name, is_enabled, sort_order, created_at, updated_at) " +
                "VALUES (?, ?, 0, ?, ?, ?)",
            arrayOf(packageName, appName, sortOrder, NOW, NOW)
        )
    }

    companion object {
        private const val DATABASE_NAME = "payment-observer-migration-test"
        private const val NOW = "2026-07-14 10:15:00.123"
    }
}
