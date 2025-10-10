package com.bluemarlin.puttmeter.presentation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.Text
import com.bluemarlin.puttmeter.presentation.debug.SensorDebugViewModelFactory
import com.bluemarlin.puttmeter.presentation.measurement.MeasurementViewModelFactory
import com.bluemarlin.puttmeter.presentation.navigation.NavigationHost
import com.bluemarlin.puttmeter.presentation.theme.PuttMeterTheme

class MainActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        
        setTheme(android.R.style.Theme_DeviceDefault)
        
        setContent {
            PuttMeterTheme {
                PermissionWrapper(
                    measurementFactory = MeasurementViewModelFactory(this),
                    debugFactory = SensorDebugViewModelFactory(this)
                )
            }
        }
    }
}

@Composable
fun PermissionWrapper(
    measurementFactory: MeasurementViewModelFactory,
    debugFactory: SensorDebugViewModelFactory
) {
    val context = LocalContext.current
    
    // 가속도계/자이로스코프는 BODY_SENSORS 권한이 필요하지 않을 수 있음
    // 바로 센서 접근을 시도하고, 실패하면 그때 안내
    Log.d("PuttMeter", "Starting app - Accelerometer and Gyroscope don't require BODY_SENSORS permission")
    
    NavigationHost(
        measurementViewModelFactory = measurementFactory,
        debugViewModelFactory = debugFactory
    )
}

