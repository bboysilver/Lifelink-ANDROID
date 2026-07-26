package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme =
  darkColorScheme(
    primary = Color(0xFF6FD4C8),
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF0B4F49),
    onPrimaryContainer = Color(0xFFDDF1EE),
    secondary = Color(0xFFFFB4AD),
    onSecondary = Color(0xFF68000B),
    background = Color(0xFF0D1B28),
    surface = Color(0xFF142533),
    onSurface = Color(0xFFE7EEF4),
    error = Color(0xFFFFB4AB),
  )

private val LightColorScheme =
  lightColorScheme(
    primary = LifeTeal,
    onPrimary = Color.White,
    primaryContainer = LifeMist,
    onPrimaryContainer = LifeNavy,
    secondary = LifeCoral,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDAD6),
    onSecondaryContainer = Color(0xFF5E1411),
    background = LifeIvory,
    onBackground = LifeNavy,
    surface = Color.White,
    onSurface = LifeNavy,
    surfaceVariant = Color(0xFFE8ECEF),
    onSurfaceVariant = LifeSlate,
    outline = Color(0xFF8796A3),
    error = Color(0xFFB42318),
    errorContainer = Color(0xFFFFE4E0),
    onErrorContainer = Color(0xFF5E1411),
  )

private val LifeLinkShapes =
  Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) {
    DarkColorScheme
  } else {
    LightColorScheme
  }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    shapes = LifeLinkShapes,
    content = content,
  )
}