package com.fgteam.paymentobserver

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.fgteam.paymentobserver.ui.payments.PaymentsScreen
import com.fgteam.paymentobserver.ui.theme.PaymentObserverTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                scrim = Color.TRANSPARENT,
                darkScrim = Color.TRANSPARENT
            )
        )
        setContent {
            PaymentObserverTheme {
                PaymentsScreen()
            }
        }
    }
}
