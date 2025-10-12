package com.bluemarlin.puttmeter.mobile.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.res.painterResource
import com.bluemarlin.puttmeter.mobile.R
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluemarlin.puttmeter.data.wearable.PhaseData
import com.bluemarlin.puttmeter.data.wearable.StrokeResultData
import com.bluemarlin.puttmeter.data.wearable.SessionStatsData

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PuttMeterMobileApp(
    viewModel: PuttingViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text("PuttMeter Mobile") 
                },
                actions = {
                    // 연결 상태 표시
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 16.dp)
                    ) {
                        Icon(
                            painter = painterResource(
                                id = if (uiState.isWearConnected) 
                                    R.drawable.ic_connection_connected 
                                else 
                                    R.drawable.ic_connection_disconnected
                            ),
                            contentDescription = "Wear Connection",
                            tint = if (uiState.isWearConnected) Color.Green else Color.Red,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = uiState.connectionStatus,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (uiState.isWearConnected) Color.Green else Color.Red
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 현재 상태 카드
            item {
                CurrentStateCard(
                    phase = uiState.currentPhase,
                    isActive = uiState.isActive,
                    isConnected = uiState.isWearConnected,
                    currentMaxSpeed = uiState.currentMaxSpeed,
                    predictedDistance = uiState.predictedDistance
                )
            }
            
            // 마지막 스트로크 결과
            uiState.lastStroke?.let { stroke ->
                item {
                    LastStrokeCard(stroke = stroke)
                }
            }
            
            // 세션 통계
            uiState.sessionStats?.let { stats ->
                item {
                    SessionStatsCard(stats = stats)
                }
            }
            
            // 스트로크 히스토리
            if (uiState.strokeHistory.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "스트로크 히스토리",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = { viewModel.clearHistory() }) {
                            Text("지우기")
                        }
                    }
                }
                
                items(uiState.strokeHistory.reversed()) { stroke ->
                    StrokeHistoryItem(stroke = stroke)
                }
            }
        }
    }
}

@Composable
fun CurrentStateCard(
    phase: PhaseData?,
    isActive: Boolean,
    isConnected: Boolean,
    currentMaxSpeed: Float,
    predictedDistance: Float
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "현재 상태",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (!isConnected) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_connection_disconnected),
                    contentDescription = "Disconnected",
                    modifier = Modifier.size(48.dp),
                    tint = Color.Gray
                )
                Text(
                    text = "Wear OS 앱과 연결되지 않음",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
            } else if (phase == null) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_connection_connected),
                    contentDescription = "Connected",
                    modifier = Modifier.size(48.dp),
                    tint = Color.Green
                )
                Text(
                    text = "연결됨 - 측정 대기 중",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.Green
                )
            } else {
                // 현재 단계 표시
                val phaseColor = when (phase.color) {
                    "Gray" -> Color.Gray
                    "Blue" -> Color.Blue
                    "Yellow" -> Color(0xFFFFA500) // Orange
                    "LightGray" -> Color.LightGray
                    "Red" -> Color.Red
                    "Green" -> Color.Green
                    else -> Color.Gray
                }
                
                Text(
                    text = phase.displayName,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = phaseColor
                )
                
                Text(
                    text = phase.subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.Gray
                )
                
                // 실시간 예측 거리 및 최대 속도 표시
                if (isActive && currentMaxSpeed > 0f) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 예측 거리 (크게 표시)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "실시간 예측 거리",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "%.1f m".format(predictedDistance),
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            
                            HorizontalDivider()
                            
                            // 최대 속도 (작게 표시)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "최대 속도",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "%.2f m/s".format(currentMaxSpeed),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
                
                if (isActive) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun LastStrokeCard(stroke: StrokeResultData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "마지막 퍼팅",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 거리 표시
            Text(
                text = "%.1f m".format(stroke.predictedDistance),
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Green
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 메트릭 그리드
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricItem("최대 속도", "%.2f m/s".format(stroke.metrics.clubSpeed))
                MetricItem("스윙 시간", "%.0f ms".format(stroke.metrics.swingTime.toFloat()))
            }
            
        }
    }
}

@Composable
fun SessionStatsCard(stats: SessionStatsData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "세션 통계",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MetricItem("총 퍼팅", "${stats.totalStrokes}회")
                MetricItem("평균 거리", "%.1f m".format(stats.averageDistance))
                MetricItem("최고 거리", "%.1f m".format(stats.bestDistance))
            }
        }
    }
}

@Composable
fun StrokeHistoryItem(stroke: StrokeResultData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "%.1f m".format(stroke.predictedDistance),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "최대 속도: %.2f m/s".format(stroke.metrics.clubSpeed),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "%.0f ms".format(stroke.metrics.swingTime.toFloat()),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "스윙 시간",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
fun MetricItem(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}
