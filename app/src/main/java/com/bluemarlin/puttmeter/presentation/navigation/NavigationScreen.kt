package com.bluemarlin.puttmeter.presentation.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.*
import com.bluemarlin.puttmeter.presentation.debug.SensorDebugScreen
import com.bluemarlin.puttmeter.presentation.debug.SensorDebugViewModelFactory
import com.bluemarlin.puttmeter.presentation.measurement.MeasurementScreen
import com.bluemarlin.puttmeter.presentation.measurement.MeasurementViewModelFactory

enum class Screen {
    MENU,
    MEASUREMENT,
    DEBUG
}

@Composable
fun NavigationHost(
    measurementViewModelFactory: MeasurementViewModelFactory,
    debugViewModelFactory: SensorDebugViewModelFactory
) {
    var currentScreen by remember { mutableStateOf(Screen.MENU) }
    
    when (currentScreen) {
        Screen.MENU -> {
            MenuScreen(
                onNavigateToMeasurement = { currentScreen = Screen.MEASUREMENT },
                onNavigateToDebug = { currentScreen = Screen.DEBUG }
            )
        }
        Screen.MEASUREMENT -> {
            val measurementViewModel = viewModel<com.bluemarlin.puttmeter.presentation.measurement.MeasurementViewModel>(factory = measurementViewModelFactory)
            MeasurementScreen(viewModel = measurementViewModel)
        }
        Screen.DEBUG -> {
            val debugViewModel = viewModel<com.bluemarlin.puttmeter.presentation.debug.SensorDebugViewModel>(factory = debugViewModelFactory)
            SensorDebugScreen(viewModel = debugViewModel)
        }
    }
}

@Composable
fun MenuScreen(
    onNavigateToMeasurement: () -> Unit,
    onNavigateToDebug: () -> Unit
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
                        Text("📏 퍼팅 측정")
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            item {
                Chip(
                    onClick = onNavigateToDebug,
                    label = {
                        Text("🔧 센서 디버그")
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

