package com.fgteam.paymentobserver.ui.payments

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.fgteam.paymentobserver.data.ObservedApp
import com.fgteam.paymentobserver.ui.theme.PaymentObserverTheme
import java.time.LocalDateTime
import org.junit.Rule
import org.junit.Test

class PaymentsContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsTotalListenerStatusAndIndependentAppSwitches() {
        val now = LocalDateTime.of(2026, 7, 13, 10, 0)
        composeRule.setContent {
            PaymentObserverTheme {
                PaymentsContent(
                    uiState = PaymentsUiState(
                        hasNotificationAccess = true,
                        isForegroundServiceRunning = true,
                        isListenerConnected = true,
                        canPostNotifications = true,
                        isIgnoringBatteryOptimizations = true,
                        totalToday = 20_000,
                        observedApps = listOf(
                            ObservedApp("com.shopeepay.id", "ShopeePay", false, 1, now, now),
                            ObservedApp("com.shopee.id", "Shopee", false, 2, now, now)
                        )
                    ),
                    onGrantAccess = {},
                    onSelectPackage = {},
                    onAppEnabledChange = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithTag("today_total").assertIsDisplayed()
        composeRule.onNodeWithText("Rp20.000").assertIsDisplayed()
        composeRule.onNodeWithText("Akses notifikasi aktif").assertIsDisplayed()
        composeRule.onNodeWithText("Service always-on aktif").assertIsDisplayed()
        composeRule.onNodeWithText("Listener terhubung").assertIsDisplayed()
        composeRule.onNodeWithTag("switch_com.shopeepay.id").assertIsOff()
        composeRule.onNodeWithTag("switch_com.shopee.id").assertIsOff()
    }
}
