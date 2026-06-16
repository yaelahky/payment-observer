package com.example.paymentobserver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.paymentobserver.ui.payments.PaymentsScreen
import com.example.paymentobserver.ui.theme.PaymentObserverTheme

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
