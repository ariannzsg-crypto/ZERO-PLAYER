package com.zeroplayer.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeroplayer.app.ui.components.MicroLabel
import com.zeroplayer.app.ui.components.OutlinePillButton
import com.zeroplayer.app.ui.theme.Zero

@Composable
fun PermissionScreen(onRequest: () -> Unit, onOpenSettings: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Zero.Black)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 30.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.linearGradient(listOf(Zero.Surface3, Zero.Surface1))),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Z",
                    style = MaterialTheme.typography.headlineLarge.copy(brush = Zero.chromeGradient),
                )
            }
            Spacer(Modifier.height(26.dp))
            MicroLabel("Zero player")
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Your music,\nnothing else.",
                style = MaterialTheme.typography.displayLarge,
                color = Zero.TextPrimary,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = "ZERO PLAYER needs read access to the audio files on this device to build your library. " +
                    "There is no account, no analytics and no network permission in the app.",
                style = MaterialTheme.typography.bodyLarge,
                color = Zero.TextSecondary,
            )
            Spacer(Modifier.height(28.dp))
            OutlinePillButton(
                text = "Grant music access",
                onClick = onRequest,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Box(modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(
                    text = "Already denied? Open app settings",
                    style = MaterialTheme.typography.labelMedium,
                    color = Zero.TextTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Zero.Surface1)
                        .clickable(onClick = onOpenSettings)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Tap here if the system dialog no longer appears",
                style = MaterialTheme.typography.bodySmall,
                color = Zero.TextTertiary.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }

        Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 26.dp)) {
            Text(
                text = "OFFLINE · NO NETWORK PERMISSION · NO TRACKING",
                fontSize = 9.sp,
                letterSpacing = 2.sp,
                color = Zero.TextTertiary.copy(alpha = 0.6f),
            )
        }
    }
}
