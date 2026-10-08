package com.elsco.mindwaymaths.core

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Light = lightColorScheme(primary = Color(0xFF176B54), onPrimary = Color.White,
    primaryContainer = Color(0xFFDDEDE3), onPrimaryContainer = Color(0xFF114A39),
    secondary = Color(0xFF826328), secondaryContainer = Color(0xFFF8ECCF),
    background = Color(0xFFF7F8F4), surface = Color(0xFFF7F8F4), surfaceContainer = Color(0xFFEFF2EB),
    surfaceContainerLow = Color(0xFFFFFFFF), onSurface = Color(0xFF192C25), onSurfaceVariant = Color(0xFF65736A), outlineVariant = Color(0xFFDDE3DB))
private val Dark = darkColorScheme(primary = Color(0xFF8BD3B4), onPrimary = Color(0xFF073B2C),
    primaryContainer = Color(0xFF214D3E), background = Color(0xFF111B16), surface = Color(0xFF111B16),
    surfaceContainer = Color(0xFF223027), surfaceContainerLow = Color(0xFF1A271F), onSurface = Color(0xFFE3ECE2), onSurfaceVariant = Color(0xFFB0C0B3))
@Composable fun MindwayTheme(theme: String = "system", content: @Composable () -> Unit) {
    val dark = theme == "dark" || theme == "system" && isSystemInDarkTheme()
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = Typography(
        headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 39.sp),
        headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 27.sp, lineHeight = 34.sp),
        titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 29.sp),
        titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 23.sp),
        bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 25.sp), bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
        labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp)), content = content)
}
