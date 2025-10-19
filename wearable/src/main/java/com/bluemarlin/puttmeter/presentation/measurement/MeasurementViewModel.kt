package com.bluemarlin.puttmeter.wearable.presentation.measurement

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bluemarlin.puttmeter.wearable.data.sensor.SensorDataSource
import com.bluemarlin.puttmeter.wearable.data.wearable.*
import com.bluemarlin.puttmeter.wearable.domain.detection.SimpleStrokeDetector
import com.bluemarlin.puttmeter.wearable.domain.detection.SimplePuttStroke
import com.bluemarlin.puttmeter.wearable.domain.detection.SpeedAlgorithm
import com.bluemarlin.puttmeter.wearable.domain.processing.SignalFilter
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

/**
 * 측정 상태
 */
enum class MeasurementState {
    IDLE,           // 대기 중 (시작 전)
    PRACTICE,       // 연습 스윙 + 정지 감지 중
    READY,          // 측정 준비 완료 (2초 정지 감지됨)
    MEASURING,      // 실제 퍼팅 측정 중
    RESULT          // 결과 표시
}

/**
 * 측정 UI 상태
 */
data class MeasurementUiState(
    val state: MeasurementState = MeasurementState.IDLE,
    val currentMaxSpeed: Float = 0f,
    val predictedDistance: Float = 0f,
    val lastStroke: SimplePuttStroke? = null,
    val sessionStrokes: List<SimplePuttStroke> = emptyList(),
    val averageDistance: Float = 0f,
    val strokeCount: Int = 0,
    val calibrationFactor: Float = 1.0f,
    val error: String? = null,
    val debugAccelMagnitude: Float = 0f,
    val debugGyroMagnitude: Float = 0f,
    val showDetailedStats: Boolean = false,
    val idleProgress: Float = 0f,  // 정지 상태 진행률 (0.0 ~ 1.0)
    val debugCurrentSpeed: Float = 0f,  // 현재 속도 (디버깅)
    val debugIsInSwing: Boolean = false,  // 스윙 중인지 (디버깅)
    val isWristUp: Boolean = false  // Wrist Up 상태 (화면 보는 중)
)

/**
 * 측정 ViewModel
 */
