package com.bluemarlin.puttmeter.wearable.presentation.measurement

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bluemarlin.puttmeter.wearable.data.sensor.SensorDataSource
import com.bluemarlin.puttmeter.wearable.data.wearable.*
import com.bluemarlin.puttmeter.wearable.domain.detection.SimpleStrokeDetector
import com.bluemarlin.puttmeter.wearable.domain.detection.SimplePuttStroke
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

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
    val error: String? = null
) {
    val isCountingDown: Boolean
        get() = countdownSeconds > 0
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
    
    init {
        // 저장된 캘리브레이션 계수 로드
        val calibrationFactor = sharedPreferences.getFloat("calibration_factor", 1.0f)
        _uiState.value = _uiState.value.copy(calibrationFactor = calibrationFactor)
        
        // 단순화된 스트로크 감지기 생성
        strokeDetector = SimpleStrokeDetector(calibrationFactor)
        
        // 센서 데이터 수집 시작 (앱이 실행되는 동안 계속 유지)
        startSensorCollection()
        
        // 앱 시작 시 연결 상태 전송
        viewModelScope.launch {
            sendAppStateToMobile()
        }
        
        // 주기적 하트비트 전송 (5초마다)
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(5000)
                sendAppStateToMobile()
            }
        }
        
        // 현재 최대 속도 관찰 및 모바일로 전송
        viewModelScope.launch {
            strokeDetector.currentMaxSpeed.collect { speed ->
                // 예측 거리 계산: 속도 x 보정 계수
                val predictedDist = speed * _uiState.value.calibrationFactor
                _uiState.value = _uiState.value.copy(
                    currentMaxSpeed = speed,
                    predictedDistance = predictedDist.coerceIn(0f, 20f)
                )
                // 실시간 최대 속도를 모바일로 전송
                if (_uiState.value.isActive) {
                    sendPuttingStateToMobile()
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
     * 측정 시작 (3초 카운트다운 후)
     */
    fun startMeasurement() {
        if (_uiState.value.isActive || _uiState.value.isCountingDown) return
        
        sessionStartTime = System.currentTimeMillis()
        
        // 센서 감지기 리셋 (이전 데이터 초기화)
        strokeDetector.reset()
        
        // UI 상태 초기화
        _uiState.value = _uiState.value.copy(
            currentMaxSpeed = 0f,
            predictedDistance = 0f,
            error = null
        )
        
        // 3초 카운트다운 시작
        viewModelScope.launch {
            for (i in 3 downTo 1) {
                _uiState.value = _uiState.value.copy(
                    countdownSeconds = i
                )
                kotlinx.coroutines.delay(1000)
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
        
        _uiState.value = _uiState.value.copy(
            lastStroke = stroke,
            sessionStrokes = currentStrokes,
            averageDistance = avgDistance,
            strokeCount = currentStrokes.size
        )
        
        // 스트로크 결과를 모바일로 전송
        viewModelScope.launch {
            wearableDataSender.sendSimpleStrokeResult(stroke)
            sendSessionStatsToMobile()
        }
        
        // 스트로크 처리 완료
        strokeDetector.clearDetectedStroke()
    }
    
    /**
     * 세션 초기화
     */
    fun clearSession() {
        _uiState.value = _uiState.value.copy(
            lastStroke = null,
            sessionStrokes = emptyList(),
            averageDistance = 0f,
            strokeCount = 0
        )
        strokeDetector.reset()
    }
    
    /**
     * 오류 메시지 지우기
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
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


