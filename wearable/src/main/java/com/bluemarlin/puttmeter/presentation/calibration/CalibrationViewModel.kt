package com.bluemarlin.puttmeter.wearable.presentation.calibration

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bluemarlin.puttmeter.wearable.data.sensor.SensorDataSource
import com.bluemarlin.puttmeter.wearable.data.wearable.*
import com.bluemarlin.puttmeter.wearable.domain.detection.CalibrationData
import com.bluemarlin.puttmeter.wearable.domain.detection.SimpleStrokeDetector
import com.bluemarlin.puttmeter.wearable.domain.detection.SpeedAlgorithm
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * 캘리브레이션 UI 상태
 */
data class CalibrationUiState(
    val isActive: Boolean = false,
    val currentMaxSpeed: Float = 0f,
    val calibrationData: CalibrationData = CalibrationData(),
    val waitingForDistance: Boolean = false,
    val lastMeasuredSpeed: Float = 0f,
    val error: String? = null
) {
    val measurementCount: Int
        get() = calibrationData.measurements.size
    
    val isComplete: Boolean
        get() = calibrationData.isValid
}

/**
 * 캘리브레이션 ViewModel
 */
class CalibrationViewModel(
    private val sensorDataSource: SensorDataSource,
    private val context: Context
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(CalibrationUiState())
    val uiState: StateFlow<CalibrationUiState> = _uiState.asStateFlow()
    
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("putt_meter_prefs", Context.MODE_PRIVATE)
    
    private val wearableDataSender = WearableDataSender(context)
    private lateinit var strokeDetector: SimpleStrokeDetector
    
    init {
        // 저장된 속도 측정 알고리즘 로드
        val algorithmName = sharedPreferences.getString("speed_algorithm", SpeedAlgorithm.SENSOR_FUSION.name)
        val algorithm = try {
            SpeedAlgorithm.valueOf(algorithmName ?: SpeedAlgorithm.SENSOR_FUSION.name)
        } catch (e: Exception) {
            SpeedAlgorithm.SENSOR_FUSION
        }
        
        // 스트로크 감지기 생성
        strokeDetector = SimpleStrokeDetector(algorithm = algorithm)
        
        // 저장된 캘리브레이션 데이터 로드
        loadCalibrationData()
        
        // 설정이 변경될 때마다 리로드
        observeSettingsChanges()
        
        // 센서 데이터 수집 시작 (앱이 실행되는 동안 계속 유지)
        startSensorCollection()
        
        // 현재 최대 속도 관찰 및 모바일로 전송
        viewModelScope.launch {
            strokeDetector.currentMaxSpeed.collect { speed ->
                _uiState.value = _uiState.value.copy(currentMaxSpeed = speed)
                // 실시간 최대 속도를 모바일로 전송
                if (_uiState.value.isActive) {
                    sendCalibrationStateToMobile()
                }
            }
        }
        
        // 스트로크 감지 관찰 (자동 완료는 제거됨)
        viewModelScope.launch {
            strokeDetector.detectedStroke.collect { stroke ->
                stroke?.let {
                    // 측정 완료 - 사용자가 거리를 입력할 때까지 대기
                    _uiState.value = _uiState.value.copy(
                        isActive = false,
                        waitingForDistance = true,
                        lastMeasuredSpeed = it.maxSpeed
                    )
                    strokeDetector.clearDetectedStroke()
                }
            }
        }
    }
    
    /**
     * 센서 데이터 수집 시작
     */
    private fun startSensorCollection() {
        viewModelScope.launch {
            sensorDataSource.getSensorDataStream()
                .catch { e ->
                    _uiState.value = _uiState.value.copy(
                        error = "센서 오류: ${e.message}"
                    )
                }
                .collect { sensorData ->
                    if (_uiState.value.isActive) {
                        strokeDetector.processSensorData(sensorData)
                    }
                }
        }
    }
    
    /**
     * 측정 시작 (즉시 시작)
     */
    fun startMeasurement() {
        if (_uiState.value.isActive) return
        
        // 센서 감지기 리셋 (이전 데이터 초기화)
        strokeDetector.reset()
        
        // UI 상태 초기화
        _uiState.value = _uiState.value.copy(
            waitingForDistance = false,
            currentMaxSpeed = 0f,
            error = null,
            isActive = true
        )
        
        // 측정 시작
        strokeDetector.startMeasurement()
        
        // 측정 시작을 모바일에 알림
        viewModelScope.launch {
            sendCalibrationStateToMobile()
        }
    }
    
    /**
     * 측정 취소
     */
    fun stopMeasurement() {
        _uiState.value = _uiState.value.copy(
            isActive = false,
            currentMaxSpeed = 0f
        )
        // 측정 중지를 모바일에 알림
        viewModelScope.launch {
            sendCalibrationStateToMobile()
        }
    }
    
    /**
     * 다시 측정 (측정 완료하지 않고 바로 재시작)
     */
    fun restartMeasurement() {
        // 현재 측정 중지
        _uiState.value = _uiState.value.copy(
            isActive = false,
            currentMaxSpeed = 0f
        )
        
        // 즉시 다시 시작
        startMeasurement()
    }
    
    /**
     * 스윙 완료 (수동)
     */
    fun completeSwing() {
        val currentSpeed = _uiState.value.currentMaxSpeed
        if (currentSpeed > 0.3f) { // 최소 속도 임계값 (0.3 m/s)
            // 측정 완료 - 사용자가 거리를 입력할 때까지 대기
            _uiState.value = _uiState.value.copy(
                isActive = false,
                waitingForDistance = true,
                lastMeasuredSpeed = currentSpeed
            )
            // 모바일에 상태 전송
            viewModelScope.launch {
                sendCalibrationStateToMobile()
            }
        } else {
            _uiState.value = _uiState.value.copy(
                error = "측정된 속도가 너무 낮습니다 (현재: %.2f m/s)\n더 세게 스윙하세요".format(currentSpeed)
            )
        }
    }
    
    /**
     * 실제 거리 입력 및 캘리브레이션 데이터 업데이트
     */
    fun inputActualDistance(distance: Float) {
        val speed = _uiState.value.lastMeasuredSpeed
        if (speed <= 0f) {
            _uiState.value = _uiState.value.copy(error = "측정된 속도가 없습니다")
            return
        }
        
        // 설정에서 설정된 횟수 가져오기
        val maxCount = sharedPreferences.getInt("calibration_count", 5)
        
        val newCalibrationData = _uiState.value.calibrationData.addMeasurement(
            maxSpeed = speed,
            actualDistance = distance,
            maxCount = maxCount
        )
        
        _uiState.value = _uiState.value.copy(
            calibrationData = newCalibrationData,
            waitingForDistance = false,
            lastMeasuredSpeed = 0f
        )
        
        // 캘리브레이션 데이터 저장
        saveCalibrationData(newCalibrationData)
    }
    
    /**
     * 설정 변경 관찰
     */
    private fun observeSettingsChanges() {
        // SharedPreferences 변경 리스너
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "calibration_count" || key == "calibration_factor" || key == "speed_algorithm") {
                // 알고리즘이 변경되면 감지기를 재생성해야 함
                if (key == "speed_algorithm") {
                    recreateDetector()
                }
                loadCalibrationData()
            }
        }
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
    }
    
    /**
     * 감지기 재생성 (알고리즘 변경 시)
     */
    private fun recreateDetector() {
        val algorithmName = sharedPreferences.getString("speed_algorithm", SpeedAlgorithm.SENSOR_FUSION.name)
        val algorithm = try {
            SpeedAlgorithm.valueOf(algorithmName ?: SpeedAlgorithm.SENSOR_FUSION.name)
        } catch (e: Exception) {
            SpeedAlgorithm.SENSOR_FUSION
        }
        
        strokeDetector = SimpleStrokeDetector(algorithm = algorithm)
    }
    
    /**
     * 캘리브레이션 취소
     */
    fun cancelCalibration() {
        _uiState.value = _uiState.value.copy(
            waitingForDistance = false,
            lastMeasuredSpeed = 0f
        )
    }
    
    /**
     * 캘리브레이션 데이터 초기화
     */
    fun resetCalibration() {
        val emptyData = CalibrationData()
        _uiState.value = _uiState.value.copy(
            calibrationData = emptyData,
            waitingForDistance = false,
            lastMeasuredSpeed = 0f
        )
        saveCalibrationData(emptyData)
    }
    
    /**
     * 캘리브레이션 데이터 저장
     */
    private fun saveCalibrationData(data: CalibrationData) {
        sharedPreferences.edit().apply {
            putFloat("calibration_factor", data.averageFactor)
            putInt("measurement_count", data.measurements.size)
            putLong("last_updated", data.lastUpdated)
            apply()
        }
    }
    
    /**
     * 캘리브레이션 데이터 로드
     */
    private fun loadCalibrationData() {
        val factor = sharedPreferences.getFloat("calibration_factor", 1.0f)
        val requiredCount = sharedPreferences.getInt("calibration_count", 5)
        val lastUpdated = sharedPreferences.getLong("last_updated", 0L)
        
        val data = CalibrationData(
            measurements = emptyList(), // 세션 시작 시 비어있음
            averageFactor = factor,
            requiredCount = requiredCount,
            lastUpdated = lastUpdated
        )
        _uiState.value = _uiState.value.copy(calibrationData = data)
    }
    
    /**
     * 오류 메시지 지우기
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    /**
     * 캘리브레이션 상태를 모바일로 전송
     */
    private suspend fun sendCalibrationStateToMobile() {
        val state = _uiState.value
        val puttingState = PuttingStateData(
            isActive = state.isActive,
            currentPhase = PhaseData(
                type = "Calibration",
                displayName = when {
                    state.waitingForDistance -> "거리 입력 대기"
                    state.isActive -> "보정 측정 중"
                    else -> "보정 대기"
                },
                color = when {
                    state.waitingForDistance -> "Yellow"
                    state.isActive -> "Green"
                    else -> "Gray"
                },
                subtitle = when {
                    state.waitingForDistance -> "실제 거리를 입력하세요"
                    state.isActive -> "스윙하세요"
                    else -> {
                        if (state.calibrationData.requiredCount > 0) {
                            "${state.measurementCount}/${state.calibrationData.requiredCount} 측정 완료"
                        } else {
                            "${state.measurementCount}회 측정 완료"
                        }
                    }
                }
            ),
            sessionCount = state.measurementCount,
            averageDistance = 0f,
            currentMaxSpeed = state.currentMaxSpeed
        )
        wearableDataSender.sendPuttingState(puttingState)
    }
    
    override fun onCleared() {
        super.onCleared()
        strokeDetector.reset()
        wearableDataSender.cleanup()
    }
}

