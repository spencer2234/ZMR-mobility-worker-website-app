package com.zmr.rent.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Large type scale: body text is 18 sp and nothing the worker has to read is below 16 sp.
// Line heights are generous so Devanagari (Hindi) text is not clipped. The system font is
// used, which already covers Indian scripts and adds nothing to the app size.

private fun style(size: Int, lineHeight: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
)

internal val ZmrTypography = Typography(
    headlineSmall = style(26, 34, FontWeight.Bold),
    titleLarge = style(22, 30, FontWeight.SemiBold),
    titleMedium = style(20, 28, FontWeight.SemiBold),
    titleSmall = style(18, 26, FontWeight.SemiBold),
    bodyLarge = style(18, 26),
    bodyMedium = style(18, 26),
    bodySmall = style(16, 24),
    labelLarge = style(18, 26, FontWeight.SemiBold),
    labelMedium = style(16, 24, FontWeight.Medium),
    labelSmall = style(16, 24, FontWeight.Medium),
)
