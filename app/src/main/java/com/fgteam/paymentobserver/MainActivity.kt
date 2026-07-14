package com.fgteam.paymentobserver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.fgteam.paymentobserver.ui.payments.PaymentsScreen
import com.fgteam.paymentobserver.ui.theme.PaymentObserverTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PaymentObserverTheme {
                PaymentsScreen()
            }
        }
    }
}
