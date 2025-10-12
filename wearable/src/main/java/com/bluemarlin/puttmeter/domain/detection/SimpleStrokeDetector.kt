package com.bluemarlin.puttmeter.wearable.domain.detection

import com.bluemarlin.puttmeter.wearable.domain.model.*
import com.bluemarlin.puttmeter.wearable.domain.processing.SignalFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 단순화된 스트로크 감지기
 * 측정 시작 후 최대 속도를 감지하여 거리를 예측
 */
class SimpleStrokeDetector(
    private val calibrationFactor: Float = 1.0f  // 보정 계수 (속도 x 계수 = 거리)
) {
    private val _detectedStroke = MutableStateFlow<SimplePuttStroke?>(null)
    val detectedStroke: StateFlow<SimplePuttStroke?> = _detectedStroke.asStateFlow()
    
    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()
    
    private val _currentMaxSpeed = MutableStateFlow(0f)
    val currentMaxSpeed: StateFlow<Float> = _currentMaxSpeed.asStateFlow()
    
    // 센서 데이터 버퍼
    private val sensorBuffer = mutableListOf<SensorData>()
    private val maxBufferSize = 200 // 2초 @ 100Hz
    
    // 측정 상태
    private var measurementStartTime = 0L
    private var maxSpeed = 0f
    private var maxSpeedTimestamp = 0L
    
    // 움직임 감지 상태
    private var lastSignificantMotionTime = 0L
    private val motionTimeout = 2000L // 2초간 움직임이 없으면 자동으로 측정 완료
    
    // 초기 안정화 시간
    private val stabilizationTime = 500L // 측정 시작 후 0.5초는 무시
    
    // 이전 평활화된 값
    private var previousSmoothedMagnitude = 0f
    
    /**
     * 측정 시작
     */
    fun startMeasurement() {
        _isActive.value = true
        measurementStartTime = System.currentTimeMillis()
        maxSpeed = 0f
        maxSpeedTimestamp = 0L
        lastSignificantMotionTime = measurementStartTime
        previousSmoothedMagnitude = 0f
        sensorBuffer.clear()
        _currentMaxSpeed.value = 0f
    }
    
    /**
     * 측정 중지
     */
    fun stopMeasurement() {
        if (_isActive.value && maxSpeed > 0.5f) { // 최소 속도 임계값
            completeStroke()
        }
        _isActive.value = false
    }
    
    /**
     * 센서 데이터 처리
     */
    fun processSensorData(sensorData: SensorData) {
        if (!_isActive.value) return
        
        // 초기 안정화 시간 동안은 데이터 수집만 하고 속도는 측정하지 않음
        val elapsedSinceStart = sensorData.timestamp - measurementStartTime
        val isStabilizing = elapsedSinceStart < stabilizationTime
        
        // 버퍼에 추가
        sensorBuffer.add(sensorData)
        if (sensorBuffer.size > maxBufferSize) {
            sensorBuffer.removeAt(0)
        }
        
        // 안정화 중에는 속도 측정 안 함
        if (isStabilizing) {
            return
        }
        
        // 선형 가속도 (중력 제거)
        val linearAccel = SignalFilter.removeGravity(sensorData.acceleration)
        val magnitude = linearAccel.magnitude()
        
        // 평활화
        val smoothedMagnitude = SignalFilter.exponentialMovingAverage(
            current = magnitude,
            previous = previousSmoothedMagnitude,
            alpha = 0.3f
        )
        previousSmoothedMagnitude = smoothedMagnitude
        
        // 속도 추정 (가속도 적분 근사)
        val speed = estimateSpeed(smoothedMagnitude)
        
        // 최대 속도 업데이트
        if (speed > maxSpeed) {
            maxSpeed = speed
            maxSpeedTimestamp = sensorData.timestamp
            _currentMaxSpeed.value = speed
        }
        
        // 움직임 감지 (타임아웃 없이 계속 측정)
        if (smoothedMagnitude > 1.0f) { // 움직임 임계값
            lastSignificantMotionTime = sensorData.timestamp
        }
    }
    
    /**
     * 속도 추정 (간단한 적분 근사)
     */
    private fun estimateSpeed(@Suppress("UNUSED_PARAMETER") acceleration: Float): Float {
        // 최근 10개 샘플의 평균 가속도로 속도 추정
        if (sensorBuffer.size < 10) return 0f
        
        val recentSamples = sensorBuffer.takeLast(10)
        val accelerations = recentSamples.map { 
            SignalFilter.removeGravity(it.acceleration).magnitude() 
        }
        
        // 간단한 적분 (가속도 x 시간)
        val avgAcceleration = accelerations.average().toFloat()
        val dt = 0.01f // 10ms = 0.01s
        
        return avgAcceleration * dt * 10f // 10개 샘플
    }
    
    /**
     * 스트로크 완료 처리
     */
    private fun completeStroke() {
        val elapsedTime = maxSpeedTimestamp - measurementStartTime
        
        // 최소 스윙 시간 체크
        if (elapsedTime < 200L) { // 200ms 미만은 무효
            return
        }
        
        // 거리 예측: 속도 x 보정 계수
        val predictedDistance = maxSpeed * calibrationFactor
        
        val stroke = SimplePuttStroke(
            timestamp = maxSpeedTimestamp,
            maxSpeed = maxSpeed,
            predictedDistance = predictedDistance.coerceIn(0.1f, 15.0f), // 0.1m ~ 15m
            swingTime = elapsedTime
        )
        
        _detectedStroke.value = stroke
    }
    
    /**
     * 감지된 스트로크 초기화
     */
    fun clearDetectedStroke() {
        _detectedStroke.value = null
    }
    
    /**
     * 리셋
     */
    fun reset() {
        _isActive.value = false
        _detectedStroke.value = null
        _currentMaxSpeed.value = 0f
        sensorBuffer.clear()
        maxSpeed = 0f
        maxSpeedTimestamp = 0L
        measurementStartTime = 0L
        lastSignificantMotionTime = 0L
        previousSmoothedMagnitude = 0f
    }
}

