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
 * 단순화된 측정 UI 상태
 */
data class MeasurementUiState(
    val isActive: Boolean = false,
    val countdownSeconds: Int = 0,  // 카운트다운 (3, 2, 1, 0)
    val currentMaxSpeed: Float = 0f,
    val predictedDistance: Float = 0f,  // 실시간 예측 거리
    val lastStroke: SimplePuttStroke? = null,
    val sessionStrokes: List<SimplePuttStroke> = emptyList(),
    val averageDistance: Float = 0f,
    val strokeCount: Int = 0,
    val calibrationFactor: Float = 1.0f,
    val error: String? = null,
    // 자동 시작 관련 상태
    val isMonitoringForAutoStart: Boolean = false,  // 자동 시작을 위한 센서 모니터링 중
    val autoStartCountdown: Int = 0,  // 자동 시작까지 남은 시간 (초)
    val isAutoStarted: Boolean = false,  // 자동으로 시작된 측정인지
    val debugAccelMagnitude: Float = 0f,  // 디버깅용 가속도 크기
    val debugGyroMagnitude: Float = 0f,  // 디버깅용 자이로 크기
    val targetSwingCount: Int = 3,  // 목표 스윙 횟수 (2-3회)
    val currentSwingCount: Int = 0,  // 현재 스윙 횟수
    val isSwingCompleted: Boolean = false,  // 목표 스윙 횟수 달성 여부
    val showDetailedStats: Boolean = false,  // 세션 통계 상세 표시
    val averageSwingTime: Long = 0L  // 평균 스윙 시간 (ms)
) {
    val isCountingDown: Boolean
        get() = countdownSeconds > 0

    val isAutoStartCountingDown: Boolean
        get() = autoStartCountdown > 0
}

