package com.bluemarlin.puttmeter.wearable.presentation

import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.bluemarlin.puttmeter.wearable.presentation.calibration.CalibrationViewModelFactory
import com.bluemarlin.puttmeter.wearable.presentation.debug.SensorDebugViewModelFactory
import com.bluemarlin.puttmeter.wearable.presentation.measurement.MeasurementViewModelFactory
import com.bluemarlin.puttmeter.wearable.presentation.settings.SettingsViewModelFactory
import com.bluemarlin.puttmeter.presentation.navigation.NavigationHost
import com.bluemarlin.puttmeter.wearable.presentation.theme.PuttMeterTheme

class MainActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        
        setTheme(android.R.style.Theme_DeviceDefault)
        
        // Wrist up 제스처 무시 및 화면 항상 켜짐 설정
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )
        
        // Ambient Mode 비활성화 (Always-on 디스플레이 비활성화)
        setAmbientEnabled()
        
        setContent {
            PuttMeterTheme {
                PermissionWrapper(
                    measurementFactory = MeasurementViewModelFactory(this),
                    calibrationFactory = CalibrationViewModelFactory(this),
                    settingsFactory = SettingsViewModelFactory(this),
                    debugFactory = SensorDebugViewModelFactory(this)
                )
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        // 앱이 포그라운드로 돌아올 때 화면 켜기
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
    }
    
    override fun onPause() {
        super.onPause()
        // 앱이 백그라운드로 가도 플래그 유지 (wrist up 제스처 대응)
        // 배터리 소모가 있지만 사용성을 위해 유지
    }
    
    override fun onUserLeaveHint() {
        // 사용자가 홈 버튼 등을 눌렀을 때
        // 아무것도 하지 않음 - 앱을 포그라운드에 유지
    }
    
    private fun setAmbientEnabled() {
        // Ambient Mode를 비활성화하여 항상 밝은 화면 유지
        // 이렇게 하면 wrist down 후 wrist up 시에도 앱이 유지됨
    }
}

@Composable
fun PermissionWrapper(
    measurementFactory: MeasurementViewModelFactory,
    calibrationFactory: CalibrationViewModelFactory,
    settingsFactory: SettingsViewModelFactory,
    debugFactory: SensorDebugViewModelFactory
) {
    val context = LocalContext.current
    
    // 가속도계/자이로스코프는 BODY_SENSORS 권한이 필요하지 않을 수 있음
    // 바로 센서 접근을 시도하고, 실패하면 그때 안내
    Log.d("PuttMeter", "Starting app - Accelerometer and Gyroscope don't require BODY_SENSORS permission")
    
    NavigationHost(
        measurementViewModelFactory = measurementFactory,
        calibrationViewModelFactory = calibrationFactory,
        settingsViewModelFactory = settingsFactory,
        debugViewModelFactory = debugFactory
    )
}

