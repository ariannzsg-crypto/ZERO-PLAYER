package com.zeroplayer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.zeroplayer.app.core.DiagLog
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
        val audioPermission = if (android.os.Build.VERSION.SDK_INT >= 33) {
            android.Manifest.permission.READ_MEDIA_AUDIO
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
        val granted = checkSelfPermission(audioPermission) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        DiagLog.d(
            "UI",
            "onCreate: sdk=${android.os.Build.VERSION.SDK_INT} audioPermission=$granted",
        )
        PlaybackController.connect(this)

        setContent {
            ZeroTheme {
                ZeroApp()
            }
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            DiagLog.d("UI", "onDestroy: releasing the controller")
            PlaybackController.release()
        }
        super.onDestroy()
    }
}
