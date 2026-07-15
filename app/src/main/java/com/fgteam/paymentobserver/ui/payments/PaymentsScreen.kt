package com.fgteam.paymentobserver.ui.payments

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import com.fgteam.paymentobserver.data.IncomingPayment
import com.fgteam.paymentobserver.data.LocalDateTimeConverter
import com.fgteam.paymentobserver.data.ObservedApp
import com.fgteam.paymentobserver.ui.theme.PaymentObserverTheme
import com.fgteam.paymentobserver.util.NotificationAccess
import com.fgteam.paymentobserver.service.ObserverForegroundService
import com.fgteam.paymentobserver.util.OemPowerGuide
import com.fgteam.paymentobserver.util.ObserverSetup
import java.text.NumberFormat
import java.time.LocalDateTime
import java.util.Locale

@Composable
fun PaymentsScreen(
    modifier: Modifier = Modifier,
    viewModel: PaymentsViewModel = viewModel(factory = PaymentsViewModel.Factory)
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    fun refreshObserverStatus() {
        val status = ObserverSetup.status(context)
        if (status.hasNotificationAccess) {
            runCatching { ObserverForegroundService.start(context) }
        }
        viewModel.onResume(status)
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshObserverStatus() }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshObserverStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    when {
        uiState.isRestoringSession -> Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) { CircularProgressIndicator() }
        uiState.session == null -> LoginContent(
            isLoading = uiState.isAuthenticating,
            error = uiState.authError,
            onLogin = viewModel::login,
            modifier = modifier
        )
        else -> PaymentsContent(
            uiState = uiState,
            onGrantAccess = { context.startActivity(NotificationAccess.settingsIntent()) },
            onEnableNotifications = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    ObserverSetup.openAppNotificationSettings(context)
                }
            },
            onDisableBatteryOptimization = { ObserverSetup.openBatteryExemption(context) },
            onOpenOemSettings = { guide -> ObserverSetup.openOemPowerSettings(context, guide) },
            onConfirmOemSetup = {
                ObserverSetup.acknowledgeOemSetup(context)
                refreshObserverStatus()
            },
            onSelectPackage = viewModel::selectPackage,
            onAppEnabledChange = viewModel::setAppEnabled,
            onLogout = viewModel::logout,
            onSync = viewModel::syncAndReconcile,
            modifier = modifier
        )
    }
}

