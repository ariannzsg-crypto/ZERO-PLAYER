package com.zeroplayer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.zeroplayer.app.core.Graph
import com.zeroplayer.app.playback.PlaybackController
import com.zeroplayer.app.ui.ZeroApp
import com.zeroplayer.app.ui.theme.ZeroTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        Graph.init(this)
        PlaybackController.connect(this)

        setContent {
            ZeroTheme {
                ZeroApp()
            }
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            PlaybackController.release()
        }
        super.onDestroy()
    }
}
