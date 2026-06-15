package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

private val BentoLightColorScheme =
  lightColorScheme(
    primary = Color(0xFF4F46E5), // Indigo 600
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEEF2FF), // Indigo 50
    onPrimaryContainer = Color(0xFF3730A3), // Indigo 800
    secondary = Color(0xFF10B981), // Emerald 500
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFECFDF5), // Emerald 50
    onSecondaryContainer = Color(0xFF065F46), // Emerald 800
    tertiary = Color(0xFFF59E0B), // Amber 500
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFEF3C7), // Amber 100
    onTertiaryContainer = Color(0xFF92400E), // Amber 800
    error = Color(0xFFEF4444), // Red 500
    onError = Color.White,
    errorContainer = Color(0xFFFEF2F2), // Red 50
    onErrorContainer = Color(0xFF991B1B), // Red 800
    background = Color(0xFFFAF9F6), // Luxury Warm Alabaster White
    onBackground = Color(0xFF111827), // Slate 900
    surface = Color.White,
    onSurface = Color(0xFF111827), // Slate 900
    surfaceVariant = Color(0xFFF3F4F6), // Cool gray 100
    onSurfaceVariant = Color(0xFF4B5563), // Slate 600
    outline = Color(0xFFE5E7EB), // Gray 200
    outlineVariant = Color(0xFFF3F4F6) // Gray 100
  )

private val BentoDarkColorScheme =
  darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFADC6FF), // Blue pricing helper for dark
    onTertiary = Color(0xFF002E69),
    tertiaryContainer = Color(0xFF004494),
    onTertiaryContainer = Color(0xFFD3E3FD),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    background = Color(0xFF141218),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF141218),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F)
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = false, // Default to false to showcase our new beautiful aesthetic light theme!
  dynamicColor: Boolean = false, // Force custom Bento colors
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> BentoDarkColorScheme
      else -> BentoLightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
