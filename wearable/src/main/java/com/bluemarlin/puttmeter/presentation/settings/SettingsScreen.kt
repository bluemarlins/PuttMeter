package com.bluemarlin.puttmeter.wearable.presentation.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.*
import com.bluemarlin.puttmeter.wearable.domain.detection.SpeedAlgorithm

/**
 * 설정 메인 화면
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateToSensorCheck: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedOption by remember { mutableStateOf<SettingsOption?>(null) }
    
    BackHandler {
        if (selectedOption != null) {
            selectedOption = null
        } else {
            onNavigateBack()
        }
    }
    
    Scaffold(
        modifier = modifier,
        timeText = { TimeText() }
    ) {
        when (selectedOption) {
            SettingsOption.CALIBRATION_COUNT -> {
                CalibrationCountScreen(
                    currentCount = uiState.calibrationCount,
                    onCountSelected = { count ->
                        viewModel.setCalibrationCount(count)
                        selectedOption = null
                    },
                    onBack = { selectedOption = null }
                )
            }
            SettingsOption.SPEED_ALGORITHM -> {
                SpeedAlgorithmScreen(
                    currentAlgorithm = uiState.speedAlgorithm,
                    onAlgorithmSelected = { algorithm ->
                        viewModel.setSpeedAlgorithm(algorithm)
                        selectedOption = null
                    },
                    onBack = { selectedOption = null }
                )
            }
            SettingsOption.IDLE_SENSITIVITY -> {
                IdleSensitivityScreen(
                    currentSensitivity = uiState.idleSensitivity,
                    onSensitivitySelected = { sensitivity ->
                        viewModel.setIdleSensitivity(sensitivity)
                        selectedOption = null
                    },
                    onBack = { selectedOption = null }
                )
            }
            SettingsOption.SENSOR_CHECK -> {
                // 센서 체크는 별도 화면으로 이동
                LaunchedEffect(Unit) {
                    selectedOption = null
                    onNavigateToSensorCheck()
                }
                Box(modifier = Modifier.fillMaxSize())
            }
            null -> {
                SettingsMenuScreen(
                    uiState = uiState,
                    onOptionSelected = { option -> selectedOption = option },
                    onReset = { viewModel.resetSettings() }
                )
            }
        }
    }
}

/**
 * 설정 옵션
 */
enum class SettingsOption {
    CALIBRATION_COUNT,
    SPEED_ALGORITHM,
    IDLE_SENSITIVITY,
    SENSOR_CHECK
}

/**
 * 설정 메인 메뉴
 */
