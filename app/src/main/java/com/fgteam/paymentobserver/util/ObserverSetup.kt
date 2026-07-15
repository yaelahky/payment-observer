package com.fgteam.paymentobserver.util

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.util.Locale

data class OemPowerGuide(
    val name: String,
    val instructions: String,
    internal val settingsComponents: List<ComponentName>
)

data class ObserverSetupStatus(
    val hasNotificationAccess: Boolean = false,
    val canPostNotifications: Boolean = false,
    val isIgnoringBatteryOptimizations: Boolean = false,
    val oemPowerGuide: OemPowerGuide? = null,
    val isOemSetupAcknowledged: Boolean = false
)

object ObserverSetup {
    private const val PREFERENCES = "observer_setup"
    private const val OEM_ACKNOWLEDGED = "oem_setup_acknowledged"

    fun status(context: Context): ObserverSetupStatus {
        val guide = guideForManufacturer(Build.MANUFACTURER)
        return ObserverSetupStatus(
            hasNotificationAccess = NotificationAccess.isEnabled(context),
            canPostNotifications = hasNotificationPermission(context) &&
                NotificationManagerCompat.from(context).areNotificationsEnabled(),
            isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations(context),
            oemPowerGuide = guide,
            isOemSetupAcknowledged = guide == null || context.getSharedPreferences(
                PREFERENCES,
                Context.MODE_PRIVATE
            ).getBoolean(OEM_ACKNOWLEDGED, false)
        )
    }

    fun hasNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("BatteryLife")
    fun openBatteryExemption(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            isIgnoringBatteryOptimizations(context)
        ) return
        val directRequest = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}")
        )
        val settingsFallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        startFirstAvailable(context, listOf(directRequest, settingsFallback, appDetailsIntent(context)))
    }

    fun openAppNotificationSettings(context: Context) {
        val notificationSettings = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else {
            appDetailsIntent(context)
        }
        startFirstAvailable(context, listOf(notificationSettings, appDetailsIntent(context)))
    }

    fun openOemPowerSettings(context: Context, guide: OemPowerGuide) {
        val candidates = guide.settingsComponents.map { component ->
            Intent().setComponent(component)
        } + appDetailsIntent(context)
        startFirstAvailable(context, candidates)
    }

    fun acknowledgeOemSetup(context: Context) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(OEM_ACKNOWLEDGED, true)
            .apply()
    }

    internal fun guideForManufacturer(manufacturer: String): OemPowerGuide? =
        when (manufacturer.trim().lowercase(Locale.ROOT)) {
            "xiaomi", "redmi", "poco" -> OemPowerGuide(
                name = "Xiaomi / Redmi / POCO",
                instructions = "Aktifkan Autostart dan set Battery saver ke No restrictions.",
                settingsComponents = listOf(
                    ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"
                    )
                )
            )
            "oppo", "realme", "oneplus" -> OemPowerGuide(
                name = "OPPO / realme / OnePlus",
                instructions = "Izinkan Auto launch dan Allow background activity.",
                settingsComponents = listOf(
                    ComponentName(
                        "com.oplus.safecenter",
                        "com.oplus.safecenter.startupapp.StartupAppListActivity"
                    ),
                    ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                    )
                )
            )
            "vivo", "iqoo" -> OemPowerGuide(
                name = "vivo / iQOO",
                instructions = "Aktifkan Autostart dan izinkan konsumsi daya tinggi di latar belakang.",
                settingsComponents = listOf(
                    ComponentName(
                        "com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                    )
                )
            )
            "huawei", "honor" -> OemPowerGuide(
                name = "Huawei / Honor",
                instructions = "Atur App launch secara manual dan izinkan berjalan di latar belakang.",
                settingsComponents = listOf(
                    ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                    )
                )
            )
            "samsung" -> OemPowerGuide(
                name = "Samsung",
                instructions = "Set Battery ke Unrestricted dan keluarkan aplikasi dari Sleeping apps.",
                settingsComponents = emptyList()
            )
            else -> null
        }

    private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        return (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .isIgnoringBatteryOptimizations(context.packageName)
    }

    private fun appDetailsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        )

    private fun startFirstAvailable(context: Context, candidates: List<Intent>) {
        for (candidate in candidates) {
            try {
                context.startActivity(candidate.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: Exception) {
                // OEM settings components are not stable; continue to the standard fallback.
            }
        }
    }
}
