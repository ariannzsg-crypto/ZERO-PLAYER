package com.zeroplayer.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.zeroplayer.app.ui.theme.Zero

/**
 * Album artwork with a cinematic fallback: a chrome monogram on a charcoal
 * gradient, so tracks without artwork still look intentional.
 */
@Composable
fun CoverArt(
    artworkUri: String?,
    modifier: Modifier = Modifier,
    corner: Dp = 14.dp,
    label: String? = null,
    showBorder: Boolean = true,
) {
    val shape = RoundedCornerShape(corner)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(Zero.Surface3, Zero.Surface1),
                ),
            )
            .then(
                if (showBorder) Modifier.border(0.7.dp, Color.White.copy(alpha = 0.06f), shape) else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (!artworkUri.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(artworkUri)
                    .crossfade(220)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = (label?.firstOrNull()?.uppercaseChar() ?: 'Z').toString(),
                color = Zero.SilverDim.copy(alpha = 0.5f),
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                style = MaterialTheme.typography.displayMedium,
            )
        }
    }
}