class MeasurementViewModel(
    private val sensorDataSource: SensorDataSource,
    private val context: Context
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(MeasurementUiState())
    val uiState: StateFlow<MeasurementUiState> = _uiState.asStateFlow()
    
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("putt_meter_prefs", Context.MODE_PRIVATE)

    private val wearableDataSender = WearableDataSender(context)
    private var sessionStartTime = 0L
    
    // 센서 수집 Job 관리
    private var sensorCollectionJob: Job? = null

    // 정지 감지 관련 변수들
    private var lastMotionDetectedTime = 0L
    private val idleThreshold = 2000L  // 2초 정지 감지
    private var idleStartTime = 0L

    // 변화량 기반 임계값
    private val accelDeltaThreshold = 0.5f
    private val gyroDeltaThreshold = 0.3f

    // 이전 센서 값 저장용
    private var previousAccelMagnitude = 0f
    private var previousGyroMagnitude = 0f
    private var isFirstSample = true
    
    // 스트로크 감지기
    private var strokeDetector: SimpleStrokeDetector
    
    init {
        // 저장된 캘리브레이션 계수 로드
        val calibrationFactor = sharedPreferences.getFloat("calibration_factor", 1.0f)
        _uiState.value = _uiState.value.copy(calibrationFactor = calibrationFactor)

        // 저장된 속도 측정 알고리즘 로드
        val algorithmName = sharedPreferences.getString("speed_algorithm", SpeedAlgorithm.SENSOR_FUSION.name)
        val algorithm = try {
            SpeedAlgorithm.valueOf(algorithmName ?: SpeedAlgorithm.SENSOR_FUSION.name)
        } catch (e: Exception) {
            SpeedAlgorithm.SENSOR_FUSION
        }

        // 스트로크 감지기 생성
        strokeDetector = SimpleStrokeDetector(
            calibrationFactor = calibrationFactor,
            algorithm = algorithm
        )

        // 설정 변경 감지
        observeSettingsChanges()

        // 주기적 하트비트 전송 (5초마다)
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(5000)
                sendAppStateToMobile()
            }
        }

        // 스트로크 감지 결과 관찰
        viewModelScope.launch {
            strokeDetector.detectedStroke.collect { stroke ->
                stroke?.let {
                    onStrokeDetected(it)
                }
            }
        }
        
        // 현재 속도 관찰 (디버깅용)
        viewModelScope.launch {
            strokeDetector.currentSpeed.collect { speed ->
                _uiState.value = _uiState.value.copy(debugCurrentSpeed = speed)
            }
        }
        
        // 스윙 상태 관찰 (디버깅용)
        viewModelScope.launch {
            strokeDetector.isInSwingState.collect { inSwing ->
                _uiState.value = _uiState.value.copy(debugIsInSwing = inSwing)
            }
        }
    }
    
    /**
     * 설정 변경 감지
     */
    private fun observeSettingsChanges() {
        sharedPreferences.registerOnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "calibration_factor" -> {
                    val newFactor = sharedPreferences.getFloat("calibration_factor", 1.0f)
                    _uiState.value = _uiState.value.copy(calibrationFactor = newFactor)
                    strokeDetector.updateCalibrationFactor(newFactor)
                }
                "speed_algorithm" -> {
                    val algorithmName = sharedPreferences.getString("speed_algorithm", SpeedAlgorithm.SENSOR_FUSION.name)
                    val algorithm = try {
                        SpeedAlgorithm.valueOf(algorithmName ?: SpeedAlgorithm.SENSOR_FUSION.name)
                    } catch (e: Exception) {
                        SpeedAlgorithm.SENSOR_FUSION
                    }
                    recreateDetector(algorithm)
                }
            }
        }
    }
    
    /**
     * Detector 재생성
     */
    private fun recreateDetector(algorithm: SpeedAlgorithm) {
        strokeDetector = SimpleStrokeDetector(
            calibrationFactor = _uiState.value.calibrationFactor,
            algorithm = algorithm
        )
        
        // 스트로크 감지 결과 다시 구독
        viewModelScope.launch {
            strokeDetector.detectedStroke.collect { stroke ->
                stroke?.let {
                    onStrokeDetected(it)
                }
            }
        }
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
     * 센서 데이터 처리 (상태별 분기)
     */
    private fun processSensorData(sensorData: com.bluemarlin.puttmeter.wearable.domain.model.SensorData) {
        val currentTime = sensorData.timestamp

        // 중력 보정된 가속도 사용
        val linearAccel = SignalFilter.removeGravity(sensorData.acceleration)
        val currentAccelMagnitude = linearAccel.magnitude()
        val currentGyroMagnitude = sensorData.gyroscope.magnitude()

        // 첫 샘플 초기화
        if (isFirstSample) {
            previousAccelMagnitude = currentAccelMagnitude
            previousGyroMagnitude = currentGyroMagnitude
            isFirstSample = false
            lastMotionDetectedTime = currentTime
            idleStartTime = currentTime
            return
        }

        // 변화량 계산
        val accelDelta = kotlin.math.abs(currentAccelMagnitude - previousAccelMagnitude)
        val gyroDelta = kotlin.math.abs(currentGyroMagnitude - previousGyroMagnitude)

        previousAccelMagnitude = currentAccelMagnitude
        previousGyroMagnitude = currentGyroMagnitude

        // 디버깅 정보 업데이트
        _uiState.value = _uiState.value.copy(
            debugAccelMagnitude = currentAccelMagnitude,
            debugGyroMagnitude = currentGyroMagnitude
        )

        // Wrist Up 감지 (손목을 들어 시계를 보는 자세)
        // Z축이 양수이고 큰 경우 = 중력이 손목에서 팔꿈치 방향
        val isWristUp = sensorData.acceleration.z > 5.0f
        
        // 움직임 감지
        val hasMotion = accelDelta > accelDeltaThreshold || gyroDelta > gyroDeltaThreshold
        
        if (hasMotion) {
            lastMotionDetectedTime = currentTime
            idleStartTime = currentTime  // 정지 타이머 리셋
            _uiState.value = _uiState.value.copy(idleProgress = 0f)
        }
        
        // Wrist Up 상태일 때는 정지 타이머 리셋 (화면을 보는 중)
        if (isWristUp) {
            idleStartTime = currentTime
        }

        // 정지 시간 및 진행률 계산 (Wrist Up이 아닐 때만)
        val idleDuration = if (!isWristUp) currentTime - idleStartTime else 0L
        val idleProgress = if (!isWristUp) {
            (idleDuration.toFloat() / idleThreshold).coerceIn(0f, 1f)
        } else {
            0f
        }
        
        // UI 상태 업데이트 (Wrist Up 상태 포함)
        _uiState.value = _uiState.value.copy(
            idleProgress = idleProgress,
            isWristUp = isWristUp
        )

        // 상태별 처리
        when (_uiState.value.state) {
            MeasurementState.IDLE -> {
                // IDLE 상태에서는 아무것도 안 함
            }
            MeasurementState.PRACTICE -> {
                // 연습 스윙 중 - 2초 정지 감지 (Wrist Up이 아닐 때만)
                if (idleDuration >= idleThreshold && !hasMotion && !isWristUp) {
                    transitionToReady()
                }
            }
            MeasurementState.READY -> {
                // READY 상태는 자동으로 MEASURING으로 전환됨
            }
            MeasurementState.MEASURING -> {
                // 측정 중 - 스트로크 감지기에 데이터 전달
                strokeDetector.processSensorData(sensorData)
            }
            MeasurementState.RESULT -> {
                // 결과 표시 중 - 2초 정지 감지 시 다음 측정 (Wrist Up이 아닐 때만)
                if (idleDuration >= idleThreshold && !hasMotion && !isWristUp) {
                    transitionToPractice()
                }
            }
        }
    }

    /**
     * 측정 시작 (IDLE -> PRACTICE)
     */
    fun startMeasurement() {
        if (_uiState.value.state != MeasurementState.IDLE) return
        
        sessionStartTime = System.currentTimeMillis()
        isFirstSample = true
        
        _uiState.value = _uiState.value.copy(
            state = MeasurementState.PRACTICE,
            error = null,
            idleProgress = 0f
        )
        
        // 센서 수집 시작
        if (sensorCollectionJob?.isActive != true) {
            startSensorCollection()
        }
        
        viewModelScope.launch {
            sendAppStateToMobile()
        }
    }
    
    /**
     * PRACTICE -> READY 전환
     */
    private fun transitionToReady() {
        _uiState.value = _uiState.value.copy(
            state = MeasurementState.READY
        )
        
        // 진동 신호
        try {
            val haptic = com.bluemarlin.puttmeter.wearable.presentation.util.HapticFeedback(context)
            haptic.impactDetected()  // 강한 진동
        } catch (e: Exception) {
            // 진동 실패 시 무시
        }
        
        // 즉시 MEASURING으로 전환 (300ms 후)
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
            state = MeasurementState.MEASURING,
            currentMaxSpeed = 0f,
            predictedDistance = 0f
        )
        
        // 스트로크 감지기 시작
        strokeDetector.reset()
        strokeDetector.startMeasurement()
        
        viewModelScope.launch {
            sendAppStateToMobile()
        }
    }
    
    /**
     * 스트로크 감지 콜백
     */
    private fun onStrokeDetected(stroke: SimplePuttStroke) {
        if (_uiState.value.state != MeasurementState.MEASURING) return
        
        val currentStrokes = _uiState.value.sessionStrokes + stroke
        val avgDistance = currentStrokes.map { it.predictedDistance }.average().toFloat()

        _uiState.value = _uiState.value.copy(
            state = MeasurementState.RESULT,
            lastStroke = stroke,
            sessionStrokes = currentStrokes,
            averageDistance = avgDistance,
            strokeCount = currentStrokes.size,
            currentMaxSpeed = stroke.maxSpeed,
            predictedDistance = stroke.predictedDistance,
            idleProgress = 0f
        )
        
        // 측정 완료 진동
        try {
            val haptic = com.bluemarlin.puttmeter.wearable.presentation.util.HapticFeedback(context)
            haptic.measurementComplete()
        } catch (e: Exception) {
            // 진동 실패 시 무시
        }
        
        // 스트로크 감지기 중지
        strokeDetector.stopMeasurement()
        
        // 결과를 모바일로 전송
        viewModelScope.launch {
            wearableDataSender.sendSimpleStrokeResult(stroke)
            sendSessionStatsToMobile()
            sendAppStateToMobile()
        }
        
        // 정지 타이머 리셋
        isFirstSample = true
        
        strokeDetector.clearDetectedStroke()
    }
    
    /**
     * RESULT -> PRACTICE 전환 (다음 측정)
     */
    private fun transitionToPractice() {
        _uiState.value = _uiState.value.copy(
            state = MeasurementState.PRACTICE,
            currentMaxSpeed = 0f,
            predictedDistance = 0f,
            idleProgress = 0f
        )
        
        isFirstSample = true
        
        // 진동 신호
        try {
            val haptic = com.bluemarlin.puttmeter.wearable.presentation.util.HapticFeedback(context)
            haptic.tapStart()
        } catch (e: Exception) {
            // 진동 실패 시 무시
        }
    }
    
    /**
     * 측정 중지
     */
    fun stopMeasurement() {
        strokeDetector.stopMeasurement()
        
        _uiState.value = _uiState.value.copy(
            state = MeasurementState.IDLE
        )
        
        viewModelScope.launch {
            sendAppStateToMobile()
        }
    }
    
    /**
     * 세션 초기화
     */
    fun clearSession() {
        _uiState.value = _uiState.value.copy(
            state = MeasurementState.IDLE,
            lastStroke = null,
            sessionStrokes = emptyList(),
            averageDistance = 0f,
            strokeCount = 0,
            currentMaxSpeed = 0f,
            predictedDistance = 0f,
            showDetailedStats = false,
            idleProgress = 0f
        )
        
        strokeDetector.reset()
        isFirstSample = true
    }
    
    /**
     * 세션 통계 상세 표시 토글
     */
    fun toggleDetailedStats() {
        _uiState.value = _uiState.value.copy(
            showDetailedStats = !_uiState.value.showDetailedStats
        )
    }

    /**
     * 오류 메시지 지우기
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    /**
     * 화면 나가기
     */
    fun onScreenExit() {
        if (_uiState.value.state == MeasurementState.MEASURING) {
            strokeDetector.stopMeasurement()
        }
        
        stopSensorCollection()
        
        _uiState.value = _uiState.value.copy(
            state = MeasurementState.IDLE
        )
        
        viewModelScope.launch {
            sendAppStateToMobile()
        }
    }
    
    /**
     * 화면 진입
     */
    fun onScreenEnter() {
        // 센서 수집은 startMeasurement에서 시작
        viewModelScope.launch {
            sendAppStateToMobile()
        }
    }
    
    // ===== Wearable 데이터 전송 함수들 =====
    
    private suspend fun sendAppStateToMobile() {
        val appState = AppStateData(
            isWearAppActive = true,
            isMeasuring = _uiState.value.state == MeasurementState.MEASURING
        )
        wearableDataSender.sendAppState(appState)
    }
    
    private suspend fun sendSessionStatsToMobile() {
        val state = _uiState.value
        val strokes = state.sessionStrokes
        
        val sessionStats = SessionStatsData(
            totalStrokes = strokes.size,
            averageDistance = state.averageDistance,
            bestDistance = strokes.maxOfOrNull { it.predictedDistance } ?: 0f,
            averageStability = 1.0f,
            averageSmoothness = 1.0f,
            sessionStartTime = sessionStartTime
        )
        wearableDataSender.sendSessionStats(sessionStats)
    }
    
    override fun onCleared() {
        super.onCleared()
        stopSensorCollection()
        if (_uiState.value.state == MeasurementState.MEASURING) {
            strokeDetector.stopMeasurement()
        }
        wearableDataSender.cleanup()
    }
}

// WearableDataSender 확장 함수
private suspend fun WearableDataSender.sendSimpleStrokeResult(stroke: SimplePuttStroke) {
    val metricsData = MetricsData(
        peakAcceleration = 0f,
        impactVelocity = stroke.maxSpeed,
        impulse = 0f,
        swingTime = stroke.swingTime,
        addressTime = 0L,
        backswingTime = 0L,
        downswingTime = 0L,
        followThroughTime = 0L,
        tempoRatio = 0f,
        backswingDistance = 0f,
        followThroughDistance = 0f,
        clubSpeed = stroke.maxSpeed,
        impactForce = 0f,
        addressStability = 1.0f,
        finishStability = 1.0f,
        smoothness = 1.0f
    )
    
    val strokeData = StrokeResultData(
        timestamp = stroke.timestamp,
        predictedDistance = stroke.predictedDistance,
        metrics = metricsData,
        isValidStroke = true
    )
    
    sendStrokeResult(strokeData)
}
