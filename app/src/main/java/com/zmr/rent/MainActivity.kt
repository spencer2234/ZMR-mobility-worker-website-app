package com.zmr.rent

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.zmr.rent.ui.ZmrApp
import com.zmr.rent.util.AppLanguage

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(AppLanguage.wrap(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        // The app always uses its light, high-contrast theme: white icons over the blue
        // top bar, dark icons over the white bottom edge, whatever the phone's dark-mode setting.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        val container = (application as ZmrApplication).container
        setContent { ZmrApp(container) }
    }
}
