package com.fgteam.paymentobserver.ui.payments

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fgteam.paymentobserver.data.IncomingPayment
import com.fgteam.paymentobserver.data.ObservedApp
import com.fgteam.paymentobserver.service.ObserverForegroundService
import com.fgteam.paymentobserver.ui.theme.ObserverBlue
import com.fgteam.paymentobserver.ui.theme.ObserverBlueSoft
import com.fgteam.paymentobserver.ui.theme.ObserverBorder
import com.fgteam.paymentobserver.ui.theme.ObserverGreen
import com.fgteam.paymentobserver.ui.theme.ObserverGreenSoft
import com.fgteam.paymentobserver.ui.theme.ObserverOrange
import com.fgteam.paymentobserver.ui.theme.ObserverOrangeSoft
import com.fgteam.paymentobserver.ui.theme.ObserverPurple
import com.fgteam.paymentobserver.ui.theme.ObserverPurpleDark
import com.fgteam.paymentobserver.ui.theme.ObserverPurpleSoft
import com.fgteam.paymentobserver.ui.theme.ObserverRed
import com.fgteam.paymentobserver.ui.theme.ObserverRedSoft
import com.fgteam.paymentobserver.ui.theme.PaymentObserverTheme
import com.fgteam.paymentobserver.util.NotificationAccess
import com.fgteam.paymentobserver.util.OemPowerGuide
import com.fgteam.paymentobserver.util.ObserverSetup
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToLong

