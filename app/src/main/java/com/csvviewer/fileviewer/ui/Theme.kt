package com.csvviewer.fileviewer.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Tokens match the Figma "Color" collection
private val LightColors = lightColorScheme(
    primary = Color(0xFF15803D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCFCE7),
    onPrimaryContainer = Color(0xFF14532D),
    secondary = Color(0xFF0F766E),
    onSecondary = Color.White,
    background = Color(0xFFF7F9F8),
    onBackground = Color(0xFF111814),
    surface = Color.White,
    onSurface = Color(0xFF111814),
    surfaceVariant = Color(0xFFEEF2F0),
    onSurfaceVariant = Color(0xFF637169),
    outline = Color(0xFFD9E0DC),
    outlineVariant = Color(0xFFE6EBE8),
    error = Color(0xFFDC2626),
    inverseSurface = Color(0xFF1F2723),
    inverseOnSurface = Color(0xFFE8EEEA),
    inversePrimary = Color(0xFF4ADE80),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4ADE80),
    onPrimary = Color(0xFF052E16),
    primaryContainer = Color(0xFF14532D),
    onPrimaryContainer = Color(0xFFDCFCE7),
    secondary = Color(0xFF5EEAD4),
    onSecondary = Color(0xFF042F2E),
    background = Color(0xFF0E1311),
    onBackground = Color(0xFFE8EEEA),
    surface = Color(0xFF161C19),
    onSurface = Color(0xFFE8EEEA),
    surfaceVariant = Color(0xFF1F2723),
    onSurfaceVariant = Color(0xFF859189),
    outline = Color(0xFF2C3631),
    outlineVariant = Color(0xFF242D29),
    error = Color(0xFFF87171),
    inverseSurface = Color(0xFFE8EEEA),
    inverseOnSurface = Color(0xFF161C19),
    inversePrimary = Color(0xFF166534),
)

@Immutable
data class FileTints(val csv: Color, val excel: Color, val pdf: Color, val json: Color, val text: Color, val image: Color, val zip: Color, val other: Color)

private val LightTints = FileTints(
    csv = Color(0xFF15803D), excel = Color(0xFF047857), pdf = Color(0xFFDC2626), json = Color(0xFF7C3AED),
    text = Color(0xFF2563EB), image = Color(0xFFDB2777), zip = Color(0xFFB45309), other = Color(0xFF64748B),
)
private val DarkTints = FileTints(
    csv = Color(0xFF4ADE80), excel = Color(0xFF34D399), pdf = Color(0xFFF87171), json = Color(0xFFA78BFA),
    text = Color(0xFF60A5FA), image = Color(0xFFF472B6), zip = Color(0xFFFBBF24), other = Color(0xFF94A3B8),
)

val LocalTints = staticCompositionLocalOf { LightTints }
val LocalDark = staticCompositionLocalOf { false }

val Gold1 = Color(0xFFFBBF24)
val Gold2 = Color(0xFFD97706)

private val AppTypography = Typography(
    headlineSmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 30.sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, lineHeight = 14.sp),
)

@Composable
fun CsvTheme(dark: Boolean, content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(
        LocalTints provides if (dark) DarkTints else LightTints,
        LocalDark provides dark,
    ) {
        MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = AppTypography, content = content)
    }
}
