package com.bluemarlin.puttmeter.wearable.presentation.calibration

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import com.bluemarlin.puttmeter.wearable.presentation.util.HapticFeedback
import com.bluemarlin.puttmeter.wearable.presentation.util.KeepScreenOn

@Composable
fun CalibrationScreen(
    viewModel: CalibrationViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val haptic = remember { HapticFeedback(context) }
    
    BackHandler {
        when (uiState.state) {
            CalibrationState.INPUT_DISTANCE -> viewModel.cancelDistanceInput()
            CalibrationState.IDLE -> onNavigateBack()
            else -> viewModel.cancelMeasurement()
        }
    }
    
    Scaffold(
        modifier = modifier,
        timeText = { TimeText() }
    ) {
        when (uiState.state) {
            CalibrationState.IDLE -> {
                IdleCalibrationScreen(
                    uiState = uiState,
                    onStart = {
                        haptic.tapStart()
                        viewModel.startMeasurement()
                    },
                    onReset = { viewModel.resetCalibration() }
                )
            }
            CalibrationState.PRACTICE -> {
                PracticeCalibrationScreen(
                    uiState = uiState,
                    onCancel = { viewModel.cancelMeasurement() }
                )
            }
            CalibrationState.READY -> {
                ReadyCalibrationScreen()
            }
            CalibrationState.MEASURING -> {
                MeasuringCalibrationScreen(
                    uiState = uiState,
                    onCancel = { viewModel.cancelMeasurement() }
                )
            }
            CalibrationState.INPUT_DISTANCE -> {
                DistanceInputScreen(
                    measuredSpeed = uiState.lastMeasuredSpeed,
                    onDistanceInput = { distance ->
                        viewModel.inputActualDistance(distance)
                    },
                    onCancel = {
                        viewModel.cancelDistanceInput()
                    }
                )
            }
        }
    }
}

@Composable
fun IdleCalibrationScreen(
    uiState: CalibrationUiState,
    onStart: () -> Unit,
    onReset: () -> Unit
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
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 타이틀
        item {
            Text(
                text = "거리 보정",
                style = MaterialTheme.typography.title2,
                color = MaterialTheme.colors.primary
            )
        }
        
        // 현재 보정 파라미터 (회귀분석 결과)
        if (uiState.measurementCount > 0) {
            item {
                Card(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "보정 함수",
                            style = MaterialTheme.typography.caption1,
                            color = MaterialTheme.colors.onSurfaceVariant
                        )
                        Text(
                            text = "거리 = %.1f × 속도 %+.1f".format(
                                uiState.calibrationData.slope,
                                uiState.calibrationData.intercept
                            ),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Green
                        )
                        Text(
                            text = if (uiState.calibrationData.requiredCount > 0) {
                                "${uiState.measurementCount}/${uiState.calibrationData.requiredCount} 측정 완료"
                            } else {
                                "${uiState.measurementCount}회 측정 완료"
                            },
                            style = MaterialTheme.typography.caption2,
                            color = MaterialTheme.colors.onSurfaceVariant
                        )
                    }
                }
            }
        }
        
        // 안내 메시지
        item {
            Text(
                text = "스윙 후 실제 거리를\n입력하여 보정하세요",
                style = MaterialTheme.typography.body2,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colors.onSurfaceVariant
            )
        }
        
        // 측정 시작 버튼
        item {
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                Text("측정 시작")
            }
        }
        
        // 초기화 버튼
        if (uiState.measurementCount > 0) {
            item {
                Button(
                    onClick = onReset,
                    modifier = Modifier.fillMaxWidth(0.9f),
                    colors = ButtonDefaults.secondaryButtonColors()
                ) {
                    Text("초기화")
                }
            }
        }
    }
}

@Composable
fun PracticeCalibrationScreen(
    uiState: CalibrationUiState,
    onCancel: () -> Unit
) {
    KeepScreenOn()
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "보정 준비",
                style = MaterialTheme.typography.title2,
                color = MaterialTheme.colors.primary
            )
            
            Text(
                text = "2~3회 연습 스윙 후\n2초간 정지하세요",
                style = MaterialTheme.typography.body1,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colors.onSurface
            )
            
            // 정지 진행률 표시
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isWristUp) {
                    Text(
                        text = "⚠ 화면 확인 중",
                        style = MaterialTheme.typography.caption1,
                        color = Color.Yellow,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "손목을 내려 퍼팅 자세를 취하세요",
                        style = MaterialTheme.typography.caption3,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colors.onSurfaceVariant
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { uiState.idleProgress },
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(8.dp),
                        color = if (uiState.idleProgress >= 1f) Color.Green else MaterialTheme.colors.primary,
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = if (uiState.idleProgress >= 1f) "준비 완료!" else "%.1f초 / 2.0초".format(uiState.idleProgress * 2f),
                        style = MaterialTheme.typography.caption1,
                        color = if (uiState.idleProgress >= 1f) Color.Green else MaterialTheme.colors.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(0.8f),
                colors = ButtonDefaults.secondaryButtonColors()
            ) {
                Text("취소")
            }
        }
    }
}

