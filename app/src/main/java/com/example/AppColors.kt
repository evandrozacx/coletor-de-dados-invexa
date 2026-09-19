package com.example

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class CollectorColors(
    val isDark: Boolean,
    val darkBg: Color,             // Screen background
    val panelBg: Color,            // Surfaces, cards, dialogs
    val panelBgTranslucent: Color,
    val cyanAccent: Color,         // Primary accent
    val textLight: Color,          // Secondary / muted text
    val textCyan: Color,           // Cyan / highlight text
    val greenSuccess: Color,       // Success green
    val borderDark: Color,         // Borders & dividers
    val textDark: Color,           // High contrast text on cyan/green buttons
    val footerText: Color,         // Footer / tertiary text
    val textPrimary: Color,        // Main high-contrast text / titles
    val inputBg: Color             // Input fields background
)

val DarkCollectorColors = CollectorColors(
    isDark = true,
    darkBg = Color(0xFF0F172A),             // Slate 900
    panelBg = Color(0xFF1E293B),            // Slate 800
    panelBgTranslucent = Color(0x801E293B),
    cyanAccent = Color(0xFF06B6D4),         // Cyan 500
    textLight = Color(0xFF94A3B8),          // Slate 400
    textCyan = Color(0xFF22D3EE),           // Cyan 400
    greenSuccess = Color(0xFF10B981),       // Emerald 500
    borderDark = Color(0xFF334155),         // Slate 700
    textDark = Color(0xFF0F172A),           // Dark text on bright button
    footerText = Color(0xFF475569),         // Slate 600
    textPrimary = Color(0xFFF8FAFC),        // High contrast light text
    inputBg = Color(0xFF1E293B)
)

val LightCollectorColors = CollectorColors(
    isDark = false,
    darkBg = Color(0xFFF1F5F9),             // Slate 100 - clean crisp light background
    panelBg = Color(0xFFFFFFFF),            // Pure white card surfaces
    panelBgTranslucent = Color(0xF0FFFFFF),
    cyanAccent = Color(0xFF0284C7),         // Sky 600 - bold readable accent on light
    textLight = Color(0xFF64748B),          // Slate 500 - crisp readable secondary
    textCyan = Color(0xFF0369A1),           // Sky 700
    greenSuccess = Color(0xFF059669),       // Emerald 600
    borderDark = Color(0xFFCBD5E1),         // Slate 300 - crisp elegant borders
    textDark = Color(0xFFFFFFFF),           // White text on Sky 600 / Emerald 600 buttons
    footerText = Color(0xFF64748B),         // Slate 500
    textPrimary = Color(0xFF0F172A),        // Slate 900 - high contrast dark text
    inputBg = Color(0xFFF8FAFC)             // Slate 50
)

val LocalCollectorColors = staticCompositionLocalOf { DarkCollectorColors }

object AppColors {
    val isDark: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalCollectorColors.current.isDark

    val DarkBg: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalCollectorColors.current.darkBg

    val PanelBg: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalCollectorColors.current.panelBg

    val PanelBgTranslucent: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalCollectorColors.current.panelBgTranslucent

    val CyanAccent: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalCollectorColors.current.cyanAccent

    val TextLight: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalCollectorColors.current.textLight

    val TextCyan: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalCollectorColors.current.textCyan

    val GreenSuccess: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalCollectorColors.current.greenSuccess

    val BorderDark: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalCollectorColors.current.borderDark

    val TextDark: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalCollectorColors.current.textDark

    val FooterText: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalCollectorColors.current.footerText

    val TextPrimary: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalCollectorColors.current.textPrimary

    val InputBg: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalCollectorColors.current.inputBg
}