@Composable
private fun LoginContent(
    isLoading: Boolean,
    error: String?,
    onLogin: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Payment Observer", style = MaterialTheme.typography.headlineSmall)
                Text("Masuk menggunakan akun FGTeam dengan role admin.")
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    enabled = !isLoading,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(
                    onClick = { onLogin(email, password) },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isLoading) CircularProgressIndicator(modifier = Modifier.width(20.dp))
                    else Text("Masuk")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PaymentsContent(
    uiState: PaymentsUiState,
    onGrantAccess: () -> Unit,
    modifier: Modifier = Modifier,
    onEnableNotifications: () -> Unit = {},
    onDisableBatteryOptimization: () -> Unit = {},
    onOpenOemSettings: (OemPowerGuide) -> Unit = {},
    onConfirmOemSetup: () -> Unit = {},
    onSelectPackage: (String?) -> Unit,
    onAppEnabledChange: (String, Boolean) -> Unit,
    onLogout: () -> Unit = {},
    onSync: () -> Unit = {}
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Payment Observer") },
                actions = {
                    TextButton(onClick = onLogout) { Text("Logout") }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { AdminSessionCard(uiState) }
            item { TodayTotalCard(uiState.totalToday) }
            item {
                ListenerStatusCard(
                    uiState = uiState,
                    onGrantAccess = onGrantAccess,
                    onEnableNotifications = onEnableNotifications,
                    onDisableBatteryOptimization = onDisableBatteryOptimization,
                    onOpenOemSettings = onOpenOemSettings,
                    onConfirmOemSetup = onConfirmOemSetup
                )
            }
            item {
                ObservedAppsCard(
                    apps = uiState.observedApps,
                    onAppEnabledChange = onAppEnabledChange
                )
            }
            item {
                PaymentFilters(
                    apps = uiState.observedApps,
                    selectedPackageName = uiState.selectedPackageName,
                    onSelectPackage = onSelectPackage
                )
            }
            item {
                Button(
                    onClick = onSync,
                    enabled = !uiState.isSyncing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (uiState.isSyncing) "Menyinkronkan..." else "Sinkronkan / Cek Status")
                }
                uiState.syncMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            if (uiState.payments.isEmpty()) {
                item { EmptyState(uiState.hasNotificationAccess) }
            } else {
                items(uiState.payments, key = IncomingPayment::id) { payment ->
                    PaymentCard(payment)
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun AdminSessionCard(uiState: PaymentsUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(uiState.session?.fullName ?: "Admin", fontWeight = FontWeight.Bold)
            Text("Session admin aktif", color = MaterialTheme.colorScheme.primary)
            Text("Belum sync: ${uiState.pendingSyncCount}")
        }
    }
}

@Composable
private fun TodayTotalCard(totalToday: Long) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("today_total"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Total hari ini", style = MaterialTheme.typography.labelLarge)
            Text(
                formatRupiah(totalToday),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ListenerStatusCard(
    uiState: PaymentsUiState,
    onGrantAccess: () -> Unit,
    onEnableNotifications: () -> Unit,
    onDisableBatteryOptimization: () -> Unit,
    onOpenOemSettings: (OemPowerGuide) -> Unit,
    onConfirmOemSetup: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Status always-on", style = MaterialTheme.typography.titleMedium)
            ObserverStatusLine(
                ready = uiState.hasNotificationAccess,
                readyText = "Akses notifikasi aktif",
                missingText = "Akses notifikasi belum aktif"
            )
            if (!uiState.hasNotificationAccess) {
                Text("Aktifkan akses agar aplikasi dapat membaca notifikasi pembayaran.")
                Button(onClick = onGrantAccess) { Text("Buka Pengaturan Akses Notifikasi") }
            }
            ObserverStatusLine(
                ready = uiState.isForegroundServiceRunning,
                readyText = "Service always-on aktif",
                missingText = "Service always-on belum aktif"
            )
            ObserverStatusLine(
                ready = uiState.isListenerConnected,
                readyText = "Listener terhubung",
                missingText = "Listener sedang menghubungkan ulang"
            )
            ObserverStatusLine(
                ready = uiState.canPostNotifications,
                readyText = "Notifikasi status diizinkan",
                missingText = "Notifikasi status belum diizinkan"
            )
            if (!uiState.canPostNotifications) {
                Button(onClick = onEnableNotifications) { Text("Izinkan Notifikasi Status") }
            }
            ObserverStatusLine(
                ready = uiState.isIgnoringBatteryOptimizations,
                readyText = "Optimasi baterai dinonaktifkan",
                missingText = "Optimasi baterai masih aktif"
            )
            if (!uiState.isIgnoringBatteryOptimizations) {
                Button(onClick = onDisableBatteryOptimization) {
                    Text("Izinkan Berjalan Tanpa Batas")
                }
            }
            uiState.oemPowerGuide?.let { guide ->
                ObserverStatusLine(
                    ready = uiState.isOemSetupAcknowledged,
                    readyText = "Pengaturan ${guide.name} sudah dikonfirmasi",
                    missingText = "Periksa pengaturan ${guide.name}"
                )
                if (!uiState.isOemSetupAcknowledged) {
                    Text(guide.instructions)
                    Button(onClick = { onOpenOemSettings(guide) }) {
                        Text("Buka Pengaturan Perangkat")
                    }
                    TextButton(onClick = onConfirmOemSetup) {
                        Text("Saya Sudah Mengaktifkannya")
                    }
                }
            }
        }
    }
}

@Composable
private fun ObserverStatusLine(ready: Boolean, readyText: String, missingText: String) {
    Text(
        text = if (ready) readyText else missingText,
        color = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun ObservedAppsCard(
    apps: List<ObservedApp>,
    onAppEnabledChange: (String, Boolean) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Aplikasi yang dipantau", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            apps.forEach { app ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(app.appName, fontWeight = FontWeight.SemiBold)
                        Text(
                            app.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = app.isEnabled,
                        onCheckedChange = { enabled ->
                            onAppEnabledChange(app.packageName, enabled)
                        },
                        modifier = Modifier.testTag("switch_${app.packageName}")
                    )
                }
            }
        }
    }
}

@Composable
private fun PaymentFilters(
    apps: List<ObservedApp>,
    selectedPackageName: String?,
    onSelectPackage: (String?) -> Unit
) {
    Column {
        Text("Filter transaksi", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedPackageName == null,
                onClick = { onSelectPackage(null) },
                label = { Text("Semua") }
            )
            apps.forEach { app ->
                FilterChip(
                    selected = selectedPackageName == app.packageName,
                    onClick = { onSelectPackage(app.packageName) },
                    label = { Text(app.appName) }
                )
            }
        }
    }
}