/**
 * 단순화된 퍼팅 스트로크 결과
 */
data class SimplePuttStroke(
    val timestamp: Long,
    val maxSpeed: Float,           // 최대 속도 (m/s)
    val predictedDistance: Float,  // 예측 거리 (m)
    val actualDistance: Float? = null, // 실제 거리 (캘리브레이션용)
    val swingTime: Long = 0L       // 스윙 시간 (ms)
)

/**
 * 캘리브레이션 데이터
 */
data class CalibrationData(
    val measurements: List<CalibrationMeasurement> = emptyList(),
    val averageFactor: Float = 1.0f,  // 평균 보정 계수
    val requiredCount: Int = 5,  // 필요한 측정 횟수
    val lastUpdated: Long = 0L
) {
    val isValid: Boolean
        get() = requiredCount == 0 || measurements.size >= requiredCount
    
    fun addMeasurement(maxSpeed: Float, actualDistance: Float, maxCount: Int): CalibrationData {
        val newMeasurement = CalibrationMeasurement(
            maxSpeed = maxSpeed,
            actualDistance = actualDistance,
            factor = actualDistance / maxSpeed,
            timestamp = System.currentTimeMillis()
        )
        
        val newMeasurements = (measurements + newMeasurement).let {
            if (maxCount > 0) it.takeLast(maxCount) else it
        }
        val newAverageFactor = newMeasurements.map { it.factor }.average().toFloat()
        
        return copy(
            measurements = newMeasurements,
            averageFactor = newAverageFactor,
            requiredCount = maxCount,
            lastUpdated = System.currentTimeMillis()
        )
    }
}

/**
 * 개별 캘리브레이션 측정
 */
data class CalibrationMeasurement(
    val maxSpeed: Float,
    val actualDistance: Float,
    val factor: Float,
    val timestamp: Long
)

