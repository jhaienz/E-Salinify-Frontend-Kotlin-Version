package com.esalinify.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val QuicksandFontFamily = FontFamily.Default

val Typography = Typography(
    // 30sp - OnBoarding Title
    headlineLarge = TextStyle(
        fontFamily = QuicksandFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    // 25sp - ImageTextPrimary (OnBoarding tagline)
    headlineMedium = TextStyle(
        fontFamily = QuicksandFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 25.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    // 20sp - Button text
    titleLarge = TextStyle(
        fontFamily = QuicksandFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    // 18sp - Welcome text, Card titles
    titleMedium = TextStyle(
        fontFamily = QuicksandFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    // 18sp - Body text (medium weight)
    bodyLarge = TextStyle(
        fontFamily = QuicksandFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    // 16sp - Regular body text
    bodyMedium = TextStyle(
        fontFamily = QuicksandFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    ),
    // 14sp - Small text
    bodySmall = TextStyle(
        fontFamily = QuicksandFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    // 16sp - Labels
    labelLarge = TextStyle(
        fontFamily = QuicksandFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    )
)
