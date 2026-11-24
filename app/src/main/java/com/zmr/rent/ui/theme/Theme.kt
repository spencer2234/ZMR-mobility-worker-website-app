package com.zmr.rent.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * One fixed light theme. Dark mode and wallpaper-based dynamic colours are deliberately not
 * used, so every phone shows the same colours and the same contrast.
 */
@Composable
fun ZmrTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ZmrColorScheme,
        typography = ZmrTypography,
        content = content,
    )
}