/**
 * 단순화된 측정 ViewModel
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

    private lateinit var strokeDetector: SimpleStrokeDetector
    
    // 센서 수집 Job 관리
    private var sensorCollectionJob: Job? = null

    // 자동 시작 관련 변수들
    private var autoStartMonitoringStartTime = 0L
    private var lastMotionDetectedTime = 0L
    private val idleThreshold = 2000L  // 2초 정지 감지

    // 변화량 기반 임계값 (절대값 대신 변화량 사용)
    private val accelDeltaThreshold = 0.5f  // 가속도 변화량 임계값 (m/s²)
    private val gyroDeltaThreshold = 0.3f   // 자이로 변화량 임계값 (rad/s)

    // 측정 완료 후 자동 종료 관련
    private var lastMotionAfterCompletion = 0L
    private val completionIdleThreshold = 2000L  // 측정 완료 후 2초 정지 시 종료

    // wrist up 감지 관련
    private var wristUpStartTime = 0L
    private val wristUpThreshold = 2000L  // wrist up 상태 2초 지속 시 새로운 측정 시작 방지
    private val wristUpAccelThreshold = 8.0f  // wrist up으로 간주하는 가속도 범위 (더 예민)
    private val wristUpGyroThreshold = 0.5f   // wrist up으로 간주하는 자이로 범위

    // 이전 센서 값 저장용
    private var previousAccelMagnitude = 0f
    private var previousGyroMagnitude = 0f
    private var isFirstSample = true
    
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

        // 단순화된 스트로크 감지기 생성
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

        // 현재 최대 속도 관찰 및 모바일로 전송 (측정 중일 때만 + 속도 범위 필터링)
        viewModelScope.launch {
            strokeDetector.currentMaxSpeed.collect { speed ->
                // 측정 중일 때만 속도 업데이트 (wrist up 시 업데이트 방지)
                if (_uiState.value.isActive) {
                    // 퍼팅 속도 범위 필터링: 1.0~2.0 m/s (유효한 퍼팅 속도 범위)
                    val isValidPuttingSpeed = speed in 1.0f..2.0f

                    if (isValidPuttingSpeed) {
                        // 새로운 예측 거리 계산: 1m/s=2m, 2m/s=20m 기반 선형 관계
                        val predictedDist = predictDistanceFromSpeed(speed)
                        _uiState.value = _uiState.value.copy(
                            currentMaxSpeed = speed,
                            predictedDistance = predictedDist.coerceIn(0f, 20f)
                        )
                        // 실시간 최대 속도를 모바일로 전송
                        sendPuttingStateToMobile()
                    }
                    // 비정상 속도는 무시 (wrist up 등)
                }
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
                    // detector의 보정 계수도 업데이트
                    strokeDetector.updateCalibrationFactor(newFactor)
                }
                "speed_algorithm" -> {
                    // 알고리즘 변경 시 detector 재생성
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
     * Detector 재생성 (알고리즘 변경 시)
     */
    private fun recreateDetector(algorithm: SpeedAlgorithm) {
        val wasActive = _uiState.value.isActive
        if (wasActive) {
            strokeDetector.stopMeasurement()
        }
        
        strokeDetector = SimpleStrokeDetector(
            calibrationFactor = _uiState.value.calibrationFactor,
            algorithm = algorithm
        )
        
        // 스트로크 감지 결과 다시 구독
        viewModelScope.launch {
            strokeDetector.currentMaxSpeed.collect { speed ->
                val predictedDist = predictDistanceFromSpeed(speed)
                _uiState.value = _uiState.value.copy(
                    currentMaxSpeed = speed,
                    predictedDistance = predictedDist.coerceIn(0f, 20f)
                )
                if (_uiState.value.isActive) {
                    sendPuttingStateToMobile()
                }
            }
        }
        
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
        // 이미 실행 중이면 중복 실행 방지
        if (sensorCollectionJob?.isActive == true) return
        
        sensorCollectionJob = viewModelScope.launch {
            sensorDataSource.getSensorDataStream()
                .catch { e ->
                    _uiState.value = _uiState.value.copy(
                        error = "센서 오류: ${e.message}"
                    )
                }
                .collect { sensorData ->
                    // 자동 시작 모니터링용 데이터 처리
                    if (_uiState.value.isMonitoringForAutoStart) {
                        processSensorDataForAutoStart(sensorData)
                    }
                    // 측정 중인 경우 스트로크 감지기에도 전달
                    if (_uiState.value.isActive) {
                        strokeDetector.processSensorData(sensorData)
                    }
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
     * 자동 시작을 위한 센서 모니터링 시작
     */
    private fun startAutoStartMonitoring() {
        autoStartMonitoringStartTime = System.currentTimeMillis()
        lastMotionDetectedTime = autoStartMonitoringStartTime
        isFirstSample = true  // 첫 샘플 초기화
        previousAccelMagnitude = 0f
        previousGyroMagnitude = 0f
        // wrist up 관련 변수 초기화
        wristUpStartTime = 0L
        // 디버그 정보 초기화
        _uiState.value = _uiState.value.copy(
            isMonitoringForAutoStart = true,
            debugAccelMagnitude = 0f,
            debugGyroMagnitude = 0f
        )
    }

    /**
     * 자동 시작을 위한 센서 데이터 처리 (변화량 기반 정지 감지)
     */
    private fun processSensorDataForAutoStart(sensorData: com.bluemarlin.puttmeter.wearable.domain.model.SensorData) {
        val currentTime = sensorData.timestamp

        // 중력 보정된 가속도 사용
        val linearAccel = SignalFilter.removeGravity(sensorData.acceleration)
        val currentAccelMagnitude = linearAccel.magnitude()
        val currentGyroMagnitude = sensorData.gyroscope.magnitude()

        // 첫 샘플인 경우 초기화만 하고 리턴
        if (isFirstSample) {
            previousAccelMagnitude = currentAccelMagnitude
            previousGyroMagnitude = currentGyroMagnitude
            isFirstSample = false
            lastMotionDetectedTime = currentTime

            // 디버깅 정보 업데이트 (첫 샘플)
            _uiState.value = _uiState.value.copy(
                debugAccelMagnitude = 0f,  // 변화량 0으로 시작
                debugGyroMagnitude = 0f
            )
            return
        }

        // 변화량 계산 (현재 값 - 이전 값)
        val accelDelta = kotlin.math.abs(currentAccelMagnitude - previousAccelMagnitude)
        val gyroDelta = kotlin.math.abs(currentGyroMagnitude - previousGyroMagnitude)

        // 이전 값 업데이트
        previousAccelMagnitude = currentAccelMagnitude
        previousGyroMagnitude = currentGyroMagnitude

        // 디버깅 정보 업데이트
        if (_uiState.value.isActive) {
            // 측정 중: 실시간 속도 표시 (디버그 정보에 속도 표시)
            _uiState.value = _uiState.value.copy(
                debugAccelMagnitude = _uiState.value.currentMaxSpeed,  // 속도로 표시
                debugGyroMagnitude = gyroDelta
            )
        } else {
            // 측정 대기 중 (첫 측정, 초기화 후, 측정 완료 후 모두 포함):
            // 현재 센서 절대값 표시 (wrist up 감지 확인용)
            _uiState.value = _uiState.value.copy(
                debugAccelMagnitude = currentAccelMagnitude,
                debugGyroMagnitude = currentGyroMagnitude
            )
        }

        // 움직임이 감지되면 타이머 리셋 (변화량 기반)
        // 변화량이 임계값보다 크면 움직임으로 판단
        if (accelDelta > accelDeltaThreshold || gyroDelta > gyroDeltaThreshold) {
            lastMotionDetectedTime = currentTime

            // 자동 시작 카운트다운 중이었으면 취소
            if (_uiState.value.isAutoStartCountingDown) {
                _uiState.value = _uiState.value.copy(autoStartCountdown = 0)
            }
            return
        }

        // 정지 상태 시간 계산
        val idleDuration = currentTime - lastMotionDetectedTime

        // wrist up 상태 감지 (측정 중이 아닐 때 항상 체크)
        // 첫 번째 측정, 초기화 후, 측정 완료 후 모두 포함
        if (!_uiState.value.isActive) {
            // wrist up 감지: 가속도와 자이로가 각각의 임계값 이하로 지속적으로 머무는 경우
            val isWristUpAccel = kotlin.math.abs(currentAccelMagnitude) <= wristUpAccelThreshold
            val isWristUpGyro = kotlin.math.abs(currentGyroMagnitude) <= wristUpGyroThreshold

            if (isWristUpAccel && isWristUpGyro) {
                // wrist up 시작 시간 기록
                if (wristUpStartTime == 0L) {
                    wristUpStartTime = currentTime
                }

                // wrist up 상태가 2초 이상 지속되면 새로운 측정 시작 방지
                val wristUpDuration = currentTime - wristUpStartTime
                if (wristUpDuration >= wristUpThreshold) {
                    // wrist up 상태로 인식됨 - 새로운 측정 시작을 위한 카운터 리셋
                    lastMotionDetectedTime = currentTime
                    return
                }
            } else {
                // wrist up 상태가 아님 - 카운터 리셋
                wristUpStartTime = 0L
            }
        }

        // 2초 이상 정지 상태이면 즉시 측정 시작 (wrist up이 아닌 경우에만)
        if (idleDuration >= idleThreshold && !_uiState.value.isAutoStartCountingDown && !_uiState.value.isActive) {
            startAutoStartMeasurement()
        }
    }

    /**
     * 자동 시작 (카운트다운 없이 즉시 시작)
     */
    private fun startAutoStartMeasurement() {
        // 카운트다운 없이 바로 측정 시작
        _uiState.value = _uiState.value.copy(
            autoStartCountdown = 0,
            isAutoStarted = true
        )

        // 진동 알림
        try {
            val haptic = com.bluemarlin.puttmeter.wearable.presentation.util.HapticFeedback(context)
            haptic.measurementComplete()  // 시작 알림
        } catch (e: Exception) {
            // 진동 실패 시 무시
        }

        // 자동 측정 시작
        autoStartMeasurement()
    }

    /**
     * 자동 측정 시작
     */
    private fun autoStartMeasurement() {
        if (_uiState.value.isActive) return

        sessionStartTime = System.currentTimeMillis()

        // 센서 감지기 리셋 (이전 데이터 초기화)
        strokeDetector.reset()

        // UI 상태 초기화
        _uiState.value = _uiState.value.copy(
            currentMaxSpeed = 0f,
            predictedDistance = 0f,
            error = null,
            currentSwingCount = 0,
            isSwingCompleted = false
        )

        // 측정 시작
        _uiState.value = _uiState.value.copy(isActive = true)
        strokeDetector.startMeasurement()

        // 측정 시작을 모바일에 알림
        viewModelScope.launch {
            sendAppStateToMobile()
            sendPuttingStateToMobile()
        }
    }
    
    /**
     * 측정 시작 (설정된 시간만큼 카운트다운 후) - 수동 시작
     */
    fun startMeasurement() {
        if (_uiState.value.isActive || _uiState.value.isCountingDown) return

        // 수동 시작 시 자동 모니터링 중지
        _uiState.value = _uiState.value.copy(isMonitoringForAutoStart = false)

        sessionStartTime = System.currentTimeMillis()

        // 센서 감지기 리셋 (이전 데이터 초기화)
        strokeDetector.reset()

        // UI 상태 초기화
        _uiState.value = _uiState.value.copy(
            currentMaxSpeed = 0f,
            predictedDistance = 0f,
            error = null,
            currentSwingCount = 0,
            isSwingCompleted = false,
            isAutoStarted = false  // 수동 시작 표시
        )

        // 설정된 카운트다운 시간 읽기
        val countdownDuration = sharedPreferences.getInt("countdown_duration", 3)

        // 카운트다운 시작 (0이면 즉시 시작)
        viewModelScope.launch {
            if (countdownDuration > 0) {
                for (i in countdownDuration downTo 1) {
                    _uiState.value = _uiState.value.copy(
                        countdownSeconds = i
                    )
                    kotlinx.coroutines.delay(1000)
                }
            }

            // 카운트다운 완료 후 측정 시작
            _uiState.value = _uiState.value.copy(
                isActive = true,
                countdownSeconds = 0
            )

            // 이 시점에 측정 시작
            strokeDetector.startMeasurement()

            // 측정 시작을 모바일에 알림
            sendAppStateToMobile()
            sendPuttingStateToMobile()
        }
    }
    
    /**
     * 측정 중지
     */
    fun stopMeasurement() {
        strokeDetector.stopMeasurement()
        _uiState.value = _uiState.value.copy(
            isActive = false,
            countdownSeconds = 0
        )
        // 측정 중지를 모바일에 알림
        viewModelScope.launch {
            sendPuttingStateToMobile()
        }
    }
    
    /**
     * 다시 측정 (측정 완료하지 않고 바로 재시작)
     */
    fun restartMeasurement() {
        // 현재 측정 중지
        _uiState.value = _uiState.value.copy(
            isActive = false,
            countdownSeconds = 0
        )
        
        // 즉시 다시 시작 (세션은 유지)
        startMeasurement()
    }
    
    /**
     * 스트로크 감지 콜백
     */
    private fun onStrokeDetected(stroke: SimplePuttStroke) {
        val currentStrokes = _uiState.value.sessionStrokes + stroke
        val avgDistance = currentStrokes.map { it.predictedDistance }.average().toFloat()
        val newSwingCount = _uiState.value.currentSwingCount + 1
        val isSwingCompleted = newSwingCount >= _uiState.value.targetSwingCount

        // 평균 스윙 시간 계산 (인접 스윙 간 시간차의 평균)
        val avgSwingTime = calculateAverageSwingTime(currentStrokes)

        _uiState.value = _uiState.value.copy(
            lastStroke = stroke,
            sessionStrokes = currentStrokes,
            averageDistance = avgDistance,
            strokeCount = currentStrokes.size,
            currentSwingCount = newSwingCount,
            isSwingCompleted = isSwingCompleted,
            averageSwingTime = avgSwingTime
        )

        // 스트로크 결과를 모바일로 전송
        viewModelScope.launch {
            wearableDataSender.sendSimpleStrokeResult(stroke)
            sendSessionStatsToMobile()
        }

        // 자동 시작 모드이고 목표 스윙 횟수에 도달했으면 측정 완료
        if (_uiState.value.isAutoStarted && isSwingCompleted) {
            viewModelScope.launch {
                kotlinx.coroutines.delay(1000) // 1초 대기 후 자동 완료
                autoCompleteMeasurement()
            }
        }

        // 스트로크 처리 완료
        strokeDetector.clearDetectedStroke()
    }

    /**
     * 자동 측정 완료
     */
    private fun autoCompleteMeasurement() {
        if (!_uiState.value.isActive) return

        strokeDetector.stopMeasurement()
        strokeDetector.reset()  // 완전 초기화 (wrist up 시 속도 업데이트 방지)

        _uiState.value = _uiState.value.copy(
            isActive = false,
            // 측정 완료 시 속도 정보 리셋 (wrist up 시 표시 방지)
            currentMaxSpeed = 0f,
            predictedDistance = 0f,
            debugAccelMagnitude = 0f,
            debugGyroMagnitude = 0f
            // isMonitoringForAutoStart는 계속 true로 유지해서 다음 측정 준비
        )

        // 측정 완료 후 움직임 감지 타이머 초기화
        lastMotionAfterCompletion = System.currentTimeMillis()

        // 측정 중지를 모바일에 알림
        viewModelScope.launch {
            sendPuttingStateToMobile()
        }
    }
    
    /**
     * 세션 초기화
     */
    fun clearSession() {
        _uiState.value = _uiState.value.copy(
            lastStroke = null,
            sessionStrokes = emptyList(),
            averageDistance = 0f,
            strokeCount = 0,
            currentSwingCount = 0,
            isSwingCompleted = false,
            isAutoStarted = false,
            showDetailedStats = false,  // 상세 통계 표시 초기화
            averageSwingTime = 0L  // 평균 스윙 시간 초기화
        )
        strokeDetector.reset()
        // 자동 모니터링 관련 변수 초기화
        isFirstSample = true
        previousAccelMagnitude = 0f
        previousGyroMagnitude = 0f
        // wrist up 관련 변수 초기화
        wristUpStartTime = 0L
        // 디버그 정보 초기화
        _uiState.value = _uiState.value.copy(
            debugAccelMagnitude = 0f,
            debugGyroMagnitude = 0f
        )
        // 자동 모니터링 재시작
        startAutoStartMonitoring()
    }
    
    /**
     * 자동 시작 취소
     */
    fun cancelAutoStart() {
        _uiState.value = _uiState.value.copy(
            autoStartCountdown = 0,
            isMonitoringForAutoStart = false
        )
        // 자동 모니터링 재시작
        startAutoStartMonitoring()
    }

    /**
     * 평균 스윙 시간 계산 함수
     * 인접한 스윙들 간의 시간차를 계산해서 각 스윙의 시간을 추정
     */
    private fun calculateAverageSwingTime(strokes: List<SimplePuttStroke>): Long {
        if (strokes.size <= 1) return 300L  // 기본값

        // 인접한 스윙들 간의 시간차 계산
        val timeDifferences = mutableListOf<Long>()
        for (i in 1 until strokes.size) {
            val timeDiff = strokes[i].timestamp - strokes[i-1].timestamp
            // 비정상적으로 긴 시간차는 제외 (예: 측정 중단 후 재시작)
            if (timeDiff in 200L..2000L) {  // 200ms ~ 2초 사이만 유효
                timeDifferences.add(timeDiff)
            }
        }

        return if (timeDifferences.isNotEmpty()) {
            timeDifferences.average().toLong()
        } else {
            300L  // 기본값
        }
    }

    /**
     * 속도를 이용한 거리 예측 함수
     * 1 m/s = 2 m, 2 m/s = 20 m 기반 선형 관계
     */
    private fun predictDistanceFromSpeed(speed: Float): Float {
        // 선형 관계: 거리 = 18 * 속도 - 16
        // 검증: 1m/s -> 18*1 - 16 = 2m ✓
        //       2m/s -> 18*2 - 16 = 20m ✓
        val distance = 18f * speed - 16f
        return distance.coerceAtLeast(0f) // 음수 방지
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
     * 화면 나가기 (센서 수집 중지)
     */
    fun onScreenExit() {
        // 측정 중이면 중지
        if (_uiState.value.isActive) {
            stopMeasurement()
        }
        
        // 센서 수집 중지 (배터리 절약)
        stopSensorCollection()
        
        // 자동 모니터링 중지
        _uiState.value = _uiState.value.copy(isMonitoringForAutoStart = false)
        
        // 모바일에 상태 전송
        viewModelScope.launch {
            sendAppStateToMobile()
        }
    }
    
    /**
     * 화면 진입 (센서 수집 재시작)
     */
    fun onScreenEnter() {
        // 센서 수집 재시작
        startSensorCollection()
        
        // 자동 모니터링 시작
        startAutoStartMonitoring()
        
        // 모바일에 상태 전송
        viewModelScope.launch {
            sendAppStateToMobile()
        }
    }
    
    // ===== Wearable 데이터 전송 함수들 =====
    
    /**
     * 실시간 퍼팅 상태를 모바일로 전송
     */
    private suspend fun sendPuttingStateToMobile() {
        val state = _uiState.value
        val puttingState = PuttingStateData(
            isActive = state.isActive,
            currentPhase = PhaseData(
                type = "Simple",
                displayName = if (state.isActive) "측정 중" else "대기 중",
                color = if (state.isActive) "Green" else "Gray",
                subtitle = if (state.isActive) "스윙하세요" else "측정을 시작하세요"
            ),
            sessionCount = state.strokeCount,
            averageDistance = state.averageDistance,
            currentMaxSpeed = state.currentMaxSpeed
        )
        wearableDataSender.sendPuttingState(puttingState)
    }
    
    /**
     * 세션 통계를 모바일로 전송
     */
    private suspend fun sendSessionStatsToMobile() {
        val state = _uiState.value
        val strokes = state.sessionStrokes
        
        val sessionStats = SessionStatsData(
            totalStrokes = strokes.size,
            averageDistance = state.averageDistance,
            bestDistance = strokes.maxOfOrNull { it.predictedDistance } ?: 0f,
            averageStability = 1.0f, // 단순화 버전에서는 고정값
            averageSmoothness = 1.0f, // 단순화 버전에서는 고정값
            sessionStartTime = sessionStartTime
        )
        wearableDataSender.sendSessionStats(sessionStats)
    }
    
    /**
     * 앱 상태를 모바일로 전송
     */
    private suspend fun sendAppStateToMobile() {
        val appState = AppStateData(
            isWearAppActive = true,
            isMeasuring = _uiState.value.isActive
        )
        wearableDataSender.sendAppState(appState)
    }
    
    override fun onCleared() {
        super.onCleared()
        // 센서 수집 중지
        stopSensorCollection()
        // 측정 중지
        if (_uiState.value.isActive) {
            strokeDetector.stopMeasurement()
        }
        wearableDataSender.cleanup()
    }
}

// WearableDataSender에 SimplePuttStroke 전송 함수 추가를 위한 확장 함수
private suspend fun WearableDataSender.sendSimpleStrokeResult(stroke: SimplePuttStroke) {
    // 기존 StrokeResultData 형식으로 변환하여 전송
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


