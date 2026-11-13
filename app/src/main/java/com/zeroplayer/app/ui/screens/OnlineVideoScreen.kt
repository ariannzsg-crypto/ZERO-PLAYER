package com.zeroplayer.app.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.zeroplayer.app.net.online.YouTubeService
import com.zeroplayer.app.ui.components.MicroLabel
import com.zeroplayer.app.ui.components.ZeroIconButton
import com.zeroplayer.app.ui.nav.LocalNav
import com.zeroplayer.app.ui.theme.Zero

/**
 * YouTube playback through the official embedded player.
 *
 * ZERO PLAYER never extracts a stream: the WebView hosts YouTube's own iframe
 * player, so advertising, attribution and the background-playback restriction
 * are exactly what YouTube enforces. Playback stops when this screen is closed.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun OnlineVideoScreen(videoId: String, title: String) {
    val nav = LocalNav.current
    val context = LocalContext.current
    val html = remember(videoId) { YouTubeService.embedHtml(videoId) }
    val webView = remember { mutableStateOf<WebView?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(Zero.Black)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ZeroIconButton(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                onClick = { nav.pop() },
            )
            Spacer(Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                MicroLabel("YouTube")
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = Zero.Silver,
                    maxLines = 1,
                )
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://www.youtube.com/watch?v=$videoId"),
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                    contentDescription = null,
                    tint = Zero.SilverDim,
                    modifier = Modifier.width(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("YouTube", style = MaterialTheme.typography.labelSmall, color = Zero.SilverDim)
            }
        }

        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.domStorageEnabled = true
                    webViewClient = WebViewClient()
                    setBackgroundColor(android.graphics.Color.BLACK)
                    loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "utf-8", null)
                    webView.value = this
                }
            },
        )

        Text(
            text = "Playback runs inside YouTube's official player: ads and attribution are " +
                "untouched, and audio stops with this screen.",
            style = MaterialTheme.typography.labelSmall,
            color = Zero.TextTertiary,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp),
        )
        Box(Modifier.height(8.dp))
    }

    DisposableEffect(Unit) {
        onDispose {
            webView.value?.let { view ->
                view.loadUrl("about:blank")
                view.destroy()
            }
            webView.value = null
        }
    }
}
