package com.example.paymentobserver.ui.payments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.paymentobserver.data.Payment
import com.example.paymentobserver.data.PaymentStatus
import com.example.paymentobserver.ui.theme.PaymentObserverTheme
import java.util.Locale

/**
 * Stateful entry point: connects the [PaymentsViewModel] to the stateless [PaymentsContent].
 * The screen only observes state and forwards events — no business logic lives here.
 */
@Composable
fun PaymentsScreen(
    modifier: Modifier = Modifier,
    viewModel: PaymentsViewModel = viewModel(factory = PaymentsViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    PaymentsContent(uiState = uiState, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentsContent(
    uiState: PaymentsUiState,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text("Payment Observer") }) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            when {
                uiState.isLoading -> CircularProgressIndicator()
                uiState.errorMessage != null -> Text(text = uiState.errorMessage)
                else -> PaymentList(payments = uiState.payments)
            }
        }
    }
}

@Composable
private fun PaymentList(
    payments: List<Payment>,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(payments, key = { it.id }) { payment ->
            PaymentCard(payment = payment)
        }
    }
}

@Composable
private fun PaymentCard(
    payment: Payment,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = payment.merchant,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${payment.currency} ${formatAmount(payment.amount)}",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = payment.status.name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun formatAmount(amount: Double): String =
    String.format(Locale.US, "%.2f", amount)

@Preview(showBackground = true)
@Composable
private fun PaymentsContentPreview() {
    PaymentObserverTheme {
        PaymentsContent(
            uiState = PaymentsUiState(
                payments = listOf(
                    Payment("1", "Spotify", 4.99, "USD", PaymentStatus.COMPLETED, 0L),
                    Payment("2", "Amazon", 129.50, "USD", PaymentStatus.PENDING, 0L),
                    Payment("3", "Steam", 59.99, "USD", PaymentStatus.FAILED, 0L)
                )
            )
        )
    }
}
