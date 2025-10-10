package com.bluemarlin.puttmeter.presentation.measurement

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
import androidx.wear.compose.material.*
import com.bluemarlin.puttmeter.domain.model.StrokePhase
import com.bluemarlin.puttmeter.presentation.util.HapticFeedback

@Composable
fun MeasurementScreen(
    viewModel: MeasurementViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val haptic = remember { HapticFeedback(context) }
    
    // 임팩트 감지 시 진동
    LaunchedEffect(uiState.currentPhase) {
        when (uiState.currentPhase) {
            is StrokePhase.Impact -> {
                haptic.impactDetected()
            }
            else -> {}
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
        if (uiState.isActive) {
            ActiveMeasurementScreen(
                uiState = uiState,
                onStop = { viewModel.stopMeasurement() }
            )
        } else {
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

@Composable
fun IdleMeasurementScreen(
    uiState: MeasurementUiState,
    onStart: () -> Unit,
    onClearSession: () -> Unit
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
    onStop: () -> Unit
) {
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
            // 상태 표시
            PhaseIndicator(phase = uiState.currentPhase)
            
            // 마지막 측정값 (있으면 표시)
            if (uiState.lastStroke != null) {
                Text(
                    text = "%.1f m".format(uiState.lastStroke.predictedDistance),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Green
                )
            }
            
            // 세션 카운터
            if (uiState.strokeCount > 0) {
                Text(
                    text = "${uiState.strokeCount}회",
                    style = MaterialTheme.typography.caption1,
                    color = MaterialTheme.colors.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 중지 버튼
            Button(
                onClick = onStop,
                modifier = Modifier.fillMaxWidth(0.8f),
                colors = ButtonDefaults.secondaryButtonColors()
            ) {
                Text("측정 중지")
            }
        }
    }
}

@Composable
fun PhaseIndicator(phase: StrokePhase) {
    val (text, color) = when (phase) {
        is StrokePhase.Idle -> "대기 중" to Color.Gray
        is StrokePhase.Backswing -> "백스윙" to Color.Yellow
        is StrokePhase.Downswing -> "다운스윙" to Color.Yellow
        is StrokePhase.Impact -> "임팩트!" to Color.Red
        is StrokePhase.FollowThrough -> "완료" to Color.Green
    }
    
    Card(
        onClick = {},
        enabled = false,
        modifier = Modifier.fillMaxWidth(0.9f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = text,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            
            // 애니메이션 표시
            when (phase) {
                is StrokePhase.Idle -> Text("퍼팅을 준비하세요", style = MaterialTheme.typography.caption2)
                is StrokePhase.Backswing -> Text("감지 중...", style = MaterialTheme.typography.caption2)
                is StrokePhase.Downswing -> Text("감지 중...", style = MaterialTheme.typography.caption2)
                is StrokePhase.Impact -> Text("볼 임팩트 감지!", style = MaterialTheme.typography.caption2)
                is StrokePhase.FollowThrough -> Text("측정 완료", style = MaterialTheme.typography.caption2)
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
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "힘: %.1f m/s²".format(stroke.metrics.peakAcceleration),
                    style = MaterialTheme.typography.caption2
                )
                Text(
                    text = "%.0f ms".format(stroke.metrics.swingTime.toFloat()),
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

