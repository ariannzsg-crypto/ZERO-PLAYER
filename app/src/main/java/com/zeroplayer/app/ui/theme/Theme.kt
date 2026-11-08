package com.zeroplayer.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val ZeroColorScheme = darkColorScheme(
    primary = Zero.Accent,
    onPrimary = Zero.Black,
    primaryContainer = Zero.Surface3,
    onPrimaryContainer = Zero.TextPrimary,
    secondary = Zero.Silver,
    onSecondary = Zero.Black,
    secondaryContainer = Zero.Surface2,
    onSecondaryContainer = Zero.TextPrimary,
    tertiary = Zero.SilverDim,
    onTertiary = Zero.Black,
    background = Zero.Black,
    onBackground = Zero.TextPrimary,
    surface = Zero.Surface0,
    onSurface = Zero.TextPrimary,
    surfaceVariant = Zero.Surface2,
    onSurfaceVariant = Zero.TextSecondary,
    surfaceContainer = Zero.Surface1,
    surfaceContainerHigh = Zero.Surface2,
    surfaceContainerHighest = Zero.Surface3,
    surfaceContainerLow = Zero.Surface0,
    surfaceContainerLowest = Zero.Black,
    outline = Zero.Hairline,
    outlineVariant = Zero.Surface3,
    error = Zero.Danger,
    onError = Color.White,
    scrim = Color.Black,
    inverseSurface = Zero.Silver,
    inverseOnSurface = Zero.Black,
)

private val ZeroShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
)

@Composable
fun ZeroTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = ZeroColorScheme,
        typography = ZeroTypography,
        shapes = ZeroShapes,
    ) {
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black,
            content = content,
        )
    }
}
