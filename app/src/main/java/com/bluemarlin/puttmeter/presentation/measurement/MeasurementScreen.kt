package com.bluemarlin.puttmeter.presentation.measurement

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
import androidx.wear.compose.material.*
import com.bluemarlin.puttmeter.domain.model.StrokePhase
import com.bluemarlin.puttmeter.presentation.util.HapticFeedback

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
    
    // 단계별 진동 피드백 (단계 전환 시에만)
    var previousPhase by remember { mutableStateOf<StrokePhase?>(null) }
    
    LaunchedEffect(uiState.currentPhase) {
        val currentPhase = uiState.currentPhase
        
        // 이전 단계와 다를 때만 진동 발생
        if (previousPhase?.javaClass != currentPhase.javaClass) {
            when (currentPhase) {
                is StrokePhase.Address -> {
                    // Address 단계 진입 시 즉시 부드러운 진동으로 준비 완료 알림
                    haptic.addressDetected()
                }
                is StrokePhase.Impact -> {
                    // 임팩트 감지 시 강한 진동
                    haptic.impactDetected()
                }
                else -> {}
            }
        }
        
        previousPhase = currentPhase
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
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "%.1f m".format(uiState.lastStroke.predictedDistance),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Green
                    )
                    
                    // 추가 메트릭 표시
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "힘: ${uiState.lastStroke.metrics.impactForce.toInt()}N",
                            style = MaterialTheme.typography.caption2,
                            color = MaterialTheme.colors.onSurfaceVariant
                        )
                        Text(
                            text = "속도: ${uiState.lastStroke.metrics.clubSpeed.toInt()}m/s",
                            style = MaterialTheme.typography.caption2,
                            color = MaterialTheme.colors.onSurfaceVariant
                        )
                    }
                }
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
    val (text, color, subtitle) = when (phase) {
        is StrokePhase.Idle -> Triple("대기 중", Color.Gray, "측정을 시작하세요")
        is StrokePhase.Address -> Triple("어드레스", Color.Blue, "안정성: ${(phase.stabilityScore * 100).toInt()}%")
        is StrokePhase.Backswing -> Triple("백스윙", Color.Yellow, "거리: ${phase.backswingDistance.toInt()}")
        is StrokePhase.Downswing -> Triple("다운스윙", Color.LightGray, "속도: ${phase.clubSpeed.toInt()} m/s")
        is StrokePhase.Impact -> Triple("임팩트!", Color.Red, "힘: ${phase.impactForce.toInt()} N")
        is StrokePhase.FollowThrough -> Triple("팔로우스루", Color.Green, "완료")
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
            
            // 상세 정보 표시
            Text(
                text = subtitle,
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.onSurfaceVariant
            )
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
                    text = "임팩트 힘: %.1f N".format(stroke.metrics.impactForce),
                    style = MaterialTheme.typography.caption2
                )
                Text(
                    text = "클럽 속도: %.1f m/s".format(stroke.metrics.clubSpeed),
                    style = MaterialTheme.typography.caption2
                )
            }
            
            // 시간 메트릭
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "스윙 시간: %.0f ms".format(stroke.metrics.swingTime.toFloat()),
                    style = MaterialTheme.typography.caption2
                )
                Text(
                    text = "템포: %.1f".format(stroke.metrics.tempoRatio),
                    style = MaterialTheme.typography.caption2
                )
            }
            
            // 품질 메트릭
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "부드러움: ${(stroke.metrics.smoothness * 100).toInt()}%",
                    style = MaterialTheme.typography.caption2
                )
                Text(
                    text = "안정성: ${(stroke.metrics.addressStability * 100).toInt()}%",
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