private val PagePadding = 16.dp
private val SectionSpacing = 6.dp
private val ContentSpacing = 8.dp
private val CardPadding = 14.dp
private val ScreenBottomPadding = 16.dp
private val CardShape = RoundedCornerShape(20.dp)
private val CompactShape = RoundedCornerShape(14.dp)

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
            if (event == Lifecycle.Event.ON_RESUME) refreshObserverStatus()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    when {
        uiState.isRestoringSession -> Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = ObserverPurple)
        }

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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = CardShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ObserverPurpleSoft,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            tint = ObserverPurple
                        )
                    }
                }
                Text(
                    text = "Payment Observer",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Masuk menggunakan akun FGTeam dengan role admin.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    enabled = !isLoading,
                    shape = CompactShape,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    enabled = !isLoading,
                    visualTransformation = PasswordVisualTransformation(),
                    shape = CompactShape,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                Button(
                    onClick = { onLogin(email, password) },
                    enabled = !isLoading,
                    shape = CompactShape,
                    contentPadding = PaddingValues(vertical = 12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Masuk", fontWeight = FontWeight.SemiBold)
                    }
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
    val snackbarHostState = remember { SnackbarHostState() }
    val observerReady = uiState.isObserverReady()
    var isConfiguringApps by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(uiState.syncMessage) {
        uiState.syncMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    BackHandler(enabled = isConfiguringApps) {
        isConfiguringApps = false
    }

    if (isConfiguringApps) {
        AppConfigurationContent(
            apps = uiState.observedApps,
            onAppEnabledChange = onAppEnabledChange,
            onBack = { isConfiguringApps = false },
            modifier = modifier
        )
        return
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            DashboardHeader(
                adminName = uiState.session?.fullName ?: "Admin",
                onLogout = onLogout
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("payments_list"),
            contentPadding = PaddingValues(
                start = PagePadding,
                end = PagePadding,
                bottom = ScreenBottomPadding
            ),
            verticalArrangement = Arrangement.spacedBy(SectionSpacing)
        ) {
            item(key = "balance", contentType = "dashboard") {
                BalanceHeroCard(
                    uiState = uiState,
                    observerReady = observerReady,
                    onSync = onSync
                )
            }

            if (!observerReady) {
                item(key = "observer_setup", contentType = "observer_setup") {
                    ListenerStatusCard(
                        uiState = uiState,
                        onGrantAccess = onGrantAccess,
                        onEnableNotifications = onEnableNotifications,
                        onDisableBatteryOptimization = onDisableBatteryOptimization,
                        onOpenOemSettings = onOpenOemSettings,
                        onConfirmOemSetup = onConfirmOemSetup
                    )
                }
            }

            item(key = "observed_apps", contentType = "observed_apps") {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    ActiveAppsHeader(
                        onManageApps = { isConfiguringApps = true }
                    )
                    ActiveAppChips(apps = uiState.observedApps)
                }
            }

            item(key = "payments_header", contentType = "section_header") {
                SectionHeader(
                    title = "Transaksi terbaru",
                    supportingText = "${uiState.payments.size} data"
                )
            }

            item(key = "payment_filters", contentType = "filters") {
                PaymentFilters(
                    apps = uiState.observedApps,
                    selectedPackageName = uiState.selectedPackageName,
                    onSelectPackage = onSelectPackage
                )
            }

            if (uiState.payments.isEmpty()) {
                item(key = "empty_payments", contentType = "empty") {
                    EmptyState(uiState.hasNotificationAccess)
                }
            } else {
                items(
                    items = uiState.payments,
                    key = IncomingPayment::id,
                    contentType = { "payment" }
                ) { payment ->
                    PaymentCard(payment)
                }
            }
        }
    }
}

@Composable
private fun DashboardHeader(adminName: String, onLogout: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = PagePadding, top = 4.dp, end = 8.dp, bottom = 8.dp)
                .testTag("dashboard_header"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Halo, $adminName",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Dashboard monitoring pembayaran",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onLogout,
                modifier = Modifier.size(48.dp).testTag("logout_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = "Logout",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ObserverReadyPanel() {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("observer_active_chip")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = CardPadding, vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SmoothPulsingDot(ObserverGreen)
            Text(
                text = "Siap menerima notifikasi",
                color = ObserverPurple,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun BalanceHeroCard(
    uiState: PaymentsUiState,
    observerReady: Boolean,
    onSync: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("today_total"),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF7568F8), Color(0xFF564BE7))
                    )
                )
                .padding(horizontal = CardPadding, vertical = 12.dp)
                .testTag("balance_summary"),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = "Total pembayaran hari ini",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.82f)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnimatedBalanceAmount(
                    targetAmount = uiState.totalToday,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = onSync,
                    enabled = !uiState.isSyncing,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.2f),
                        contentColor = Color.White,
                        disabledContainerColor = Color.White.copy(alpha = 0.12f),
                        disabledContentColor = Color.White.copy(alpha = 0.65f)
                    ),
                    contentPadding = PaddingValues(horizontal = 13.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("sync_button")
                ) {
                    if (uiState.isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Proses")
                    } else {
                        Text("Sinkronkan", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(5.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            Text(
                text = "${uiState.payments.size} transaksi  •  ${uiState.pendingSyncCount} belum sync",
                color = Color.White.copy(alpha = 0.76f),
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (observerReady) ObserverReadyPanel()
    }
}

@Composable
private fun AnimatedBalanceAmount(
    targetAmount: Long,
    modifier: Modifier = Modifier
) {
    val progress = remember { Animatable(0f) }
    val formatter = remember { NumberFormat.getInstance(Locale("in", "ID")) }
    var displayedAmount by remember { mutableLongStateOf(0L) }

    LaunchedEffect(targetAmount) {
        val startAmount = displayedAmount
        if (startAmount == targetAmount) return@LaunchedEffect

        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 800,
                easing = FastOutSlowInEasing
            )
        ) {
            displayedAmount = interpolateAmount(startAmount, targetAmount, value)
        }
        displayedAmount = targetAmount
    }

    Text(
        text = "Rp${formatter.format(displayedAmount)}",
        style = MaterialTheme.typography.headlineSmall,
        color = Color.White,
        fontWeight = FontWeight.Bold,
        modifier = modifier.testTag("balance_amount")
    )
}

private fun interpolateAmount(start: Long, end: Long, fraction: Float): Long = when {
    fraction <= 0f -> start
    fraction >= 1f -> end
    else -> (start.toDouble() + (end.toDouble() - start.toDouble()) * fraction).roundToLong()
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
    Card(
        modifier = Modifier.fillMaxWidth().testTag("observer_setup_card"),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier.padding(CardPadding),
            verticalArrangement = Arrangement.spacedBy(ContentSpacing)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(shape = CircleShape, color = ObserverOrangeSoft) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = ObserverOrange,
                        modifier = Modifier.padding(7.dp).size(19.dp)
                    )
                }
                Column {
                    Text(
                        text = "Lengkapi konfigurasi observer",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Pastikan aplikasi tetap menerima pembayaran.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = ObserverBorder)

            SetupStatusRow(
                ready = uiState.hasNotificationAccess,
                readyText = "Akses notifikasi aktif",
                missingText = "Akses notifikasi belum aktif",
                actionLabel = "Aktifkan",
                onAction = onGrantAccess
            )
            SetupStatusRow(
                ready = uiState.isForegroundServiceRunning,
                readyText = "Service always-on aktif",
                missingText = "Service always-on belum aktif"
            )
            SetupStatusRow(
                ready = uiState.isListenerConnected,
                readyText = "Listener terhubung",
                missingText = "Listener sedang menghubungkan ulang"
            )
            SetupStatusRow(
                ready = uiState.canPostNotifications,
                readyText = "Notifikasi status diizinkan",
                missingText = "Notifikasi status belum diizinkan",
                actionLabel = "Izinkan",
                onAction = onEnableNotifications
            )
            SetupStatusRow(
                ready = uiState.isIgnoringBatteryOptimizations,
                readyText = "Optimasi baterai dinonaktifkan",
                missingText = "Optimasi baterai masih aktif",
                actionLabel = "Atur",
                onAction = onDisableBatteryOptimization
            )
            uiState.oemPowerGuide?.let { guide ->
                SetupStatusRow(
                    ready = uiState.isOemSetupAcknowledged,
                    readyText = "Pengaturan ${guide.name} sudah dikonfirmasi",
                    missingText = "Periksa pengaturan ${guide.name}",
                    actionLabel = "Buka",
                    onAction = { onOpenOemSettings(guide) }
                )
                if (!uiState.isOemSetupAcknowledged) {
                    Text(
                        text = guide.instructions,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 32.dp)
                    )
                    TextButton(
                        onClick = onConfirmOemSetup,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Saya sudah mengaktifkannya")
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupStatusRow(
    ready: Boolean,
    readyText: String,
    missingText: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (ready) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = if (ready) ObserverGreen else ObserverOrange,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = if (ready) readyText else missingText,
            style = MaterialTheme.typography.bodyMedium,
            color = if (ready) MaterialTheme.colorScheme.onSurface else ObserverOrange,
            fontWeight = if (ready) FontWeight.Normal else FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        if (!ready && actionLabel != null && onAction != null) {
            TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(actionLabel, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, supportingText: String) {
    Row(
        modifier = Modifier.fillMaxWidth().offset(y = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = supportingText,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ActiveAppsHeader(onManageApps: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Aplikasi aktif",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        TextButton(
            onClick = onManageApps,
            modifier = Modifier.height(36.dp).testTag("manage_apps_button"),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
        ) {
            Text("Atur", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveAppChips(apps: List<ObservedApp>) {
    val activeApps = remember(apps) {
        apps.filter(ObservedApp::isEnabled).sortedBy(ObservedApp::sortOrder)
    }

    if (activeApps.isEmpty()) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, ObserverBorder),
            modifier = Modifier.testTag("no_active_apps_chip")
        ) {
            Text(
                text = "Belum ada aplikasi aktif",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
            )
        }
        return
    }

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        activeApps.forEach { app ->
            Surface(
                shape = RoundedCornerShape(50),
                color = appAccentColor(app).copy(alpha = 0.12f),
                modifier = Modifier.testTag("active_app_chip_${app.packageName}")
            ) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.labelLarge,
                    color = appAccentColor(app),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun AppConfigurationContent(
    apps: List<ObservedApp>,
    onAppEnabledChange: (String, Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sortedApps = remember(apps) { apps.sortedBy(ObservedApp::sortOrder) }

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("app_config_screen"),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppConfigurationHeader(onBack = onBack) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("app_config_list"),
            contentPadding = PaddingValues(
                start = PagePadding,
                end = PagePadding,
                bottom = ScreenBottomPadding
            ),
            verticalArrangement = Arrangement.spacedBy(ContentSpacing)
        ) {
            items(
                items = sortedApps,
                key = ObservedApp::packageName,
                contentType = { "app_configuration" }
            ) { app ->
                AppConfigurationCard(
                    app = app,
                    onEnabledChange = { enabled ->
                        onAppEnabledChange(app.packageName, enabled)
                    }
                )
            }

            item(key = "auto_save_message", contentType = "footer") {
                Text(
                    text = "Perubahan tersimpan otomatis",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun AppConfigurationHeader(onBack: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 4.dp, top = 4.dp, end = PagePadding, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(48.dp).testTag("app_config_back")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Kembali"
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Aplikasi yang dipantau",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Pilih sumber notifikasi pembayaran",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AppConfigurationCard(
    app: ObservedApp,
    onEnabledChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("config_app_${app.packageName}"),
        shape = CompactShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = CardPadding, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (app.isEnabled) "Aktif" else "Nonaktif",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (app.isEnabled) {
                            ObserverGreen
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = app.isEnabled,
                onCheckedChange = onEnabledChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = appAccentColor(app),
                    uncheckedBorderColor = ObserverBorder
                ),
                modifier = Modifier.testTag("switch_${app.packageName}")
            )
        }
    }
}

@Composable
private fun PaymentFilters(
    apps: List<ObservedApp>,
    selectedPackageName: String?,
    onSelectPackage: (String?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ObserverFilterChip(
            selected = selectedPackageName == null,
            label = "Semua",
            onClick = { onSelectPackage(null) }
        )
        apps.forEach { app ->
            ObserverFilterChip(
                selected = selectedPackageName == app.packageName,
                label = app.appName,
                onClick = { onSelectPackage(app.packageName) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ObserverFilterChip(selected: Boolean, label: String, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) },
        shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = ObserverPurple,
            selectedLabelColor = Color.White
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = ObserverBorder,
            selectedBorderColor = ObserverPurple
        )
    )
}

@Composable
private fun EmptyState(hasAccess: Boolean) {
    EmptySurface(
        if (hasAccess) {
            "Belum ada transaksi untuk filter ini. Pastikan aplikasi sumber sudah diaktifkan."
        } else {
            "Belum ada data. Aktifkan akses notifikasi untuk mulai mendengarkan."
        },
        modifier = Modifier.testTag("empty_state")
    )
}

@Composable
private fun EmptySurface(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, ObserverBorder)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)
        )
    }
}

@Composable
private fun PaymentCard(payment: IncomingPayment) {
    val status = payment.statusStyle()
    val accent = appAccentColor(payment.appName, payment.packageName)

    Card(
        modifier = Modifier.fillMaxWidth().testTag("payment_${payment.id}"),
        shape = CompactShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier.padding(CardPadding),
            verticalArrangement = Arrangement.spacedBy(ContentSpacing)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SourceBadge(appName = payment.appName, color = accent)
                Spacer(Modifier.width(9.dp))
                Text(
                    text = payment.appName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                StatusChip(status)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = payment.sender.ifBlank { "Pengirim tidak diketahui" },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatPaymentDate(payment.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = formatRupiah(payment.amount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = ObserverPurpleDark
                )
            }
            payment.remoteOrderId?.takeIf(String::isNotBlank)?.let { orderId ->
                Surface(shape = RoundedCornerShape(50), color = ObserverBlueSoft) {
                    Text(
                        text = orderId,
                        style = MaterialTheme.typography.labelMedium,
                        color = ObserverBlue,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
            if (!payment.isSyncToDb && !payment.lastSyncError.isNullOrBlank()) {
                Text(
                    text = payment.lastSyncError,
                    style = MaterialTheme.typography.bodySmall,
                    color = ObserverRed,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun StatusChip(style: PaymentStatusStyle) {
    Surface(
        shape = RoundedCornerShape(50),
        color = style.background,
        modifier = Modifier.testTag("status_${style.testValue}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (style.animated) PulsingDot(style.foreground)
            else {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(style.foreground, CircleShape)
                )
            }
            Text(
                text = style.label,
                style = MaterialTheme.typography.labelMedium,
                color = style.foreground,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun SmoothPulsingDot(color: Color) {
    val transition = rememberInfiniteTransition(label = "ready pulse")
    val scale by transition.animateFloat(
        initialValue = 0.75f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 760),
            repeatMode = RepeatMode.Restart
        ),
        label = "ready pulse scale"
    )
    val haloAlpha by transition.animateFloat(
        initialValue = 0.60f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 760),
            repeatMode = RepeatMode.Restart
        ),
        label = "ready pulse alpha"
    )

    Box(modifier = Modifier.size(13.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(13.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = haloAlpha
                }
                .background(color, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(color, CircleShape)
        )
    }
}

@Composable
private fun PulsingDot(color: Color) {
    val transition = rememberInfiniteTransition(label = "status pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850),
            repeatMode = RepeatMode.Reverse
        ),
        label = "status pulse alpha"
    )
    Box(
        modifier = Modifier
            .size(8.dp)
            .alpha(alpha)
            .background(color, CircleShape)
    )
}

@Composable
private fun SourceBadge(appName: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.13f),
        modifier = Modifier.size(36.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = appName.trim().firstOrNull()?.uppercase() ?: "?",
                color = color,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private data class PaymentStatusStyle(
    val label: String,
    val testValue: String,
    val background: Color,
    val foreground: Color,
    val animated: Boolean
)

private fun IncomingPayment.statusStyle(): PaymentStatusStyle = when {
    !isSyncToDb -> PaymentStatusStyle(
        label = "Belum Sync",
        testValue = "pending",
        background = ObserverOrangeSoft,
        foreground = ObserverOrange,
        animated = true
    )

    remoteMatchStatus == "matched" -> PaymentStatusStyle(
        label = "Matched",
        testValue = "matched",
        background = ObserverGreenSoft,
        foreground = ObserverGreen,
        animated = false
    )

    remoteMatchStatus == "reserved" -> PaymentStatusStyle(
        label = "Reserved",
        testValue = "reserved",
        background = ObserverPurpleSoft,
        foreground = ObserverPurple,
        animated = true
    )

    else -> PaymentStatusStyle(
        label = "Synced • Unmatched",
        testValue = "unmatched",
        background = ObserverBlueSoft,
        foreground = ObserverBlue,
        animated = false
    )
}

private fun PaymentsUiState.isObserverReady(): Boolean =
    hasNotificationAccess &&
        isForegroundServiceRunning &&
        isListenerConnected &&
        canPostNotifications &&
        isIgnoringBatteryOptimizations &&
        (oemPowerGuide == null || isOemSetupAcknowledged)

private fun appAccentColor(app: ObservedApp): Color =
    appAccentColor(app.appName, app.packageName)

private fun appAccentColor(appName: String, packageName: String): Color = when {
    packageName.contains("bni", ignoreCase = true) -> ObserverOrange
    appName.contains("partner", ignoreCase = true) -> ObserverBlue
    packageName.contains("shopee", ignoreCase = true) -> ObserverPurple
    else -> ObserverGreen
}

private fun formatPaymentDate(dateTime: LocalDateTime): String {
    val dayLabel = when (dateTime.toLocalDate()) {
        LocalDate.now() -> "Hari ini"
        LocalDate.now().minusDays(1) -> "Kemarin"
        else -> dateTime.format(
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale("in", "ID"))
        )
    }
    return "$dayLabel • ${dateTime.format(DateTimeFormatter.ofPattern("HH:mm"))}"
}

private fun formatRupiah(amount: Long): String =
    "Rp" + NumberFormat.getInstance(Locale("in", "ID")).format(amount)

@Preview(showBackground = true)
@Composable
private fun PaymentsContentPreview() {
    val createdAt = LocalDateTime.now().withHour(10).withMinute(15)
    val apps = listOf(
        ObservedApp("com.shopeepay.id", "ShopeePay", true, 1, createdAt, createdAt),
        ObservedApp("id.co.bni.merchant", "BNI Merchant", false, 2, createdAt, createdAt)
    )
    PaymentObserverTheme {
        PaymentsContent(
            uiState = PaymentsUiState(
                hasNotificationAccess = true,
                isForegroundServiceRunning = true,
                isListenerConnected = true,
                canPostNotifications = true,
                isIgnoringBatteryOptimizations = true,
                totalToday = 20_865_000,
                pendingSyncCount = 3,
                session = AdminIdentity("Admin FGTeam", "admin"),
                observedApps = apps,
                payments = listOf(
                    IncomingPayment(
                        id = "preview",
                        sourceNotificationKey = "preview-key",
                        amount = 150_000,
                        rawAmount = "Rp150.000",
                        sender = "BLU BCA",
                        title = "BNI Merchant",
                        body = "Transaksi Sebesar Rp 150.000 dari BLU BCA telah berhasil",
                        appName = "BNI Merchant",
                        packageName = "id.co.bni.merchant",
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
