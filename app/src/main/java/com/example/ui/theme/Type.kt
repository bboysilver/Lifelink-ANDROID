package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Typography =
  Typography(
    bodyLarge =
      TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp,
      ),
    titleLarge = TextStyle(
      fontFamily = FontFamily.SansSerif,
      fontWeight = FontWeight.Bold,
      fontSize = 26.sp,
      lineHeight = 32.sp,
      letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
      fontFamily = FontFamily.SansSerif,
      fontWeight = FontWeight.Bold,
      fontSize = 19.sp,
      lineHeight = 25.sp,
    ),
    labelLarge = TextStyle(
      fontFamily = FontFamily.SansSerif,
      fontWeight = FontWeight.Bold,
      fontSize = 16.sp,
      lineHeight = 22.sp,
    ),
  )