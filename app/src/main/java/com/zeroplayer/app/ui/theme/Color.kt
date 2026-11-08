package com.zeroplayer.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * ZERO PLAYER palette: pure black canvas, charcoal surfaces, silver accents.
 * Every tone is slightly cool so chrome highlights read as metal rather than grey.
 */
object Zero {
    val Black = Color(0xFF000000)
    val Surface0 = Color(0xFF07070A)
    val Surface1 = Color(0xFF0F0F13)
    val Surface2 = Color(0xFF16161B)
    val Surface3 = Color(0xFF1E1E24)
    val Hairline = Color(0xFF2A2A31)

    val Silver = Color(0xFFD9DCE2)
    val SilverDim = Color(0xFF9BA0A9)
    val Chrome = Color(0xFFF7F8FA)

    val TextPrimary = Color(0xFFF3F4F6)
    val TextSecondary = Color(0xFF9296A0)
    val TextTertiary = Color(0xFF5C606A)

    val Accent = Color(0xFFFFFFFF)
    val AccentMuted = Color(0xFFB7BCC6)

    val Danger = Color(0xFFE0575B)

    val chromeGradient = Brush.linearGradient(
        listOf(Color(0xFFFFFFFF), Color(0xFFD3D7DE), Color(0xFF9AA0AB)),
    )

    val silverGradient = Brush.linearGradient(
        listOf(Color(0xFFE9EBEF), Color(0xFF8E939D)),
    )

    val surfaceGradient = Brush.verticalGradient(
        listOf(Color(0xFF15151A), Color(0xFF0A0A0D)),
    )

    /** Ambient light behind the hero artwork; intentionally subtle. */
    val ambientGlow = Brush.radialGradient(
        listOf(Color(0x22FFFFFF), Color(0x00FFFFFF)),
    )
}
