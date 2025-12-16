package com.bluemarlin.puttmeter.wearable.presentation.calibration

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bluemarlin.puttmeter.wearable.data.sensor.SensorDataSource
import com.bluemarlin.puttmeter.wearable.domain.detection.CalibrationData
import com.bluemarlin.puttmeter.wearable.domain.detection.SimpleStrokeDetector
import com.bluemarlin.puttmeter.wearable.domain.detection.SimplePuttStroke
import com.bluemarlin.puttmeter.wearable.domain.detection.SpeedAlgorithm
import com.bluemarlin.puttmeter.wearable.domain.processing.SignalFilter
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

/**
 * 캘리브레이션 상태
 */
enum class CalibrationState {
    IDLE,           // 대기 중
    PRACTICE,       // 연습 스윙 + 정지 감지 중
    READY,          // 측정 준비 완료
    MEASURING,      // 측정 중
    INPUT_DISTANCE  // 거리 입력 대기
}

/**
 * 캘리브레이션 UI 상태
 */
data class CalibrationUiState(
    val state: CalibrationState = CalibrationState.IDLE,
    val currentMaxSpeed: Float = 0f,
    val calibrationData: CalibrationData = CalibrationData(),
    val lastMeasuredSpeed: Float = 0f,
    val error: String? = null,
    val idleProgress: Float = 0f,
    val isWristUp: Boolean = false,
    val debugAccelMagnitude: Float = 0f,
    val debugGyroMagnitude: Float = 0f
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
    
    private var strokeDetector: SimpleStrokeDetector
    
    // 센서 수집 Job 관리
    private var sensorCollectionJob: Job? = null
    
    // 정지 감지 관련 변수들
    private var lastMotionDetectedTime = 0L
    private val idleThreshold = 2000L
    private var idleStartTime = 0L
    
    // 변화량 기반 임계값
    private var accelDeltaThreshold = 0.5f
    private var gyroDeltaThreshold = 0.3f
    
    // 이전 센서 값 저장용
    private var previousAccelMagnitude = 0f
    private var previousGyroMagnitude = 0f
    private var isFirstSample = true
    
    init {
        // 저장된 캘리브레이션 파라미터 로드
        // 기본값: 0 m/s = 0 m, 1 m/s = 2 m, 2 m/s = 10 m (이차 방정식)
        val slope = sharedPreferences.getFloat("calibration_slope", 3.0f)
        val intercept = sharedPreferences.getFloat("calibration_intercept", -1.0f)
        
        // 저장된 속도 측정 알고리즘 로드
        val algorithmName = sharedPreferences.getString("speed_algorithm", SpeedAlgorithm.SENSOR_FUSION.name)
        val algorithm = try {
            SpeedAlgorithm.valueOf(algorithmName ?: SpeedAlgorithm.SENSOR_FUSION.name)
        } catch (e: Exception) {
            SpeedAlgorithm.SENSOR_FUSION
        }
        
        // 정지 감도 로드
        val idleSensitivity = sharedPreferences.getInt("idle_sensitivity", 3)
        updateIdleSensitivity(idleSensitivity)
        
        // 퍼팅 감도 로드
        val puttingSensitivity = sharedPreferences.getInt("putting_sensitivity", 3)
        val swingStartThreshold = getPuttingThreshold(puttingSensitivity)
        
        // 스트로크 감지기 생성
        strokeDetector = SimpleStrokeDetector(
            slope = slope,
            intercept = intercept,
            algorithm = algorithm,
            swingStartThreshold = swingStartThreshold
        )
        
        // 저장된 캘리브레이션 데이터 로드
        loadCalibrationData()
        
        // 설정이 변경될 때마다 리로드
        observeSettingsChanges()
        
        // 스트로크 감지 관찰
        viewModelScope.launch {
            strokeDetector.detectedStroke.collect { stroke ->
                stroke?.let {
                    onStrokeDetected(it)
                }
            }
        }
    }
    
    /**
     * 정지 감도 업데이트
     */
    private fun updateIdleSensitivity(sensitivity: Int) {
        when (sensitivity) {
            1 -> { accelDeltaThreshold = 1.5f; gyroDeltaThreshold = 1.0f }
            2 -> { accelDeltaThreshold = 1.0f; gyroDeltaThreshold = 0.7f }
            3 -> { accelDeltaThreshold = 0.5f; gyroDeltaThreshold = 0.3f }
            4 -> { accelDeltaThreshold = 0.3f; gyroDeltaThreshold = 0.2f }
            5 -> { accelDeltaThreshold = 0.2f; gyroDeltaThreshold = 0.1f }
        }
    }
    
    /**
     * 퍼팅 감도를 임계값으로 변환
     */
    private fun getPuttingThreshold(sensitivity: Int): Float {
        return when (sensitivity) {
            1 -> 0.1f
            2 -> 0.2f
            3 -> 0.3f
            4 -> 0.5f
            5 -> 0.7f
            else -> 0.3f
        }
    }
    
    /**
     * 설정 변경 관찰
     */
    private fun observeSettingsChanges() {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "calibration_count", "calibration_slope", "calibration_intercept" -> {
                    loadCalibrationData()
                }
                "speed_algorithm" -> {
                    recreateDetector()
                }
                "idle_sensitivity" -> {
                    val sensitivity = sharedPreferences.getInt("idle_sensitivity", 3)
                    updateIdleSensitivity(sensitivity)
                }
                "putting_sensitivity" -> {
                    val sensitivity = sharedPreferences.getInt("putting_sensitivity", 3)
                    val threshold = getPuttingThreshold(sensitivity)
                    strokeDetector.updateSwingStartThreshold(threshold)
                }
            }
        }
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
    }
    
    /**
     * 감지기 재생성
     */
    private fun recreateDetector() {
        val algorithmName = sharedPreferences.getString("speed_algorithm", SpeedAlgorithm.SENSOR_FUSION.name)
        val algorithm = try {
            SpeedAlgorithm.valueOf(algorithmName ?: SpeedAlgorithm.SENSOR_FUSION.name)
        } catch (e: Exception) {
            SpeedAlgorithm.SENSOR_FUSION
        }
        
        // 기본값: 0 m/s = 0 m, 1 m/s = 2 m, 2 m/s = 10 m (이차 방정식)
        val slope = sharedPreferences.getFloat("calibration_slope", 3.0f)
        val intercept = sharedPreferences.getFloat("calibration_intercept", -1.0f)
        val puttingSensitivity = sharedPreferences.getInt("putting_sensitivity", 3)
        val swingStartThreshold = getPuttingThreshold(puttingSensitivity)
        
        strokeDetector = SimpleStrokeDetector(
            slope = slope,
            intercept = intercept,
            algorithm = algorithm,
            swingStartThreshold = swingStartThreshold
        )
    }
    
    /**
     * 센서 데이터 수집 시작
     */
    private fun startSensorCollection() {
        if (sensorCollectionJob?.isActive == true) return
        
        sensorCollectionJob = viewModelScope.launch {
            sensorDataSource.getSensorDataStream()
                .catch { e ->
                    _uiState.value = _uiState.value.copy(
                        error = "센서 오류: ${e.message}"
                    )
                }
                .collect { sensorData ->
                    processSensorData(sensorData)
                }
        }
    }
    
    /**
     * 센서 데이터 수집 중지
     */
    private fun stopSensorCollection() {
        sensorCollectionJob?.cancel()
        sensorCollectionJob = null
    }
    
    /**
     * 센서 데이터 처리
     */
    private fun processSensorData(sensorData: com.bluemarlin.puttmeter.wearable.domain.model.SensorData) {
        val currentTime = sensorData.timestamp
        
        val linearAccel = SignalFilter.removeGravity(sensorData.acceleration)
        val currentAccelMagnitude = linearAccel.magnitude()
        val currentGyroMagnitude = sensorData.gyroscope.magnitude()
        
        if (isFirstSample) {
            previousAccelMagnitude = currentAccelMagnitude
            previousGyroMagnitude = currentGyroMagnitude
            isFirstSample = false
            lastMotionDetectedTime = currentTime
            idleStartTime = currentTime
            return
        }
        
        val accelDelta = kotlin.math.abs(currentAccelMagnitude - previousAccelMagnitude)
        val gyroDelta = kotlin.math.abs(currentGyroMagnitude - previousGyroMagnitude)
        
        previousAccelMagnitude = currentAccelMagnitude
        previousGyroMagnitude = currentGyroMagnitude
        
        _uiState.value = _uiState.value.copy(
            debugAccelMagnitude = currentAccelMagnitude,
            debugGyroMagnitude = currentGyroMagnitude
        )
        
        val isWristUp = sensorData.acceleration.z > 5.0f
        val hasMotion = accelDelta > accelDeltaThreshold || gyroDelta > gyroDeltaThreshold
        
        if (hasMotion) {
            lastMotionDetectedTime = currentTime
            idleStartTime = currentTime
            _uiState.value = _uiState.value.copy(idleProgress = 0f)
        }
        
        if (isWristUp) {
            idleStartTime = currentTime
        }
        
        val idleDuration = if (!isWristUp) currentTime - idleStartTime else 0L
        val idleProgress = if (!isWristUp) {
            (idleDuration.toFloat() / idleThreshold).coerceIn(0f, 1f)
        } else {
            0f
        }
        
        _uiState.value = _uiState.value.copy(
            idleProgress = idleProgress,
            isWristUp = isWristUp
        )
        
        when (_uiState.value.state) {
            CalibrationState.IDLE -> {}
            CalibrationState.PRACTICE -> {
                if (idleDuration >= idleThreshold && !hasMotion && !isWristUp) {
                    transitionToReady()
                }
            }
            CalibrationState.READY -> {}
            CalibrationState.MEASURING -> {
                strokeDetector.processSensorData(sensorData)
            }
            CalibrationState.INPUT_DISTANCE -> {}
        }
    }
    
    /**
     * 측정 시작 (IDLE -> PRACTICE)
     */
    fun startMeasurement() {
        if (_uiState.value.state != CalibrationState.IDLE) return
        
        isFirstSample = true
        
        _uiState.value = _uiState.value.copy(
            state = CalibrationState.PRACTICE,
            error = null,
            idleProgress = 0f,
            currentMaxSpeed = 0f
        )
        
        if (sensorCollectionJob?.isActive != true) {
            startSensorCollection()
        }
    }
    
    /**
     * PRACTICE -> READY 전환
     */
    private fun transitionToReady() {
        _uiState.value = _uiState.value.copy(
            state = CalibrationState.READY
        )
        
        try {
            val haptic = com.bluemarlin.puttmeter.wearable.presentation.util.HapticFeedback(context)
            haptic.impactDetected()
        } catch (e: Exception) {}
        
        viewModelScope.launch {
            kotlinx.coroutines.delay(300)
            transitionToMeasuring()
        }
    }
    
    /**
     * READY -> MEASURING 전환
     */
    private fun transitionToMeasuring() {
        _uiState.value = _uiState.value.copy(
            state = CalibrationState.MEASURING,
            currentMaxSpeed = 0f
        )
        
        strokeDetector.reset()
        strokeDetector.startMeasurement()
    }
    
    /**
     * 스트로크 감지 콜백
     */
    private fun onStrokeDetected(stroke: SimplePuttStroke) {
        if (_uiState.value.state != CalibrationState.MEASURING) return
        
        _uiState.value = _uiState.value.copy(
            state = CalibrationState.INPUT_DISTANCE,
            lastMeasuredSpeed = stroke.maxSpeed,
            currentMaxSpeed = stroke.maxSpeed
        )
        
        try {
            val haptic = com.bluemarlin.puttmeter.wearable.presentation.util.HapticFeedback(context)
            haptic.measurementComplete()
        } catch (e: Exception) {}
        
        strokeDetector.stopMeasurement()
        strokeDetector.clearDetectedStroke()
    }
    
    /**
     * 실제 거리 입력 및 보정 데이터 업데이트
     */
    fun inputActualDistance(distance: Float) {
        val speed = _uiState.value.lastMeasuredSpeed
        if (speed <= 0f) {
            _uiState.value = _uiState.value.copy(error = "측정된 속도가 없습니다")
            return
        }
        
        val maxCount = sharedPreferences.getInt("calibration_count", 5)
        
        val newCalibrationData = _uiState.value.calibrationData.addMeasurement(
            maxSpeed = speed,
            actualDistance = distance,
            maxCount = maxCount
        )
        
        _uiState.value = _uiState.value.copy(
            calibrationData = newCalibrationData,
            state = CalibrationState.IDLE,
            lastMeasuredSpeed = 0f,
            currentMaxSpeed = 0f
        )
        
        saveCalibrationData(newCalibrationData)
        
        // Detector에 새로운 파라미터 적용
        strokeDetector.updateCalibration(newCalibrationData.slope, newCalibrationData.intercept)
    }
    
    /**
     * 측정 취소
     */
    fun cancelMeasurement() {
        _uiState.value = _uiState.value.copy(
            state = CalibrationState.IDLE,
            currentMaxSpeed = 0f,
            lastMeasuredSpeed = 0f
        )
        
        strokeDetector.stopMeasurement()
        stopSensorCollection()
    }
    
    /**
     * 거리 입력 취소
     */
    fun cancelDistanceInput() {
        _uiState.value = _uiState.value.copy(
            state = CalibrationState.IDLE,
            lastMeasuredSpeed = 0f,
            currentMaxSpeed = 0f
        )
    }
    
    /**
     * 캘리브레이션 데이터 초기화
     */
    fun resetCalibration() {
        val emptyData = CalibrationData()
        _uiState.value = _uiState.value.copy(
            calibrationData = emptyData,
            state = CalibrationState.IDLE,
            lastMeasuredSpeed = 0f,
            currentMaxSpeed = 0f
        )
        saveCalibrationData(emptyData)
        
        // Detector 파라미터도 초기화 (기본값: 0 m/s = 0 m, 1 m/s = 2 m, 2 m/s = 10 m)
        strokeDetector.updateCalibration(3.0f, -1.0f)
    }
    
    /**
     * 캘리브레이션 데이터 저장 (회귀분석 결과)
     */
    private fun saveCalibrationData(data: CalibrationData) {
        sharedPreferences.edit().apply {
            putFloat("calibration_slope", data.slope)
            putFloat("calibration_intercept", data.intercept)
            putInt("measurement_count", data.measurements.size)
            putLong("last_updated", data.lastUpdated)
            
            // 하위 호환성을 위해 averageFactor도 저장 (새로운 기본값 2.0 기준)
            putFloat("calibration_factor", data.slope / 2.0f)
            apply()
        }
    }
    
    /**
     * 캘리브레이션 데이터 로드
     */
    private fun loadCalibrationData() {
        // 기본값: 0 m/s = 0 m, 1 m/s = 2 m, 2 m/s = 10 m (이차 방정식)
        val slope = sharedPreferences.getFloat("calibration_slope", 3.0f)
        val intercept = sharedPreferences.getFloat("calibration_intercept", -1.0f)
        val requiredCount = sharedPreferences.getInt("calibration_count", 5)
        val lastUpdated = sharedPreferences.getLong("last_updated", 0L)
        
        val data = CalibrationData(
            measurements = emptyList(),
            slope = slope,
            intercept = intercept,
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
    
    override fun onCleared() {
        super.onCleared()
        stopSensorCollection()
        strokeDetector.reset()
    }
}
