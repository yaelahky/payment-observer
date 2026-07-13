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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.paymentobserver.data.IncomingPayment
import com.example.paymentobserver.ui.theme.PaymentObserverTheme
import com.example.paymentobserver.util.NotificationAccess
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Stateful entry point. Re-checks notification access whenever the screen resumes (e.g. after the
 * user comes back from the system Settings), then delegates rendering to [PaymentsContent].
 */
@Composable
fun PaymentsScreen(
    modifier: Modifier = Modifier,
    viewModel: PaymentsViewModel = viewModel(factory = PaymentsViewModel.Factory)
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.setAccessGranted(NotificationAccess.isEnabled(context))
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    PaymentsContent(
        uiState = uiState,
        onGrantAccess = { context.startActivity(NotificationAccess.settingsIntent()) },
        onClear = viewModel::clearPayments,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentsContent(
    uiState: PaymentsUiState,
    onGrantAccess: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Payment Observer") },
                actions = {
                    if (uiState.payments.isNotEmpty()) {
                        TextButton(onClick = onClear) { Text("Hapus") }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (!uiState.hasNotificationAccess) {
                PermissionCard(
                    onGrantAccess = onGrantAccess,
                    modifier = Modifier.padding(16.dp)
                )
            }

            if (uiState.payments.isEmpty()) {
                EmptyState(
                    hasAccess = uiState.hasNotificationAccess,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                PaymentList(payments = uiState.payments)
            }
        }
    }
}

@Composable
private fun PermissionCard(
    onGrantAccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Akses notifikasi diperlukan",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = "Agar dapat membaca notifikasi saldo masuk dari ShopeePay, " +
                    "aktifkan akses notifikasi untuk aplikasi ini.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Button(onClick = onGrantAccess) {
                Text("Buka Pengaturan Akses Notifikasi")
            }
        }
    }
}

@Composable
private fun EmptyState(
    hasAccess: Boolean,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = if (hasAccess) {
                "Menunggu notifikasi saldo masuk dari ShopeePay…"
            } else {
                "Belum ada data. Aktifkan akses notifikasi untuk mulai mendengarkan."
            },
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(32.dp)
        )
    }
}

@Composable
private fun PaymentList(
    payments: List<IncomingPayment>,
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
    payment: IncomingPayment,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = formatRupiah(payment.amount),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "dari ${payment.sender}",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = formatTimestamp(payment.timestamp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatRupiah(amount: Long): String =
    "Rp" + NumberFormat.getInstance(Locale("in", "ID")).format(amount)

private fun formatTimestamp(timestamp: Long): String =
    SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("in", "ID")).format(Date(timestamp))

@Preview(showBackground = true)
@Composable
private fun PaymentsContentPreview() {
    PaymentObserverTheme {
        PaymentsContent(
            uiState = PaymentsUiState(
                hasNotificationAccess = true,
                payments = listOf(
                    IncomingPayment(
                        id = "1",
                        amount = 10,
                        rawAmount = "Rp10",
                        sender = "ANANDA RIZKY YULIANSYAH",
                        title = "Saldo ShopeePay Diterima",
                        text = "Rp10 telah diterima dari ANANDA RIZKY YULIANSYAH. Klik untuk cek rinciannya",
                        packageName = "com.shopee.id",
                        timestamp = 1_718_000_000_000
                    )
                )
            ),
            onGrantAccess = {},
            onClear = {}
        )
    }
}
