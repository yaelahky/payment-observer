package com.fgteam.paymentobserver.ui.payments

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.espresso.Espresso.pressBack
import com.fgteam.paymentobserver.data.IncomingPayment
import com.fgteam.paymentobserver.data.ObservedApp
import com.fgteam.paymentobserver.ui.theme.PaymentObserverTheme
import java.time.LocalDateTime
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PaymentsContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun readyObserverShowsStableStatusAndOnlyEnabledAppChips() {
        val now = LocalDateTime.of(2026, 7, 13, 10, 0)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PaymentObserverTheme {
                PaymentsContent(
                    uiState = readyState(now),
                    onGrantAccess = {},
                    onSelectPackage = {},
                    onAppEnabledChange = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithTag("today_total").assertIsDisplayed()
        composeRule.onNode(
            hasTestTag("dashboard_header")
                .and(hasAnyDescendant(hasText("Halo, Admin FGTeam")))
        ).assertIsDisplayed()
        composeRule.onNode(
            hasTestTag("balance_summary")
                .and(hasAnyDescendant(hasText("Halo, Admin FGTeam")))
        ).assertDoesNotExist()
        composeRule.onNodeWithTag("balance_amount").assertTextEquals("Rp0")
        composeRule.onNodeWithTag("observer_active_chip").assertIsDisplayed()
        composeRule.onNode(
            hasTestTag("today_total")
                .and(hasAnyDescendant(hasTestTag("observer_active_chip")))
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Siap menerima notifikasi").assertIsDisplayed()
        composeRule.onNodeWithTag("observer_setup_card").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Logout").assertIsDisplayed()
        composeRule.onNodeWithTag("active_app_chip_com.shopeepay.id").assertIsDisplayed()
        composeRule.onNodeWithTag("active_app_chip_com.shopee.id").assertDoesNotExist()
        composeRule.onNodeWithTag("switch_com.shopeepay.id").assertDoesNotExist()

        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.onNodeWithTag("balance_amount").assertTextEquals("Rp20.000")
        composeRule.onNodeWithTag("observer_active_chip").assertIsDisplayed()
        composeRule.onNodeWithText("Siap menerima notifikasi").assertIsDisplayed()
    }

    @Test
    fun balanceChangeShowsIntermediateValueAndSupportsDecrease() {
        val now = LocalDateTime.of(2026, 7, 13, 10, 0)
        var state by mutableStateOf(readyState(now))
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PaymentObserverTheme {
                PaymentsContent(
                    uiState = state,
                    onGrantAccess = {},
                    onSelectPackage = {},
                    onAppEnabledChange = { _, _ -> }
                )
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.onNodeWithTag("balance_amount").assertTextEquals("Rp20.000")

        state = state.copy(totalToday = 50_000)
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(400)
        assertNotEquals("Rp20.000", balanceText())
        assertNotEquals("Rp50.000", balanceText())
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.onNodeWithTag("balance_amount").assertTextEquals("Rp50.000")

        state = state.copy(totalToday = 5_000)
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(400)
        assertNotEquals("Rp50.000", balanceText())
        assertNotEquals("Rp5.000", balanceText())
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.onNodeWithTag("balance_amount").assertTextEquals("Rp5.000")
    }

    @Test
    fun balanceRetargetDuringAnimationEndsAtLatestAmount() {
        val now = LocalDateTime.of(2026, 7, 13, 10, 0)
        var state by mutableStateOf(readyState(now))
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PaymentObserverTheme {
                PaymentsContent(
                    uiState = state,
                    onGrantAccess = {},
                    onSelectPackage = {},
                    onAppEnabledChange = { _, _ -> }
                )
            }
        }

        composeRule.mainClock.advanceTimeBy(300)
        val amountBeforeRetarget = balanceText()
        assertNotEquals("Rp0", amountBeforeRetarget)
        assertNotEquals("Rp20.000", amountBeforeRetarget)

        state = state.copy(totalToday = 75_000)
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.onNodeWithTag("balance_amount").assertTextEquals("Rp75.000")
    }

    @Test
    fun homeShowsNeutralChipWhenEveryAppIsDisabled() {
        val now = LocalDateTime.of(2026, 7, 13, 10, 0)
        composeRule.setContent {
            PaymentObserverTheme {
                PaymentsContent(
                    uiState = readyState(now).copy(
                        observedApps = readyState(now).observedApps.map {
                            it.copy(isEnabled = false)
                        }
                    ),
                    onGrantAccess = {},
                    onSelectPackage = {},
                    onAppEnabledChange = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithTag("no_active_apps_chip").assertIsDisplayed()
        composeRule.onNodeWithText("Belum ada aplikasi aktif").assertIsDisplayed()
        composeRule.onNodeWithTag("active_app_chip_com.shopeepay.id").assertDoesNotExist()
    }

    @Test
    fun incompleteObserverKeepsConfigurationCardOnHome() {
        val now = LocalDateTime.of(2026, 7, 13, 10, 0)
        composeRule.setContent {
            PaymentObserverTheme {
                PaymentsContent(
                    uiState = readyState(now).copy(
                        hasNotificationAccess = false,
                        isListenerConnected = false
                    ),
                    onGrantAccess = {},
                    onSelectPackage = {},
                    onAppEnabledChange = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithTag("observer_setup_card").assertIsDisplayed()
        composeRule.onNodeWithText("Akses notifikasi belum aktif").assertIsDisplayed()
        composeRule.onNodeWithText("Listener sedang menghubungkan ulang").assertIsDisplayed()
        composeRule.onNodeWithTag("observer_active_chip").assertDoesNotExist()
        composeRule.onNode(
            hasTestTag("today_total")
                .and(hasAnyDescendant(hasTestTag("observer_active_chip")))
        ).assertDoesNotExist()
    }

    @Test
    fun transactionCardsExposeColoredStatusLabelsAndRemoteOrder() {
        val now = LocalDateTime.of(2026, 7, 13, 10, 0)
        val basePayment = IncomingPayment(
            id = "pending",
            sourceNotificationKey = "key-pending",
            amount = 10_000,
            rawAmount = "Rp10.000",
            sender = "BLU BCA",
            title = "Pembayaran masuk",
            body = "Pembayaran sebesar Rp10.000 diterima",
            appName = "ShopeePay",
            packageName = "com.shopeepay.id",
            createdAt = now,
            updatedAt = now
        )
        val payments = listOf(
            basePayment,
            basePayment.copy(
                id = "reserved",
                sourceNotificationKey = "key-reserved",
                isSyncToDb = true,
                remoteMatchStatus = "reserved"
            ),
            basePayment.copy(
                id = "matched",
                sourceNotificationKey = "key-matched",
                isSyncToDb = true,
                remoteMatchStatus = "matched",
                remoteOrderId = "FG-123456"
            ),
            basePayment.copy(
                id = "unmatched",
                sourceNotificationKey = "key-unmatched",
                isSyncToDb = true,
                remoteMatchStatus = "unmatched"
            )
        )

        composeRule.setContent {
            PaymentObserverTheme {
                PaymentsContent(
                    uiState = readyState(now).copy(payments = payments),
                    onGrantAccess = {},
                    onSelectPackage = {},
                    onAppEnabledChange = { _, _ -> }
                )
            }
        }

        listOf("Belum Sync", "Reserved", "Matched", "Synced • Unmatched", "FG-123456")
            .forEach { expectedText ->
                composeRule.onNodeWithTag("payments_list")
                    .performScrollToNode(hasText(expectedText))
                composeRule.onNodeWithText(expectedText).assertIsDisplayed()
            }
    }

    @Test
    fun manageButtonAndBothBackActionsReturnToHome() {
        val now = LocalDateTime.of(2026, 7, 13, 10, 0)
        composeRule.setContent {
            PaymentObserverTheme {
                PaymentsContent(
                    uiState = readyState(now),
                    onGrantAccess = {},
                    onSelectPackage = {},
                    onAppEnabledChange = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithTag("manage_apps_button").performClick()
        composeRule.onNodeWithTag("app_config_screen").assertIsDisplayed()
        composeRule.onNodeWithText("Pilih sumber notifikasi pembayaran").assertIsDisplayed()
        composeRule.onNodeWithTag("app_config_back").performClick()
        composeRule.onNodeWithTag("today_total").assertIsDisplayed()

        composeRule.onNodeWithTag("manage_apps_button").performClick()
        pressBack()
        composeRule.onNodeWithTag("today_total").assertIsDisplayed()
        composeRule.onNodeWithTag("app_config_screen").assertDoesNotExist()
    }

    @Test
    fun switchesUpdateIndependentlyAndHomeChipsReflectChanges() {
        val now = LocalDateTime.of(2026, 7, 13, 10, 0)
        composeRule.setContent {
            var state by remember { mutableStateOf(readyState(now)) }
            PaymentObserverTheme {
                PaymentsContent(
                    uiState = state,
                    onGrantAccess = {},
                    onSelectPackage = {},
                    onAppEnabledChange = { packageName, enabled ->
                        state = state.copy(
                            observedApps = state.observedApps.map { app ->
                                if (app.packageName == packageName) {
                                    app.copy(isEnabled = enabled)
                                } else {
                                    app
                                }
                            }
                        )
                    }
                )
            }
        }

        composeRule.onNodeWithTag("manage_apps_button").performClick()
        composeRule.onNodeWithTag("switch_com.shopeepay.id").assertIsOn()
        composeRule.onNodeWithTag("switch_com.shopee.id").assertIsOff().performClick()
        composeRule.onNodeWithTag("switch_com.shopee.id").assertIsOn()
        composeRule.onNodeWithTag("switch_com.shopeepay.id").assertIsOn()

        composeRule.onNodeWithTag("app_config_back").performClick()
        composeRule.onNodeWithTag("active_app_chip_com.shopeepay.id").assertIsDisplayed()
        composeRule.onNodeWithTag("active_app_chip_com.shopee.id").assertIsDisplayed()
    }

    @Test
    fun configurationUsesSortOrderAndShowsPackageNames() {
        val now = LocalDateTime.of(2026, 7, 13, 10, 0)
        val apps = listOf(
            ObservedApp("id.co.bni.merchant", "BNI Merchant", true, 4, now, now),
            ObservedApp("com.shopee.id", "Shopee", false, 2, now, now),
            ObservedApp("com.shopeepay.id", "ShopeePay", true, 1, now, now),
            ObservedApp("com.shopeepay.merchant.id", "Shopee Partner", true, 3, now, now)
        )
        composeRule.setContent {
            PaymentObserverTheme {
                PaymentsContent(
                    uiState = readyState(now).copy(observedApps = apps),
                    onGrantAccess = {},
                    onSelectPackage = {},
                    onAppEnabledChange = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithTag("manage_apps_button").performClick()
        apps.forEach { app ->
            composeRule.onNodeWithText(app.packageName).assertIsDisplayed()
        }
        val orderedTops = listOf(
            "com.shopeepay.id",
            "com.shopee.id",
            "com.shopeepay.merchant.id",
            "id.co.bni.merchant"
        ).map { packageName ->
            composeRule.onNodeWithTag("config_app_$packageName")
                .fetchSemanticsNode().boundsInRoot.top
        }
        assertTrue(orderedTops.zipWithNext().all { (first, second) -> first < second })
        composeRule.onNodeWithText("Perubahan tersimpan otomatis").assertIsDisplayed()
    }

    private fun readyState(now: LocalDateTime) = PaymentsUiState(
        hasNotificationAccess = true,
        isForegroundServiceRunning = true,
        isListenerConnected = true,
        canPostNotifications = true,
        isIgnoringBatteryOptimizations = true,
        totalToday = 20_000,
        session = AdminIdentity("Admin FGTeam", "admin"),
        observedApps = listOf(
            ObservedApp("com.shopeepay.id", "ShopeePay", true, 1, now, now),
            ObservedApp("com.shopee.id", "Shopee", false, 2, now, now)
        )
    )

    private fun balanceText(): String = composeRule
        .onNodeWithTag("balance_amount")
        .fetchSemanticsNode()
        .config[SemanticsProperties.Text]
        .joinToString(separator = "") { it.text }
}
