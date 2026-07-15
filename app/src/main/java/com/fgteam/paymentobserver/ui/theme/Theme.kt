package com.fgteam.paymentobserver.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = ObserverPurple,
    onPrimary = ObserverSurface,
    primaryContainer = ObserverPurpleSoft,
    onPrimaryContainer = ObserverPurpleDark,
    secondary = ObserverBlue,
    onSecondary = ObserverSurface,
    secondaryContainer = ObserverBlueSoft,
    onSecondaryContainer = ObserverBlue,
    tertiary = ObserverGreen,
    onTertiary = ObserverSurface,
    tertiaryContainer = ObserverGreenSoft,
    onTertiaryContainer = ObserverGreen,
    error = ObserverRed,
    errorContainer = ObserverRedSoft,
    background = ObserverBackground,
    onBackground = ObserverText,
    surface = ObserverSurface,
    onSurface = ObserverText,
    surfaceVariant = ObserverBackground,
    onSurfaceVariant = ObserverTextMuted,
    outline = ObserverBorder
)

@Composable
fun PaymentObserverTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
