package com.bluemarlin.puttmeter.wearable.presentation.util

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

/**
 * 화면이 꺼지지 않도록 유지
 * 측정 중에 화면이 꺼지는 것을 방지
 */
@Composable
fun KeepScreenOn() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = (context as? ComponentActivity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

