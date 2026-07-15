package com.fgteam.paymentobserver.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PaymentObserverDatabaseTest {
    private lateinit var context: Context
    private lateinit var database: PaymentObserverDatabase

    @Before
    fun createDatabase() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, PaymentObserverDatabase::class.java)
            .addCallback(SeedObservedAppsCallback())
            .build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun datetimeColumnsAreTextAndMillisecondsRoundTrip() = runBlocking {
        val time = dateTime(10, 15, 0, 123)
        database.paymentDao().insert(payment("one", ObservedApp.SHOPEE_PAY_PACKAGE, time))

        val loaded = database.paymentDao().observeAll().first().single()
        assertEquals(time, loaded.createdAt)
        assertEquals(time, loaded.updatedAt)

        val cursor = database.openHelper.readableDatabase.query("PRAGMA table_info(incoming_payments)")
        val types = buildMap {
            cursor.use {
                while (it.moveToNext()) {
                    put(it.getString(it.getColumnIndexOrThrow("name")), it.getString(it.getColumnIndexOrThrow("type")))
                }
            }
        }
        assertEquals("TEXT", types["created_at"])
        assertEquals("TEXT", types["updated_at"])
    }

    @Test
    fun orderingFilteringDailyTotalAndDuplicateInsertWork() = runBlocking {
        val shopeePayMorning = payment("morning", ObservedApp.SHOPEE_PAY_PACKAGE, dateTime(8, 0))
        val shopeeNoon = payment("noon", ObservedApp.SHOPEE_PACKAGE, dateTime(12, 0))
        val previousDay = payment(
            "yesterday",
            ObservedApp.SHOPEE_PACKAGE,
            LocalDateTime.of(2026, 7, 12, 23, 59)
        )
        database.paymentDao().insert(shopeePayMorning)
        database.paymentDao().insert(shopeeNoon)
        database.paymentDao().insert(previousDay)

        assertEquals(listOf("noon", "morning", "yesterday"), database.paymentDao().observeAll().first().map { it.id })
        assertEquals(listOf("morning"), database.paymentDao().observeByPackage(ObservedApp.SHOPEE_PAY_PACKAGE).first().map { it.id })
        assertEquals(
            20_000,
            database.paymentDao().observeTotalBetween(
                LocalDateTime.of(2026, 7, 13, 0, 0),
                LocalDateTime.of(2026, 7, 14, 0, 0)
            ).first()
        )
        assertEquals(-1L, database.paymentDao().insert(shopeePayMorning))
    }

    @Test
    fun concurrentDuplicateInsertCreatesExactlyOneRow() = runBlocking {
        val duplicate = payment("same-notification", ObservedApp.SHOPEE_PAY_PACKAGE, dateTime(9, 0))

        val results = List(20) {
            async(Dispatchers.IO) { database.paymentDao().insert(duplicate) }
        }.awaitAll()

        assertEquals(1, results.count { it != -1L })
        assertEquals(19, results.count { it == -1L })
        assertEquals(listOf("same-notification"), database.paymentDao().observeAll().first().map { it.id })
    }

    @Test
    fun whitelistDefaultsOffAndToggleOnlyChangesUpdatedAt() = runBlocking {
        val dao = database.observedAppDao()
        val initial = dao.get(ObservedApp.SHOPEE_PAY_PACKAGE)!!
        assertFalse(initial.isEnabled)

        val changedAt = dateTime(13, 45, 2, 7)
        dao.setEnabled(initial.packageName, true, changedAt)
        val changed = dao.get(initial.packageName)!!

        assertTrue(changed.isEnabled)
        assertEquals(initial.createdAt, changed.createdAt)
        assertEquals(changedAt, changed.updatedAt)
    }

    @Test
    fun dataSurvivesDatabaseRestart() = runBlocking {
        val databaseName = "restart-test.db"
        context.deleteDatabase(databaseName)
        val first = Room.databaseBuilder(context, PaymentObserverDatabase::class.java, databaseName)
            .addCallback(SeedObservedAppsCallback())
            .build()
        first.paymentDao().insert(payment("persistent", ObservedApp.SHOPEE_PACKAGE, dateTime(14, 0)))
        first.close()

        val reopened = Room.databaseBuilder(context, PaymentObserverDatabase::class.java, databaseName)
            .addCallback(SeedObservedAppsCallback())
            .build()
        assertEquals("persistent", reopened.paymentDao().observeAll().first().single().id)
        assertEquals(4, reopened.observedAppDao().observeAll().first().size)
        reopened.close()
        context.deleteDatabase(databaseName)
        Unit
    }

    private fun payment(id: String, packageName: String, createdAt: LocalDateTime) = IncomingPayment(
        id = id,
        sourceNotificationKey = "key-$id",
        amount = 10_000,
        rawAmount = "Rp10.000",
        sender = "ANANDA",
        title = "Saldo ShopeePay Diterima",
        body = "Rp10.000 telah diterima dari ANANDA.",
        appName = if (packageName == ObservedApp.SHOPEE_PACKAGE) "Shopee" else "ShopeePay",
        packageName = packageName,
        createdAt = createdAt,
        updatedAt = createdAt
    )

    private fun dateTime(hour: Int, minute: Int, second: Int = 0, millis: Int = 0) =
        LocalDateTime.of(2026, 7, 13, hour, minute, second, millis * 1_000_000)
}
