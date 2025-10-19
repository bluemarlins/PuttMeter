package com.bluemarlin.puttmeter.wearable.presentation.measurement

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
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
fun MeasurementScreen(
    viewModel: MeasurementViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val haptic = remember { HapticFeedback(context) }

    // 화면 생명주기 관리
    DisposableEffect(Unit) {
        viewModel.onScreenEnter()
        
        onDispose {
            viewModel.onScreenExit()
        }
    }
    
    // Back navigation handler
    BackHandler {
        viewModel.onScreenExit()
        onNavigateBack()
    }
    
    Scaffold(
        modifier = modifier,
        timeText = { TimeText() }
    ) {
        when (uiState.state) {
            MeasurementState.IDLE -> {
                IdleScreen(
                    uiState = uiState,
                    onStart = {
                        haptic.tapStart()
                        viewModel.startMeasurement()
                    },
                    onClearSession = { viewModel.clearSession() },
                    onToggleDetailedStats = { viewModel.toggleDetailedStats() }
                )
            }
            MeasurementState.PRACTICE -> {
                PracticeScreen(
                    uiState = uiState,
                    onStop = { viewModel.stopMeasurement() }
                )
            }
            MeasurementState.READY -> {
                ReadyScreen()
            }
            MeasurementState.MEASURING -> {
                MeasuringScreen(
                    uiState = uiState,
                    onStop = { viewModel.stopMeasurement() }
                )
            }
            MeasurementState.RESULT -> {
                ResultScreen(
                    uiState = uiState,
                    onStop = { viewModel.stopMeasurement() }
                )
            }
        }
    }
}

/**
 * IDLE 상태 화면
 */
@Composable
fun IdleScreen(
    uiState: MeasurementUiState,
    onStart: () -> Unit,
    onClearSession: () -> Unit,
    onToggleDetailedStats: () -> Unit
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
                SessionStatsCard(
                    uiState = uiState,
                    onToggleDetailedStats = onToggleDetailedStats
                )
            }
        }

        // 측정 시작 버튼
        item {
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth(0.9f),
                colors = ButtonDefaults.primaryButtonColors()
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

/**
 * PRACTICE 상태 화면 (연습 스윙 + 정지 감지)
 */
@Composable
fun PracticeScreen(
    uiState: MeasurementUiState,
    onStop: () -> Unit
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
                text = "연습 스윙",
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
                // Wrist Up 경고 표시
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
            
            // 디버그 정보
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Accel: %.2f m/s²".format(uiState.debugAccelMagnitude),
                    style = MaterialTheme.typography.caption2,
                    color = MaterialTheme.colors.onSurfaceVariant
                )
                Text(
                    text = "Gyro: %.2f rad/s".format(uiState.debugGyroMagnitude),
                    style = MaterialTheme.typography.caption2,
                    color = MaterialTheme.colors.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = onStop,
                modifier = Modifier.fillMaxWidth(0.8f),
                colors = ButtonDefaults.secondaryButtonColors()
            ) {
                Text("취소")
            }
        }
    }
}

/**
 * READY 상태 화면 (측정 준비 완료)
 */
@Composable
fun ReadyScreen() {
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

/**
 * MEASURING 상태 화면 (측정 중)
 */
@Composable
fun MeasuringScreen(
    uiState: MeasurementUiState,
    onStop: () -> Unit
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
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            // 타이틀 + 안내 메시지 (컴팩트하게)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "측정 중",
                    style = MaterialTheme.typography.title3,
                    color = Color.Green
                )
                Text(
                    text = "퍼팅하세요",
                    style = MaterialTheme.typography.caption1,
                    color = MaterialTheme.colors.onSurfaceVariant
                )
            }
            
            // 현재 최대 속도 표시 (컴팩트하게)
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
                // 측정 중 인디케이터
                Text(
                    text = "●",
                    fontSize = 32.sp,
                    color = Color.Green
                )
            }
            
            // 디버그 정보 (컴팩트하게)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colors.surface,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                    )
                    .padding(vertical = 8.dp, horizontal = 12.dp)
            ) {
                // 스윙 상태
                Text(
                    text = if (uiState.debugIsInSwing) "스윙 중 ●" else "대기 중",
                    style = MaterialTheme.typography.caption2,
                    color = if (uiState.debugIsInSwing) Color.Green else MaterialTheme.colors.onSurfaceVariant,
                    fontWeight = if (uiState.debugIsInSwing) FontWeight.Bold else FontWeight.Normal
                )
                
                // 현재 속도
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "속도: ",
                        style = MaterialTheme.typography.caption3,
                        color = MaterialTheme.colors.onSurfaceVariant
                    )
                    Text(
                        text = "%.2f m/s".format(uiState.debugCurrentSpeed),
                        style = MaterialTheme.typography.caption2,
                        fontWeight = FontWeight.Medium,
                        color = if (uiState.debugCurrentSpeed > 0.3f) Color.Yellow else MaterialTheme.colors.onSurface
                    )
                }
                
                // 센서 값
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "가속도",
                            style = MaterialTheme.typography.caption3,
                            color = MaterialTheme.colors.onSurfaceVariant
                        )
                        Text(
                            text = "%.1f".format(uiState.debugAccelMagnitude),
                            style = MaterialTheme.typography.caption2,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colors.onSurface
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "자이로",
                            style = MaterialTheme.typography.caption3,
                            color = MaterialTheme.colors.onSurfaceVariant
                        )
                        Text(
                            text = "%.1f".format(uiState.debugGyroMagnitude),
                            style = MaterialTheme.typography.caption2,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colors.onSurface
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // 취소 버튼 (작게)
            Button(
                onClick = onStop,
                modifier = Modifier.fillMaxWidth(0.7f),
                colors = ButtonDefaults.secondaryButtonColors()
            ) {
                Text("취소", style = MaterialTheme.typography.caption1)
            }
        }
    }
}

