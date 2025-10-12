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
            SettingsOption.CALIBRATION_FACTOR -> {
                CalibrationFactorScreen(
                    currentFactor = uiState.calibrationFactor,
                    onFactorChanged = { factor ->
                        viewModel.setCalibrationFactor(factor)
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
    CALIBRATION_FACTOR,
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
                            style = MaterialTheme.typography.caption2,
                            color = MaterialTheme.colors.onSurfaceVariant
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        // 보정 계수 변경
        item {
            Chip(
                onClick = { onOptionSelected(SettingsOption.CALIBRATION_FACTOR) },
                label = {
                    Column {
                        Text("보정 계수 변경")
                        Text(
                            text = "현재: %.2f".format(uiState.calibrationFactor),
                            style = MaterialTheme.typography.caption2,
                            color = MaterialTheme.colors.onSurfaceVariant
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
 * 보정 계수 변경 화면
 */
@Composable
fun CalibrationFactorScreen(
    currentFactor: Float,
    onFactorChanged: (Float) -> Unit,
    onBack: () -> Unit
) {
    var factorText by remember { mutableStateOf(currentFactor.toString()) }
    
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
                text = "보정 계수 변경",
                style = MaterialTheme.typography.title3,
                color = MaterialTheme.colors.primary
            )
        }
        
        // 현재 계수 표시
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
                        text = "현재 계수",
                        style = MaterialTheme.typography.caption2,
                        color = MaterialTheme.colors.onSurfaceVariant
                    )
                    Text(
                        text = if (factorText.isEmpty()) "0.00" else factorText,
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
                NumberButton("1", Modifier.weight(1f)) { factorText += "1" }
                NumberButton("2", Modifier.weight(1f)) { factorText += "2" }
                NumberButton("3", Modifier.weight(1f)) { factorText += "3" }
            }
        }
        
        // 숫자 패드 - 두 번째 줄
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                NumberButton("4", Modifier.weight(1f)) { factorText += "4" }
                NumberButton("5", Modifier.weight(1f)) { factorText += "5" }
                NumberButton("6", Modifier.weight(1f)) { factorText += "6" }
            }
        }
        
        // 숫자 패드 - 세 번째 줄
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                NumberButton("7", Modifier.weight(1f)) { factorText += "7" }
                NumberButton("8", Modifier.weight(1f)) { factorText += "8" }
                NumberButton("9", Modifier.weight(1f)) { factorText += "9" }
            }
        }
        
        // 숫자 패드 - 네 번째 줄
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                NumberButton(".", Modifier.weight(1f)) {
                    if (!factorText.contains(".")) {
                        factorText += "."
                    }
                }
                NumberButton("0", Modifier.weight(1f)) { factorText += "0" }
                NumberButton("←", Modifier.weight(1f)) {
                    if (factorText.isNotEmpty()) {
                        factorText = factorText.dropLast(1)
                    }
                }
            }
        }
        
        // 확인 버튼
        item {
            Button(
                onClick = {
                    val factor = factorText.toFloatOrNull()
                    if (factor != null && factor > 0f && factor <= 10f) {
                        onFactorChanged(factor)
                    }
                },
                modifier = Modifier.fillMaxWidth(0.95f),
                enabled = factorText.isNotEmpty() && 
                          factorText.toFloatOrNull()?.let { it > 0f && it <= 10f } == true
            ) {
                Text("저장")
            }
        }
        
        // 취소 버튼
        item {
            Button(
                onClick = onBack,
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

