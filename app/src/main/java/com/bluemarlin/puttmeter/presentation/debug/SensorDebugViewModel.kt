package com.bluemarlin.puttmeter.presentation.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bluemarlin.puttmeter.data.sensor.SensorDataSource
import com.bluemarlin.puttmeter.domain.model.SensorData
import com.bluemarlin.puttmeter.domain.model.SensorStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * 센서 디버그 화면 UI 상태
 */
data class SensorDebugUiState(
    val sensorStatus: SensorStatus = SensorStatus(),
    val currentSensorData: SensorData = SensorData.EMPTY,
    val isCollecting: Boolean = false,
    val error: String? = null,
    val sampleCount: Int = 0,
    val averageUpdateRate: Float = 0f  // Hz
)

/**
 * 센서 디버그 ViewModel
 */
class SensorDebugViewModel(
    private val sensorDataSource: SensorDataSource
) : ViewModel() {

    private val _uiState = MutableStateFlow(SensorDebugUiState())
    val uiState: StateFlow<SensorDebugUiState> = _uiState.asStateFlow()

    private var lastUpdateTime = 0L
    private var updateCount = 0

    init {
        // 초기 센서 상태 체크
        checkSensorStatus()
    }

    /**
     * 센서 상태 확인
     */
    private fun checkSensorStatus() {
        val status = sensorDataSource.getSensorStatus()
        _uiState.value = _uiState.value.copy(
            sensorStatus = status,
            error = when {
                !status.isAccelerometerAvailable -> "가속도계를 사용할 수 없습니다"
                !status.isGyroscopeAvailable -> "자이로스코프를 사용할 수 없습니다"
                else -> null
            }
        )
    }

    /**
     * 센서 데이터 수집 시작
     */
    fun startCollecting() {
        if (_uiState.value.isCollecting) return

        _uiState.value = _uiState.value.copy(
            isCollecting = true,
            sampleCount = 0,
            averageUpdateRate = 0f
        )

        lastUpdateTime = System.currentTimeMillis()
        updateCount = 0

        viewModelScope.launch {
            sensorDataSource.getSensorDataStream()
                .catch { e ->
                    _uiState.value = _uiState.value.copy(
                        isCollecting = false,
                        error = "센서 오류: ${e.message}"
                    )
                }
                .collect { sensorData ->
                    updateCount++
                    val now = System.currentTimeMillis()
                    val elapsedSeconds = (now - lastUpdateTime) / 1000f

                    // 1초마다 평균 업데이트 속도 계산
                    val avgRate = if (elapsedSeconds >= 1f) {
                        val rate = updateCount / elapsedSeconds
                        lastUpdateTime = now
                        updateCount = 0
                        rate
                    } else {
                        _uiState.value.averageUpdateRate
                    }

                    _uiState.value = _uiState.value.copy(
                        currentSensorData = sensorData,
                        sampleCount = _uiState.value.sampleCount + 1,
                        averageUpdateRate = avgRate
                    )
                }
        }
    }

    /**
     * 센서 데이터 수집 중지
     */
    fun stopCollecting() {
        _uiState.value = _uiState.value.copy(isCollecting = false)
    }

    /**
     * 오류 메시지 지우기
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

