package com.bluemarlin.puttmeter.presentation.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.*
import com.bluemarlin.puttmeter.domain.model.Vector3
import kotlin.math.abs

@Composable
fun SensorDebugScreen(
    viewModel: SensorDebugViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier,
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 타이틀
            item {
                Text(
                    text = "센서 디버그",
                    style = MaterialTheme.typography.title3,
                    color = MaterialTheme.colors.primary,
                    textAlign = TextAlign.Center
                )
            }

            // 센서 상태
            item {
                SensorStatusCard(
                    isAccelAvailable = uiState.sensorStatus.isAccelerometerAvailable,
                    isGyroAvailable = uiState.sensorStatus.isGyroscopeAvailable,
                    isCollecting = uiState.isCollecting,
                    sampleRate = uiState.averageUpdateRate
                )
            }

            // 가속도계 데이터
            item {
                SensorDataCard(
                    title = "가속도계 (m/s²)",
                    vector = uiState.currentSensorData.acceleration,
                    magnitude = uiState.currentSensorData.accelerationMagnitude
                )
            }

            // 자이로스코프 데이터
            item {
                SensorDataCard(
                    title = "자이로스코프 (rad/s)",
                    vector = uiState.currentSensorData.gyroscope,
                    magnitude = uiState.currentSensorData.gyroscopeMagnitude
                )
            }

            // 시각적 인디케이터
            item {
                MovementIndicator(
                    acceleration = uiState.currentSensorData.acceleration
                )
            }

            // 제어 버튼
            item {
                Spacer(modifier = Modifier.height(8.dp))
                if (!uiState.isCollecting) {
                    Button(
                        onClick = { viewModel.startCollecting() },
                        modifier = Modifier.fillMaxWidth(0.9f),
                        enabled = uiState.sensorStatus.isReady
                    ) {
                        Text("측정 시작")
                    }
                } else {
                    Button(
                        onClick = { viewModel.stopCollecting() },
                        modifier = Modifier.fillMaxWidth(0.9f),
                        colors = ButtonDefaults.secondaryButtonColors()
                    ) {
                        Text("측정 중지")
                    }
                }
            }

            // 샘플 카운터
            item {
                Text(
                    text = "샘플: ${uiState.sampleCount}",
                    style = MaterialTheme.typography.caption2,
                    color = MaterialTheme.colors.onSurfaceVariant
                )
            }

            // 오류 메시지
            uiState.error?.let { errorMessage ->
                item {
                    Chip(
                        onClick = { viewModel.clearError() },
                        label = {
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.caption2,
                                color = Color.Red
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun SensorStatusCard(
    isAccelAvailable: Boolean,
    isGyroAvailable: Boolean,
    isCollecting: Boolean,
    sampleRate: Float
) {
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
                text = "센서 상태",
                style = MaterialTheme.typography.caption1,
                color = MaterialTheme.colors.primary
            )
            StatusRow("가속도계", isAccelAvailable)
            StatusRow("자이로스코프", isGyroAvailable)
            if (isCollecting) {
                Text(
                    text = "업데이트: %.1f Hz".format(sampleRate),
                    style = MaterialTheme.typography.caption2,
                    color = Color.Green
                )
            }
        }
    }
}

@Composable
fun StatusRow(label: String, isAvailable: Boolean) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.caption2
        )
        Text(
            text = if (isAvailable) "✓" else "✗",
            style = MaterialTheme.typography.caption2,
            color = if (isAvailable) Color.Green else Color.Red
        )
    }
}

@Composable
fun SensorDataCard(
    title: String,
    vector: Vector3,
    magnitude: Float
) {
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
                text = title,
                style = MaterialTheme.typography.caption1,
                color = MaterialTheme.colors.primary
            )
            Text(
                text = "X: %+7.3f".format(vector.x),
                style = MaterialTheme.typography.caption2,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "Y: %+7.3f".format(vector.y),
                style = MaterialTheme.typography.caption2,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "Z: %+7.3f".format(vector.z),
                style = MaterialTheme.typography.caption2,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "크기: %.3f".format(magnitude),
                style = MaterialTheme.typography.caption2,
                color = Color.Yellow,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun MovementIndicator(acceleration: Vector3) {
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
                text = "움직임 감지",
                style = MaterialTheme.typography.caption1,
                color = MaterialTheme.colors.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            // 간단한 시각적 표현 (텍스트 기반)
            val movement = abs(acceleration.x) + abs(acceleration.y) + abs(acceleration.z)
            val intensity = (movement / 30f).coerceIn(0f, 1f)
            val barLength = (intensity * 20).toInt()
            
            Text(
                text = "█".repeat(barLength) + "░".repeat(20 - barLength),
                style = MaterialTheme.typography.body2,
                fontFamily = FontFamily.Monospace,
                color = when {
                    intensity > 0.7f -> Color.Red
                    intensity > 0.4f -> Color.Yellow
                    else -> Color.Green
                }
            )
            
            Text(
                text = "%.1f%%".format(intensity * 100),
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.onSurfaceVariant
            )
        }
    }
}

