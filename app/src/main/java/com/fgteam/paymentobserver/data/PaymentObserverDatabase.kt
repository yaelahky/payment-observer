package com.fgteam.paymentobserver.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import java.time.LocalDateTime

@Database(
    entities = [IncomingPayment::class, ObservedApp::class],
    version = 3,
    exportSchema = true
)
@TypeConverters(LocalDateTimeConverter::class)
abstract class PaymentObserverDatabase : RoomDatabase() {
    abstract fun paymentDao(): PaymentDao
    abstract fun observedAppDao(): ObservedAppDao

    companion object {
        private const val DATABASE_NAME = "payment_observer.db"

        @Volatile
        private var instance: PaymentObserverDatabase? = null

        fun getInstance(context: Context): PaymentObserverDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PaymentObserverDatabase::class.java,
                    DATABASE_NAME
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .addCallback(SeedObservedAppsCallback())
                    .build()
                    .also { instance = it }
            }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE incoming_payments ADD COLUMN is_sync_to_db INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE incoming_payments ADD COLUMN synced_at TEXT NULL")
                db.execSQL("ALTER TABLE incoming_payments ADD COLUMN sync_attempt_count INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE incoming_payments ADD COLUMN last_sync_attempt_at TEXT NULL")
                db.execSQL("ALTER TABLE incoming_payments ADD COLUMN last_sync_error TEXT NULL")
                db.execSQL("ALTER TABLE incoming_payments ADD COLUMN remote_match_status TEXT NULL")
                db.execSQL("ALTER TABLE incoming_payments ADD COLUMN remote_order_id TEXT NULL")
                db.execSQL("ALTER TABLE incoming_payments ADD COLUMN remote_matched_at TEXT NULL")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val now = LocalDateTimeConverter.fromLocalDateTime(LocalDateTime.now())
                    ?: error("Local datetime conversion failed")
                insertObservedApp(
                    db,
                    ObservedApp.SHOPEE_PARTNER_PACKAGE,
                    "Shopee Partner",
                    sortOrder = 3,
                    now = now
                )
                insertObservedApp(
                    db,
                    ObservedApp.BNI_MERCHANT_PACKAGE,
                    "BNI Merchant",
                    sortOrder = 4,
                    now = now
                )
            }
        }

        private fun insertObservedApp(
            db: SupportSQLiteDatabase,
            packageName: String,
            appName: String,
            sortOrder: Int,
            now: String
        ) {
            db.execSQL(
                "INSERT OR IGNORE INTO observed_apps " +
                    "(package_name, app_name, is_enabled, sort_order, created_at, updated_at) " +
                    "VALUES (?, ?, 0, ?, ?, ?)",
                arrayOf(packageName, appName, sortOrder, now, now)
            )
        }
    }
}

internal class SeedObservedAppsCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        val now = LocalDateTimeConverter.fromLocalDateTime(LocalDateTime.now())
            ?: error("Local datetime conversion failed")
        val statement = db.compileStatement(
            "INSERT INTO observed_apps " +
                "(package_name, app_name, is_enabled, sort_order, created_at, updated_at) " +
                "VALUES (?, ?, 0, ?, ?, ?)"
        )
        seed(statement, ObservedApp.SHOPEE_PAY_PACKAGE, "ShopeePay", 1, now)
        seed(statement, ObservedApp.SHOPEE_PACKAGE, "Shopee", 2, now)
        seed(statement, ObservedApp.SHOPEE_PARTNER_PACKAGE, "Shopee Partner", 3, now)
        seed(statement, ObservedApp.BNI_MERCHANT_PACKAGE, "BNI Merchant", 4, now)
    }

    private fun seed(
        statement: androidx.sqlite.db.SupportSQLiteStatement,
        packageName: String,
        appName: String,
        sortOrder: Int,
        now: String
    ) {
        statement.clearBindings()
        statement.bindString(1, packageName)
        statement.bindString(2, appName)
        statement.bindLong(3, sortOrder.toLong())
        statement.bindString(4, now)
        statement.bindString(5, now)
        statement.executeInsert()
    }
}
