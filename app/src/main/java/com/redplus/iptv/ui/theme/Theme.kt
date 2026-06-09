package com.redplus.iptv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val PremiumRed = Color(0xFFE50914)
val PremiumRedSoft = Color(0xFFFF4655)
val PremiumBg = Color(0xFF070912)
val PremiumPanel = Color(0xB31A1D2B)
val PremiumPanelSoft = Color(0x661F2435)
val PremiumText = Color(0xFFF5F7FA)
val PremiumMuted = Color(0xFFB8BECC)
val SuccessGreen = Color(0xFF21D07A)
val WarningAmber = Color(0xFFFFB020)

private val DarkScheme = darkColorScheme(
    primary = PremiumRed,
    secondary = PremiumRedSoft,
    background = PremiumBg,
    surface = Color(0xFF10131E),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = PremiumText,
    onSurface = PremiumText,
    error = Color(0xFFFF6B6B)
)

@Composable
fun RedPlusTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = DarkScheme, typography = MaterialTheme.typography, content = content)
