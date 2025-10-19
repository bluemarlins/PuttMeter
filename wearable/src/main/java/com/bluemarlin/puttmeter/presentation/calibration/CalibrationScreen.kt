package com.bluemarlin.puttmeter.wearable.presentation.calibration

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
        onNavigateBack()
    }
    
    // 측정 시작 시 진동 (카운트다운 종료)
    LaunchedEffect(uiState.isActive) {
        if (uiState.isActive) {
            haptic.impactDetected()  // 측정 시작 알림
        }
    }
    
    // 측정 완료 시 진동
    LaunchedEffect(uiState.waitingForDistance) {
        if (uiState.waitingForDistance) {
            haptic.measurementComplete()
        }
    }
    
    Scaffold(
        modifier = modifier,
        timeText = { TimeText() }
    ) {
        when {
            uiState.waitingForDistance -> {
                DistanceInputScreen(
                    measuredSpeed = uiState.lastMeasuredSpeed,
                    onDistanceInput = { distance ->
                        viewModel.inputActualDistance(distance)
                    },
                    onCancel = {
                        viewModel.cancelCalibration()
                    }
                )
            }
            uiState.isActive -> {
                ActiveCalibrationScreen(
                    currentSpeed = uiState.currentMaxSpeed,
                    onStop = { viewModel.stopMeasurement() },
                    onComplete = { viewModel.completeSwing() },
                    onRestart = {
                        haptic.tapStart()
                        viewModel.restartMeasurement()
                    }
                )
            }
            else -> {
                IdleCalibrationScreen(
                    uiState = uiState,
                    onStart = {
                        haptic.tapStart()
                        viewModel.startMeasurement()
                    },
                    onReset = { viewModel.resetCalibration() }
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
        
        // 현재 보정 계수
        if (uiState.calibrationData.averageFactor > 0f) {
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
                            text = "현재 보정 계수",
                            style = MaterialTheme.typography.caption1,
                            color = MaterialTheme.colors.onSurfaceVariant
                        )
                        Text(
                            text = "%.2f".format(uiState.calibrationData.averageFactor),
                            fontSize = 24.sp,
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
fun ActiveCalibrationScreen(
    currentSpeed: Float,
    onStop: () -> Unit,
    onComplete: () -> Unit,
    onRestart: () -> Unit
) {
    // 화면이 꺼지지 않도록 유지
    KeepScreenOn()
    
    // 스크롤 상태 - 초기에 첫 번째 아이템을 중앙에 배치
    val listState = rememberScalingLazyListState(
        initialCenterItemIndex = 0
    )
    
    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background),
        state = listState,
        contentPadding = PaddingValues(
            top = 40.dp,
            bottom = 24.dp,
            start = 12.dp,
            end = 12.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 현재 최대 속도 카드 (가장 먼저)
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
                        text = "측정된 최대 속도",
                        style = MaterialTheme.typography.caption2,
                        color = MaterialTheme.colors.onSurfaceVariant
                    )
                    Text(
                        text = "%.2f m/s".format(currentSpeed),
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            currentSpeed > 0.3f -> Color.Green
                            currentSpeed > 0f -> Color.Yellow
                            else -> Color.Gray
                        }
                    )
                }
            }
        }
        
        // 거리 입력 버튼 (큰 버튼)
        item {
            Button(
                onClick = onComplete,
                modifier = Modifier.fillMaxWidth(),
                enabled = currentSpeed > 0.3f,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = if (currentSpeed > 0.3f) 
                        MaterialTheme.colors.primary 
                    else 
                        MaterialTheme.colors.surface
                )
            ) {
                Text(
                    text = if (currentSpeed > 0.3f) "거리 입력하기" else "스윙 후 클릭",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        // 측정 취소 버튼
        item {
            Button(
                onClick = onStop,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.secondaryButtonColors()
            ) {
                Text("취소")
            }
        }
        
        // 다시 측정 버튼
        item {
            Button(
                onClick = onRestart,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colors.surface
                )
            ) {
                Text("다시 측정")
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
                    // 소수점이 없을 때만 추가
                    if (!distanceText.contains(".")) {
                        distanceText += "."
                    }
                }
                NumberButton("0", Modifier.weight(1f)) { distanceText += "0" }
                NumberButton("←", Modifier.weight(1f)) {
                    // 백스페이스
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
                    if (distance != null && distance > 0f && distance <= 20f) {
                        onDistanceInput(distance)
                    }
                },
                modifier = Modifier.fillMaxWidth(0.95f),
                enabled = distanceText.isNotEmpty() && 
                          distanceText.toFloatOrNull()?.let { it > 0f && it <= 20f } == true
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