@Composable
fun ReadyCalibrationScreen() {
    KeepScreenOn()
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "✓",
                fontSize = 72.sp,
                color = Color.Green
            )
            
            Text(
                text = "준비 완료!",
                style = MaterialTheme.typography.title1,
                color = Color.Green,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "퍼팅하세요",
                style = MaterialTheme.typography.body1,
                color = MaterialTheme.colors.onSurface
            )
        }
    }
}

@Composable
fun MeasuringCalibrationScreen(
    uiState: CalibrationUiState,
    onCancel: () -> Unit
) {
    KeepScreenOn()
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "보정 측정 중",
                style = MaterialTheme.typography.title2,
                color = Color.Green
            )
            
            Text(
                text = "퍼팅하세요",
                style = MaterialTheme.typography.body1,
                color = MaterialTheme.colors.onSurface
            )
            
            // 현재 최대 속도 표시
            if (uiState.currentMaxSpeed > 0f) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colors.surface,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                        )
                        .padding(vertical = 8.dp, horizontal = 12.dp)
                ) {
                    Text(
                        text = "최대 속도",
                        style = MaterialTheme.typography.caption3,
                        color = MaterialTheme.colors.onSurfaceVariant
                    )
                    Text(
                        text = "%.2f m/s".format(uiState.currentMaxSpeed),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Yellow
                    )
                }
            } else {
                Text(
                    text = "●",
                    fontSize = 32.sp,
                    color = Color.Green
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Button(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(0.7f),
                colors = ButtonDefaults.secondaryButtonColors()
            ) {
                Text("취소", style = MaterialTheme.typography.caption1)
            }
        }
    }
}

@Composable
fun DistanceInputScreen(
    measuredSpeed: Float,
    onDistanceInput: (Float) -> Unit,
    onCancel: () -> Unit
) {
    var distanceText by remember { mutableStateOf("") }
    
    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background),
        contentPadding = PaddingValues(
            top = 24.dp,
            bottom = 24.dp,
            start = 16.dp,
            end = 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 타이틀
        item {
            Text(
                text = "실제 거리 입력",
                style = MaterialTheme.typography.title3,
                color = MaterialTheme.colors.primary
            )
        }
        
        // 측정된 속도
        item {
            Card(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "측정된 최대 속도",
                        style = MaterialTheme.typography.caption2,
                        color = MaterialTheme.colors.onSurfaceVariant
                    )
                    Text(
                        text = "%.2f m/s".format(measuredSpeed),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Green
                    )
                }
            }
        }
        
        // 현재 입력된 거리 표시
        item {
            Card(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "입력된 거리",
                        style = MaterialTheme.typography.caption2,
                        color = MaterialTheme.colors.onSurfaceVariant
                    )
                    Text(
                        text = if (distanceText.isEmpty()) "0.0 m" else "$distanceText m",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colors.primary
                    )
                }
            }
        }
        
        // 숫자 패드 - 첫 번째 줄
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                NumberButton("1", Modifier.weight(1f)) { distanceText += "1" }
                NumberButton("2", Modifier.weight(1f)) { distanceText += "2" }
                NumberButton("3", Modifier.weight(1f)) { distanceText += "3" }
            }
        }
        
        // 숫자 패드 - 두 번째 줄
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                NumberButton("4", Modifier.weight(1f)) { distanceText += "4" }
                NumberButton("5", Modifier.weight(1f)) { distanceText += "5" }
                NumberButton("6", Modifier.weight(1f)) { distanceText += "6" }
            }
        }
        
        // 숫자 패드 - 세 번째 줄
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                NumberButton("7", Modifier.weight(1f)) { distanceText += "7" }
                NumberButton("8", Modifier.weight(1f)) { distanceText += "8" }
                NumberButton("9", Modifier.weight(1f)) { distanceText += "9" }
            }
        }
        
        // 숫자 패드 - 네 번째 줄
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                NumberButton(".", Modifier.weight(1f)) {
                    if (!distanceText.contains(".")) {
                        distanceText += "."
                    }
                }
                NumberButton("0", Modifier.weight(1f)) { distanceText += "0" }
                NumberButton("←", Modifier.weight(1f)) {
                    if (distanceText.isNotEmpty()) {
                        distanceText = distanceText.dropLast(1)
                    }
                }
            }
        }
        
        // 확인 버튼
        item {
            Button(
                onClick = {
                    val distance = distanceText.toFloatOrNull()
                    if (distance != null && distance > 0f) {
                        onDistanceInput(distance)
                    }
                },
                modifier = Modifier.fillMaxWidth(0.95f),
                enabled = distanceText.isNotEmpty() && 
                          distanceText.toFloatOrNull()?.let { it > 0f } == true
            ) {
                Text("확인")
            }
        }
        
        // 취소 버튼
        item {
            Button(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(0.95f),
                colors = ButtonDefaults.secondaryButtonColors()
            ) {
                Text("취소")
            }
        }
    }
}

@Composable
fun NumberButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(36.dp),
        colors = ButtonDefaults.buttonColors(
            backgroundColor = MaterialTheme.colors.surface
        )
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