@Composable
private fun EmptyState(hasAccess: Boolean) {
    Text(
        text = if (hasAccess) {
            "Belum ada transaksi untuk filter ini. Pastikan aplikasi sumber sudah diaktifkan."
        } else {
            "Belum ada data. Aktifkan akses notifikasi untuk mulai mendengarkan."
        },
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(32.dp).testTag("empty_state")
    )
}

@Composable
private fun PaymentCard(payment: IncomingPayment) {
    Card(modifier = Modifier.fillMaxWidth().testTag("payment_${payment.id}")) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(payment.appName, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text(
                    payment.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                formatRupiah(payment.amount),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text("dari ${payment.sender}", style = MaterialTheme.typography.bodyLarge)
            Text(
                LocalDateTimeConverter.fromLocalDateTime(payment.createdAt).orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                when {
                    !payment.isSyncToDb -> "Belum Sync"
                    payment.remoteMatchStatus == "matched" -> "Matched • ${payment.remoteOrderId.orEmpty()}"
                    payment.remoteMatchStatus == "reserved" -> "Reserved"
                    else -> "Synced • Unmatched"
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (payment.isSyncToDb) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error
            )
        }
    }
}

private fun formatRupiah(amount: Long): String =
    "Rp" + NumberFormat.getInstance(Locale("in", "ID")).format(amount)

@Preview(showBackground = true)
@Composable
private fun PaymentsContentPreview() {
    val createdAt = LocalDateTime.of(2026, 7, 13, 10, 15, 0, 123_000_000)
    val apps = listOf(
        ObservedApp("com.shopeepay.id", "ShopeePay", true, 1, createdAt, createdAt),
        ObservedApp("com.shopee.id", "Shopee", false, 2, createdAt, createdAt)
    )
    PaymentObserverTheme {
        PaymentsContent(
            uiState = PaymentsUiState(
                hasNotificationAccess = true,
                isForegroundServiceRunning = true,
                isListenerConnected = true,
                canPostNotifications = true,
                isIgnoringBatteryOptimizations = true,
                totalToday = 10_000,
                observedApps = apps,
                payments = listOf(
                    IncomingPayment(
                        id = "preview",
                        sourceNotificationKey = "preview-key",
                        amount = 10_000,
                        rawAmount = "Rp10.000",
                        sender = "ANANDA RIZKY YULIANSYAH",
                        title = "Saldo ShopeePay Diterima",
                        body = "Rp10.000 telah diterima dari ANANDA RIZKY YULIANSYAH.",
                        appName = "ShopeePay",
                        packageName = "com.shopeepay.id",
                        createdAt = createdAt,
                        updatedAt = createdAt
                    )
                )
            ),
            onGrantAccess = {},
            onSelectPackage = {},
            onAppEnabledChange = { _, _ -> }
        )
    }
}