/**
 * RESULT 상태 화면 (결과 표시)
 */
@Composable
fun ResultScreen(
    uiState: MeasurementUiState,
    onStop: () -> Unit
) {
    KeepScreenOn()
    
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
        // 타이틀
        item {
            Text(
                text = "측정 완료",
                style = MaterialTheme.typography.title2,
                color = Color.Green
            )
        }
        
        // 결과 카드
        item {
            Card(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "예측 거리",
                        style = MaterialTheme.typography.caption2,
                        color = MaterialTheme.colors.onSurfaceVariant
                    )
                    
                    Text(
                        text = "%.1f m".format(uiState.predictedDistance),
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Green
                    )
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "속도",
                                style = MaterialTheme.typography.caption2,
                                color = MaterialTheme.colors.onSurfaceVariant
                            )
                            Text(
                                text = "%.2f m/s".format(uiState.currentMaxSpeed),
                                style = MaterialTheme.typography.body2,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "각도",
                                style = MaterialTheme.typography.caption2,
                                color = MaterialTheme.colors.onSurfaceVariant
                            )
                            Text(
                                text = "%.1f°".format(uiState.lastStroke?.swingAngle ?: 0f),
                                style = MaterialTheme.typography.body2,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
        
        // 다음 측정 안내
        item {
            Card(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "다음 측정",
                        style = MaterialTheme.typography.caption1,
                        color = MaterialTheme.colors.primary
                    )
                    
                    // Wrist Up 경고 또는 정지 진행률
                    if (uiState.isWristUp) {
                        Text(
                            text = "⚠ 화면 확인 중",
                            style = MaterialTheme.typography.caption2,
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
                        Text(
                            text = "연습 스윙 후 2초간 정지하세요",
                            style = MaterialTheme.typography.caption2,
                            color = MaterialTheme.colors.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        
                        // 정지 진행률 표시
                        LinearProgressIndicator(
                            progress = { uiState.idleProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp),
                            color = if (uiState.idleProgress >= 1f) Color.Green else MaterialTheme.colors.primary,
                        )
                        
                        if (uiState.idleProgress >= 1f) {
                            Text(
                                text = "준비 완료!",
                                style = MaterialTheme.typography.caption1,
                                color = Color.Green,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
        
        // 세션 통계
        if (uiState.strokeCount > 1) {
            item {
                SessionStatsCompactCard(uiState = uiState)
            }
        }
        
        // 종료 버튼
        item {
            Button(
                onClick = onStop,
                modifier = Modifier.fillMaxWidth(0.95f),
                colors = ButtonDefaults.secondaryButtonColors()
            ) {
                Text("측정 종료")
            }
        }
    }
}

/**
 * 마지막 스트로크 카드
 */
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
                    text = "속도: %.2f m/s".format(stroke.maxSpeed),
                    style = MaterialTheme.typography.caption2
                )
                Text(
                    text = "각도: %.1f°".format(stroke.swingAngle),
                    style = MaterialTheme.typography.caption2
                )
            }
        }
    }
}

/**
 * 세션 통계 카드
 */
@Composable
fun SessionStatsCard(
    uiState: MeasurementUiState,
    onToggleDetailedStats: () -> Unit
) {
    Card(
        onClick = onToggleDetailedStats,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "세션 통계",
                    style = MaterialTheme.typography.caption1,
                    color = MaterialTheme.colors.primary
                )
                Text(
                    text = if (uiState.showDetailedStats) "▼" else "▶",
                    style = MaterialTheme.typography.caption2,
                    color = MaterialTheme.colors.onSurfaceVariant
                )
            }

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

            if (uiState.showDetailedStats && uiState.sessionStrokes.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    text = "상세 기록",
                    style = MaterialTheme.typography.caption2,
                    color = MaterialTheme.colors.primary,
                    fontWeight = FontWeight.Medium
                )

                uiState.sessionStrokes.forEachIndexed { index, stroke ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${index + 1}회",
                                style = MaterialTheme.typography.caption2,
                                color = MaterialTheme.colors.primary
                            )
                            Text(
                                text = "%.2f m/s".format(stroke.maxSpeed),
                                style = MaterialTheme.typography.caption3,
                                color = MaterialTheme.colors.onSurfaceVariant
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "%.1f m".format(stroke.predictedDistance),
                                style = MaterialTheme.typography.caption2,
                                color = Color.Green,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "%.1f°".format(stroke.swingAngle),
                                style = MaterialTheme.typography.caption3,
                                color = MaterialTheme.colors.onSurfaceVariant
                            )
                        }
                    }

                    if (index < uiState.sessionStrokes.size - 1) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
            }
        }
    }
}

/**
 * 세션 통계 간략 카드 (RESULT 화면용)
 */
@Composable
fun SessionStatsCompactCard(uiState: MeasurementUiState) {
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
                Text(
                    text = "평균: %.1f m".format(uiState.averageDistance),
                    style = MaterialTheme.typography.caption2
                )
                Text(
                    text = "총 ${uiState.strokeCount}회",
                    style = MaterialTheme.typography.caption2
                )
            }
        }
    }
}

