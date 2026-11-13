package com.zeroplayer.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeroplayer.app.data.model.Song
import com.zeroplayer.app.playback.PlaybackController
import com.zeroplayer.app.playback.PlaybackState
import com.zeroplayer.app.ui.formatDuration
import com.zeroplayer.app.ui.theme.Zero

/* ------------------------------- chrome -------------------------------- */

@Composable
fun MicroLabel(text: String, color: Color = Zero.TextTertiary, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier,
    )
}

@Composable
fun ZeroDivider(modifier: Modifier = Modifier, color: Color = Zero.Hairline.copy(alpha = 0.55f)) {
    Box(
        modifier
            .fillMaxWidth()
            .height(0.6.dp)
            .background(color),
    )
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = Zero.TextPrimary,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Zero.TextTertiary,
                )
            }
        }
        if (actionLabel != null && onAction != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = Zero.SilverDim,
                )
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Zero.SilverDim,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
fun ZeroIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Zero.Silver,
    size: Dp = 40.dp,
    iconSize: Dp = 21.dp,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else tint.copy(alpha = 0.3f),
            modifier = Modifier.size(iconSize),
        )
    }
}

/* ------------------------------- buttons -------------------------------- */

@Composable
fun PlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Zero.chromeGradient)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play",
            tint = Zero.Black,
            modifier = Modifier.size(size * 0.46f),
        )
    }
}

/** Primary/secondary action row used by detail headers. */
@Composable
fun OutLinePlayRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .then(
                if (filled) {
                    Modifier.background(Zero.chromeGradient)
                } else {
                    Modifier
                        .background(Zero.Surface2)
                        .border(0.7.dp, Zero.Hairline, RoundedCornerShape(50))
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (filled) Zero.Black else Zero.Silver,
            modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (filled) Zero.Black else Zero.Silver,
        )
    }
}

@Composable
fun OutlinePillButton(
    text: String,
    icon: ImageVector? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .border(0.8.dp, Zero.Hairline, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Zero.Silver, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = Zero.Silver)
    }
}

/* ------------------------------- content -------------------------------- */

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    song: Song,
    isPlaying: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onMore: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    showArtwork: Boolean = true,
    leadingIndex: Int? = null,
    subtitleOverride: String? = null,
    selected: Boolean = false,
    selectionMode: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (selected) Zero.Surface2.copy(alpha = 0.9f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 18.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (selected) Zero.Chrome else Color.Transparent)
                    .border(1.2.dp, if (selected) Zero.Chrome else Zero.TextTertiary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = Zero.Black,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
        }
        if (showArtwork) {
            Box(contentAlignment = Alignment.Center) {
                CoverArt(
                    artworkUri = song.artworkUri.toString(),
                    label = song.title,
                    corner = 8.dp,
                    modifier = Modifier.size(48.dp),
                )
                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        EqualizerBars(active = true, barColor = Zero.Chrome, barWidth = 2.dp, height = 14.dp)
                    }
                }
            }
            Spacer(Modifier.width(14.dp))
        } else if (leadingIndex != null) {
            Box(modifier = Modifier.width(28.dp), contentAlignment = Alignment.CenterStart) {
                Text(
                    text = if (leadingIndex < 9) "0${leadingIndex + 1}" else "${leadingIndex + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isPlaying) Zero.Chrome else Zero.TextTertiary,
                )
            }
            Spacer(Modifier.width(6.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isPlaying) Zero.Chrome else Zero.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .basicMarquee(iterations = Int.MAX_VALUE),
                )
                if (isFavorite) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(Zero.Silver),
                    )
                }
            }
            Text(
                text = subtitleOverride ?: song.displaySubtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (isPlaying) Zero.SilverDim else Zero.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.width(10.dp))
        Text(
            text = formatDuration(song.durationMs),
            style = MaterialTheme.typography.labelMedium,
            color = Zero.TextTertiary,
        )
        if (onMore != null) {
            Box {
                IconButton(onClick = onMore, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "More options",
                        tint = Zero.TextTertiary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/** Three bar "now playing" indicator driven purely by animation. */
@Composable
fun EqualizerBars(
    active: Boolean,
    barColor: Color = Zero.Chrome,
    barWidth: Dp = 3.dp,
    height: Dp = 16.dp,
    modifier: Modifier = Modifier,
) {
    if (!active) {
        Box(modifier = modifier.height(height).width(barWidth))
        return
    }
    val transition = rememberInfiniteTransition(label = "bars")
    val bars = (0 until 3).map { index ->
        transition.animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 420 + index * 140, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "bar$index",
        )
    }
    Row(
        modifier = modifier.height(height),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        bars.forEach { value ->
            val animated by value
            Box(
                modifier = Modifier
                    .width(barWidth)
                    .height(height * (0.25f + animated * 0.75f))
                    .clip(RoundedCornerShape(1.dp))
                    .background(barColor),
            )
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 36.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Zero.Surface2),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Zero.SilverDim, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = Zero.TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = Zero.TextTertiary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            OutlinePillButton(text = actionLabel, onClick = onAction)
        }
    }
}

/* ------------------------------ mini player ----------------------------- */

@Composable
fun MiniPlayer(
    state: PlaybackState,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = state.hasTrack,
        enter = fadeIn(tween(220)),
        exit = fadeOut(tween(160)),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Zero.Surface2.copy(alpha = 0.96f))
                .border(0.7.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                .clickable(onClick = onOpen),
        ) {
            // Progress hairline
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.6.dp)
                    .background(Color.White.copy(alpha = 0.06f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(state.progress.coerceIn(0f, 1f))
                        .height(1.6.dp)
                        .background(Zero.silverGradient),
                )
            }
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoverArt(
                    artworkUri = state.artworkUri,
                    label = state.title,
                    corner = 9.dp,
                    modifier = Modifier.size(40.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = Zero.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = state.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = Zero.TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                ZeroIconButton(
                    icon = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    onClick = { PlaybackController.togglePlayPause() },
                    tint = Zero.Chrome,
                    iconSize = 24.dp,
                )
                ZeroIconButton(
                    icon = Icons.Rounded.SkipNext,
                    contentDescription = "Next",
                    onClick = { PlaybackController.next() },
                    tint = Zero.Silver,
                    iconSize = 22.dp,
                )
            }
        }
    }
}

/** Subtle inner glow used behind hero artwork. */
@Composable
fun AmbientGlow(modifier: Modifier = Modifier, color: Color = Color.White) {
    Box(
        modifier = modifier.background(
            Brush.radialGradient(
                listOf(color.copy(alpha = 0.16f), Color.Transparent),
            ),
        ),
    )
}

/** Standard vertical content padding used by scrolling screens. */
val ScreenPadding = PaddingValues(start = 0.dp, end = 0.dp, top = 0.dp, bottom = 96.dp)

@Composable
fun rememberRowInteraction(): MutableInteractionSource = remember { MutableInteractionSource() }

@Composable
fun HSpacer(height: Dp) = Spacer(Modifier.height(height))

@Composable
fun WSpacer(width: Dp) = Spacer(Modifier.width(width))

@Composable
fun RowScope.WeightSpacer() = Spacer(Modifier.weight(1f))

/** Numeric suffix used by the stats line on the home hero. */
fun playsLabel(count: Int): String {
    if (count <= 0) return "Not played yet"
    return "$count play" + if (count == 1) "" else "s"
}

@Composable
fun TransparentIcon(icon: ImageVector, tint: Color = Zero.SilverDim, size: Dp = 18.dp) {
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size))
}

@Composable
fun SmallLabel(text: String) {
    Text(
        text = text,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        color = Zero.TextTertiary,
    )
}
