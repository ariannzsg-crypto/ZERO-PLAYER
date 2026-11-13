package com.zeroplayer.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeroplayer.app.ui.theme.Zero
import kotlinx.coroutines.delay

/**
 * Five second wordmark intro: black canvas, chrome letters landing one by one,
 * a slow silver bloom behind them and a hairline that fills for the whole
 * duration. Any tap skips it, and it never blocks the library scan because the
 * scan runs in the repository, not on this screen.
 */
@Composable
fun WordmarkIntro(onSkip: () -> Unit) {
    val started = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started.value = true }

    val bloom = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(700)
        bloom.animateTo(1f, tween(2_600, easing = LinearEasing))
    }

    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(5_000, easing = LinearEasing)) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Zero.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onSkip,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        listOf(Zero.Silver.copy(alpha = 0.10f * bloom.value), Color.Transparent),
                    ),
                ),
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row {
                "ZERO".forEachIndexed { index, letter ->
                    val appear by animateFloatAsState(
                        targetValue = if (started.value) 1f else 0f,
                        animationSpec = tween(
                            durationMillis = 640,
                            delayMillis = 140 * index,
                            easing = FastOutSlowInEasing,
                        ),
                        label = "wordmark-$index",
                    )
                    Text(
                        text = letter.toString(),
                        color = Zero.Chrome,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.graphicsLayer {
                            alpha = appear
                            translationY = (1f - appear) * 28f
                            scaleX = 0.9f + appear * 0.1f
                            scaleY = 0.9f + appear * 0.1f
                        },
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            val subtitle by animateFloatAsState(
                targetValue = if (started.value) 1f else 0f,
                animationSpec = tween(900, delayMillis = 780),
                label = "wordmark-sub",
            )
            Text(
                text = "PLAYER",
                color = Zero.SilverDim,
                fontSize = 13.sp,
                letterSpacing = 10.sp,
                modifier = Modifier.alpha(subtitle),
            )

            Spacer(Modifier.height(26.dp))

            Box(
                modifier = Modifier
                    .width(132.dp)
                    .height(1.2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Zero.Surface3),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress.value)
                        .background(Zero.chromeGradient),
                )
            }
        }

        Text(
            text = "Tap to skip",
            color = Zero.TextTertiary,
            fontSize = 11.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 44.dp),
        )
    }
}
