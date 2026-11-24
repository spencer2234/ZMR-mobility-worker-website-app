package com.zmr.rent.ui.theme

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// High-contrast palette. Every text / background pair below meets WCAG AA (4.5:1) so the
// screens stay readable on low-cost phone displays and outdoors in the workshop.

val ZmrBlue = Color(0xFF0B4F8A)
private val Ink = Color(0xFF111827)
private val InkSoft = Color(0xFF374151)

// Home screen tiles: one distinct colour per action, each dark enough for white text.
val TileToday = Color(0xFF15803D)
val TileRenters = ZmrBlue
val TileAddRenter = Color(0xFF6D28D9)
val TileReport = Color(0xFFB45309)

internal val ZmrColorScheme = lightColorScheme(
    primary = ZmrBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E6F5),
    onPrimaryContainer = Color(0xFF062B4C),
    secondary = InkSoft,
    onSecondary = Color.White,
    background = Color.White,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = InkSoft,
    outline = Color(0xFF6B7280),
    outlineVariant = Color(0xFFD1D5DB),
    error = Color(0xFFB91C1C),
    onError = Color.White,
)
