package com.bluemarlin.puttmeter.wearable.presentation.measurement

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
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
fun MeasurementScreen(
    viewModel: MeasurementViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val haptic = remember { HapticFeedback(context) }
    
    // Back navigation handler
    BackHandler {
        onNavigateBack()
    }
    
    // 측정 시작 시 진동 (카운트다운 종료)
    LaunchedEffect(uiState.isActive) {
        if (uiState.isActive) {
            haptic.impactDetected()  // 측정 시작 알림
        }
    }
    
    // 스트로크 완료 시 진동
    LaunchedEffect(uiState.lastStroke) {
        uiState.lastStroke?.let {
            haptic.measurementComplete()
        }
    }
    
    Scaffold(
        modifier = modifier,
        timeText = { TimeText() }
    ) {
        when {
            uiState.isCountingDown -> {
                CountdownScreen(
                    countdownSeconds = uiState.countdownSeconds,
                    onCancel = { viewModel.stopMeasurement() }
                )
            }
            uiState.isActive -> {
                ActiveMeasurementScreen(
                    uiState = uiState,
                    onStop = { viewModel.stopMeasurement() },
                    onRestart = { 
                        haptic.tapStart()
                        viewModel.restartMeasurement() 
                    }
                )
            }
            else -> {
                IdleMeasurementScreen(
                    uiState = uiState,
                    onStart = { 
                        haptic.tapStart()
                        viewModel.startMeasurement() 
                    },
                    onClearSession = { viewModel.clearSession() }
                )
            }
        }
    }
}

@Composable
fun CountdownScreen(
    countdownSeconds: Int,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val haptic = remember { HapticFeedback(context) }
    
    // 카운트다운 숫자가 바뀔 때마다 진동
    LaunchedEffect(countdownSeconds) {
        haptic.tapStart()  // 가벼운 진동
    }
    
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
                text = "준비",
                style = MaterialTheme.typography.title2,
                color = MaterialTheme.colors.primary
            )
            
            // 카운트다운 숫자
            Text(
                text = countdownSeconds.toString(),
                fontSize = 72.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.primary
            )
            
            Text(
                text = "자세를 잡으세요",
                style = MaterialTheme.typography.body1,
                color = MaterialTheme.colors.onSurface
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 취소 버튼
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
fun IdleMeasurementScreen(
    uiState: MeasurementUiState,
    onStart: () -> Unit,
    onClearSession: () -> Unit
) {
    val listState = rememberScalingLazyListState()
    
    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background),
        state = listState,
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
                text = "퍼팅 측정",
                style = MaterialTheme.typography.title2,
                color = MaterialTheme.colors.primary
            )
        }
        
        // 마지막 측정 결과
        if (uiState.lastStroke != null) {
            item {
                LastStrokeCard(uiState = uiState)
            }
        }
        
        // 세션 통계
        if (uiState.strokeCount > 0) {
            item {
                SessionStatsCard(uiState = uiState)
            }
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
        
        // 세션 초기화 버튼
        if (uiState.strokeCount > 0) {
            item {
                Button(
                    onClick = onClearSession,
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
fun ActiveMeasurementScreen(
    uiState: MeasurementUiState,
    onStop: () -> Unit,
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
        // 예측 거리 및 최대 속도 표시 (가장 먼저)
        item {
            Card(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // 예측 거리 (크게 표시)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "예측 거리",
                            style = MaterialTheme.typography.caption2,
                            color = MaterialTheme.colors.onSurfaceVariant
                        )
                        Text(
                            text = "%.1f m".format(uiState.predictedDistance),
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (uiState.currentMaxSpeed > 0f) Color.Green else Color.Gray
                        )
                    }
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                    
                    // 최대 속도 (작게 표시)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "속도: ",
                            style = MaterialTheme.typography.caption2,
                            color = MaterialTheme.colors.onSurfaceVariant
                        )
                        Text(
                            text = "%.2f m/s".format(uiState.currentMaxSpeed),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colors.onSurface
                        )
                    }
                }
            }
        }
        
        // 세션 카운터
        if (uiState.strokeCount > 0) {
            item {
                Text(
                    text = "측정: ${uiState.strokeCount}회",
                    style = MaterialTheme.typography.caption2,
                    color = MaterialTheme.colors.onSurfaceVariant
                )
            }
        }
        
        // 측정 완료 버튼
        item {
            Button(
                onClick = onStop,
                modifier = Modifier.fillMaxWidth(0.95f),
                colors = ButtonDefaults.secondaryButtonColors()
            ) {
                Text("측정 완료")
            }
        }
        
        // 다시 측정 버튼
        item {
            Button(
                onClick = onRestart,
                modifier = Modifier.fillMaxWidth(0.95f),
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
fun LastStrokeCard(uiState: MeasurementUiState) {
    val stroke = uiState.lastStroke ?: return
    
    Card(
        onClick = {},
        enabled = false,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "마지막 퍼팅",
                style = MaterialTheme.typography.caption1,
                color = MaterialTheme.colors.primary
            )
            
            Text(
                text = "%.1f m".format(stroke.predictedDistance),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Green
            )
            
            // 기본 메트릭
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "최대 속도: %.2f m/s".format(stroke.maxSpeed),
                    style = MaterialTheme.typography.caption2
                )
                Text(
                    text = "스윙 시간: %.0f ms".format(stroke.swingTime.toFloat()),
                    style = MaterialTheme.typography.caption2
                )
            }
        }
    }
}

@Composable
fun SessionStatsCard(uiState: MeasurementUiState) {
    Card(
        onClick = {},
        enabled = false,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "세션 통계",
                style = MaterialTheme.typography.caption1,
                color = MaterialTheme.colors.primary
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "평균 거리",
                        style = MaterialTheme.typography.caption2,
                        color = MaterialTheme.colors.onSurfaceVariant
                    )
                    Text(
                        text = "%.1f m".format(uiState.averageDistance),
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "퍼팅 횟수",
                        style = MaterialTheme.typography.caption2,
                        color = MaterialTheme.colors.onSurfaceVariant
                    )
                    Text(
                        text = "${uiState.strokeCount}회",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