@Composable
fun SettingsMenuScreen(
    uiState: SettingsUiState,
    onOptionSelected: (SettingsOption) -> Unit,
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
                text = "설정",
                style = MaterialTheme.typography.title1,
                color = MaterialTheme.colors.primary
            )
        }
        
        // 거리 보정 횟수
        item {
            Chip(
                onClick = { onOptionSelected(SettingsOption.CALIBRATION_COUNT) },
                label = {
                    Column {
                        Text("거리 보정 횟수")
                        Text(
                            text = "현재: ${uiState.calibrationCount}회",
                            style = MaterialTheme.typography.caption2
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        // 속도 측정 알고리즘
        item {
            Chip(
                onClick = { onOptionSelected(SettingsOption.SPEED_ALGORITHM) },
                label = {
                    Column {
                        Text("속도 측정 방식")
                        Text(
                            text = when (uiState.speedAlgorithm) {
                                SpeedAlgorithm.ACCELEROMETER_ONLY -> "가속도계만"
                                SpeedAlgorithm.GYROSCOPE_ONLY -> "자이로스코프만"
                                SpeedAlgorithm.SENSOR_FUSION -> "센서 융합"
                                SpeedAlgorithm.PEAK_ACCELERATION -> "피크 가속도"
                            },
                            style = MaterialTheme.typography.caption2
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        // 정지 감도
        item {
            Chip(
                onClick = { onOptionSelected(SettingsOption.IDLE_SENSITIVITY) },
                label = {
                    Column {
                        Text("정지 감도")
                        Text(
                            text = "현재: ${uiState.idleSensitivity}단계",
                            style = MaterialTheme.typography.caption2
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        // 센서 체크
        item {
            Chip(
                onClick = { onOptionSelected(SettingsOption.SENSOR_CHECK) },
                label = {
                    Text("센서 체크")
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        // 설정 초기화
        item {
            Button(
                onClick = onReset,
                modifier = Modifier.fillMaxWidth(0.9f),
                colors = ButtonDefaults.secondaryButtonColors()
            ) {
                Text("설정 초기화")
            }
        }
    }
}

/**
 * 거리 보정 횟수 선택 화면
 */
@Composable
fun CalibrationCountScreen(
    currentCount: Int,
    onCountSelected: (Int) -> Unit,
    onBack: () -> Unit
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
                text = "보정 횟수 선택",
                style = MaterialTheme.typography.title2,
                color = MaterialTheme.colors.primary
            )
        }
        
        // 현재 설정
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
                        text = "현재 설정",
                        style = MaterialTheme.typography.caption1,
                        color = MaterialTheme.colors.onSurfaceVariant
                    )
                    Text(
                        text = "${currentCount}회",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Green
                    )
                }
            }
        }
        
        // 횟수 선택 옵션 (0~10)
        items(11) { index ->
            Chip(
                onClick = { onCountSelected(index) },
                label = {
                    Text(
                        text = if (index == 0) "무제한" else "${index}회",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        fontWeight = if (index == currentCount) FontWeight.Bold else FontWeight.Normal
                    )
                },
                modifier = Modifier.fillMaxWidth(0.9f),
                colors = if (index == currentCount) {
                    ChipDefaults.primaryChipColors()
                } else {
                    ChipDefaults.secondaryChipColors()
                }
            )
        }
        
        // 뒤로 가기
        item {
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(0.9f),
                colors = ButtonDefaults.secondaryButtonColors()
            ) {
                Text("뒤로")
            }
        }
    }
}

/**
 * 정지 감도 선택 화면
 */
@Composable
fun IdleSensitivityScreen(
    currentSensitivity: Int,
    onSensitivitySelected: (Int) -> Unit,
    onBack: () -> Unit
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
                text = "정지 감도",
                style = MaterialTheme.typography.title2,
                color = MaterialTheme.colors.primary
            )
        }
        
        // 현재 설정
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
                        text = "현재 설정",
                        style = MaterialTheme.typography.caption1,
                        color = MaterialTheme.colors.onSurfaceVariant
                    )
                    Text(
                        text = "${currentSensitivity}단계",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Green
                    )
                    Text(
                        text = when (currentSensitivity) {
                            1 -> "매우 둔감 (큰 움직임만)"
                            2 -> "둔감"
                            3 -> "보통 (권장)"
                            4 -> "민감"
                            5 -> "매우 민감 (작은 움직임도)"
                            else -> "보통"
                        },
                        style = MaterialTheme.typography.caption2,
                        color = MaterialTheme.colors.onSurfaceVariant
                    )
                }
            }
        }
        
        // 감도 선택 옵션 (1~5)
        items(5) { index ->
            val level = index + 1
            Chip(
                onClick = { onSensitivitySelected(level) },
                label = {
                    Column {
                        Text(
                            text = "${level}단계",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            fontWeight = if (level == currentSensitivity) FontWeight.Bold else FontWeight.Normal
                        )
                        Text(
                            text = when (level) {
                                1 -> "매우 둔감"
                                2 -> "둔감"
                                3 -> "보통 (권장)"
                                4 -> "민감"
                                5 -> "매우 민감"
                                else -> ""
                            },
                            style = MaterialTheme.typography.caption3,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colors.onSurfaceVariant
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(0.9f),
                colors = if (level == currentSensitivity) {
                    ChipDefaults.primaryChipColors()
                } else {
                    ChipDefaults.secondaryChipColors()
                }
            )
        }
        
        // 뒤로 가기
        item {
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(0.9f),
                colors = ButtonDefaults.secondaryButtonColors()
            ) {
                Text("뒤로")
            }
        }
    }
}

/**
 * 속도 측정 알고리즘 선택 화면
 */
@Composable
fun SpeedAlgorithmScreen(
    currentAlgorithm: SpeedAlgorithm,
    onAlgorithmSelected: (SpeedAlgorithm) -> Unit,
    onBack: () -> Unit
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
                text = "속도 측정 방식",
                style = MaterialTheme.typography.title2,
                color = MaterialTheme.colors.primary
            )
        }
        
        // 현재 설정
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
                        text = "현재 설정",
                        style = MaterialTheme.typography.caption1,
                        color = MaterialTheme.colors.onSurfaceVariant
                    )
                    Text(
                        text = when (currentAlgorithm) {
                            SpeedAlgorithm.ACCELEROMETER_ONLY -> "가속도계만"
                            SpeedAlgorithm.GYROSCOPE_ONLY -> "자이로스코프만"
                            SpeedAlgorithm.SENSOR_FUSION -> "센서 융합"
                            SpeedAlgorithm.PEAK_ACCELERATION -> "피크 가속도"
                        },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Green
                    )
                }
            }
        }
        
        // 알고리즘 선택 옵션들
        item {
            Chip(
                onClick = { onAlgorithmSelected(SpeedAlgorithm.SENSOR_FUSION) },
                label = {
                    Column {
                        Text("센서 융합 (추천)")
                        Text(
                            text = "가속도 + 자이로",
                            style = MaterialTheme.typography.caption2,
                            color = MaterialTheme.colors.onSurfaceVariant
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(0.95f),
                colors = if (currentAlgorithm == SpeedAlgorithm.SENSOR_FUSION) {
                    ChipDefaults.primaryChipColors()
                } else {
                    ChipDefaults.secondaryChipColors()
                }
            )
        }
        
        item {
            Chip(
                onClick = { onAlgorithmSelected(SpeedAlgorithm.GYROSCOPE_ONLY) },
                label = {
                    Column {
                        Text("자이로스코프만")
                        Text(
                            text = "회전 속도 기반",
                            style = MaterialTheme.typography.caption2,
                            color = MaterialTheme.colors.onSurfaceVariant
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(0.95f),
                colors = if (currentAlgorithm == SpeedAlgorithm.GYROSCOPE_ONLY) {
                    ChipDefaults.primaryChipColors()
                } else {
                    ChipDefaults.secondaryChipColors()
                }
            )
        }
        
        item {
            Chip(
                onClick = { onAlgorithmSelected(SpeedAlgorithm.ACCELEROMETER_ONLY) },
                label = {
                    Column {
                        Text("가속도계만")
                        Text(
                            text = "가속도 적분",
                            style = MaterialTheme.typography.caption2,
                            color = MaterialTheme.colors.onSurfaceVariant
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(0.95f),
                colors = if (currentAlgorithm == SpeedAlgorithm.ACCELEROMETER_ONLY) {
                    ChipDefaults.primaryChipColors()
                } else {
                    ChipDefaults.secondaryChipColors()
                }
            )
        }
        
        item {
            Chip(
                onClick = { onAlgorithmSelected(SpeedAlgorithm.PEAK_ACCELERATION) },
                label = {
                    Column {
                        Text("피크 가속도")
                        Text(
                            text = "최대값 기반",
                            style = MaterialTheme.typography.caption2,
                            color = MaterialTheme.colors.onSurfaceVariant
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(0.95f),
                colors = if (currentAlgorithm == SpeedAlgorithm.PEAK_ACCELERATION) {
                    ChipDefaults.primaryChipColors()
                } else {
                    ChipDefaults.secondaryChipColors()
                }
            )
        }
        
        // 뒤로 가기
        item {
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(0.9f),
                colors = ButtonDefaults.secondaryButtonColors()
            ) {
                Text("뒤로")
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

