package com.bluemarlin.puttmeter.presentation.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.*
import com.bluemarlin.puttmeter.wearable.presentation.calibration.CalibrationScreen
import com.bluemarlin.puttmeter.wearable.presentation.calibration.CalibrationViewModel
import com.bluemarlin.puttmeter.wearable.presentation.calibration.CalibrationViewModelFactory
import com.bluemarlin.puttmeter.wearable.presentation.debug.SensorDebugScreen
import com.bluemarlin.puttmeter.wearable.presentation.debug.SensorDebugViewModel
import com.bluemarlin.puttmeter.wearable.presentation.debug.SensorDebugViewModelFactory
import com.bluemarlin.puttmeter.wearable.presentation.measurement.MeasurementScreen
import com.bluemarlin.puttmeter.wearable.presentation.measurement.MeasurementViewModel
import com.bluemarlin.puttmeter.wearable.presentation.measurement.MeasurementViewModelFactory
import com.bluemarlin.puttmeter.wearable.presentation.settings.SettingsScreen
import com.bluemarlin.puttmeter.wearable.presentation.settings.SettingsViewModel
import com.bluemarlin.puttmeter.wearable.presentation.settings.SettingsViewModelFactory

enum class Screen {
    MENU,
    MEASUREMENT,
    CALIBRATION,
    SETTINGS,
    SENSOR_CHECK
}

@Composable
fun NavigationHost(
    measurementViewModelFactory: MeasurementViewModelFactory,
    calibrationViewModelFactory: CalibrationViewModelFactory,
    settingsViewModelFactory: SettingsViewModelFactory,
    debugViewModelFactory: SensorDebugViewModelFactory
) {
    var currentScreen by remember { mutableStateOf(Screen.MENU) }
    
    when (currentScreen) {
        Screen.MENU -> {
            MenuScreen(
                onNavigateToMeasurement = { currentScreen = Screen.MEASUREMENT },
                onNavigateToCalibration = { currentScreen = Screen.CALIBRATION },
                onNavigateToSettings = { currentScreen = Screen.SETTINGS }
            )
        }
        Screen.MEASUREMENT -> {
            val measurementViewModel = viewModel<MeasurementViewModel>(factory = measurementViewModelFactory)
            MeasurementScreen(
                viewModel = measurementViewModel,
                onNavigateBack = { currentScreen = Screen.MENU }
            )
        }
        Screen.CALIBRATION -> {
            val calibrationViewModel = viewModel<CalibrationViewModel>(factory = calibrationViewModelFactory)
            CalibrationScreen(
                viewModel = calibrationViewModel,
                onNavigateBack = { currentScreen = Screen.MENU }
            )
        }
        Screen.SETTINGS -> {
            val settingsViewModel = viewModel<SettingsViewModel>(factory = settingsViewModelFactory)
            SettingsScreen(
                viewModel = settingsViewModel,
                onNavigateToSensorCheck = { currentScreen = Screen.SENSOR_CHECK },
                onNavigateBack = { currentScreen = Screen.MENU }
            )
        }
        Screen.SENSOR_CHECK -> {
            val debugViewModel = viewModel<SensorDebugViewModel>(factory = debugViewModelFactory)
            SensorDebugScreen(
                viewModel = debugViewModel,
                onNavigateBack = { currentScreen = Screen.SETTINGS }
            )
        }
    }
}

@Composable
fun MenuScreen(
    onNavigateToMeasurement: () -> Unit,
    onNavigateToCalibration: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    Scaffold(
        timeText = { TimeText() }
    ) {
        ScalingLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background),
            contentPadding = PaddingValues(
                top = 32.dp,
                bottom = 32.dp,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Text(
                    text = "PuttMeter",
                    style = MaterialTheme.typography.title1,
                    color = MaterialTheme.colors.primary
                )
            }
            
            item {
                Chip(
                    onClick = onNavigateToMeasurement,
                    label = {
                        Text("거리 측정")
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            item {
                Chip(
                    onClick = onNavigateToCalibration,
                    label = {
                        Text("거리 보정")
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            item {
                Chip(
                    onClick = onNavigateToSettings,
                    label = {
                        Text("설정")
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

