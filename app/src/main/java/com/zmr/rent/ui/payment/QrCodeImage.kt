package com.zmr.rent.ui.payment

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.zmr.rent.domain.QrCode
import kotlin.math.floor

/**
 * Draws [content] as a QR code: black on white with the standard 4-module white border, and
 * whole-pixel modules so scanners see sharp edges. While it is on screen the display is turned
 * to full brightness, which makes it much easier to scan in daylight.
 */
@Composable
fun QrCodeImage(content: String, description: String, modifier: Modifier = Modifier) {
    val matrix = remember(content) { QrCode.encode(content) }
    KeepScreenBright()

    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .background(Color.White)
            .semantics { contentDescription = description },
    ) {
        val modules = matrix.size + 2 * QUIET_ZONE
        val cell = floor(size.minDimension / modules)
        // Centre the code; any leftover pixels become extra white border.
        val origin = (size.minDimension - cell * modules) / 2 + cell * QUIET_ZONE
        for (y in 0 until matrix.size) {
            for (x in 0 until matrix.size) {
                if (matrix.isDark(x, y)) {
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(origin + x * cell, origin + y * cell),
                        size = Size(cell, cell),
                    )
                }
            }
        }
    }
}

private const val QUIET_ZONE = 4

@Composable
private fun KeepScreenBright() {
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(activity) {
        val window = activity.window
        val previous = window.attributes.screenBrightness
        window.attributes = window.attributes.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
        }
        onDispose {
            window.attributes = window.attributes.apply { screenBrightness = previous }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
