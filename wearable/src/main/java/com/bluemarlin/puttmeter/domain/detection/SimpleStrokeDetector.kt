package com.bluemarlin.puttmeter.wearable.domain.detection

import com.bluemarlin.puttmeter.wearable.domain.model.*
import com.bluemarlin.puttmeter.wearable.domain.processing.SignalFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 속도 측정 알고리즘
 */
enum class SpeedAlgorithm {
    ACCELEROMETER_ONLY,      // 가속도계만 사용
    GYROSCOPE_ONLY,          // 자이로스코프만 사용
    SENSOR_FUSION,           // 가속도 + 자이로 융합
    PEAK_ACCELERATION        // 피크 가속도 기반
}

/**
 * 단순화된 스트로크 감지기
 * 측정 시작 후 최대 속도를 감지하여 거리를 예측
 */
class SimpleStrokeDetector(
    private var calibrationFactor: Float = 1.0f,  // 보정 계수 (속도 x 계수 = 거리)
    private val algorithm: SpeedAlgorithm = SpeedAlgorithm.SENSOR_FUSION  // 속도 측정 알고리즘
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

    // 스윙 감지용 변수들
    private var previousMaxSpeed = 0f
    private var lastSwingTime = 0L
    private val minSwingInterval = 500L // 최소 스윙 간격 (500ms)
    
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
        // 스윙 감지 변수 초기화
        previousMaxSpeed = 0f
        lastSwingTime = measurementStartTime
        sensorBuffer.clear()
        _currentMaxSpeed.value = 0f
    }
    
    /**
     * 측정 중지
     */
    fun stopMeasurement() {
        if (_isActive.value && maxSpeed > 0.1f) { // 최소 속도 임계값 (완화)
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
        
        // 선택된 알고리즘으로 속도 추정
        val speed = when (algorithm) {
            SpeedAlgorithm.ACCELEROMETER_ONLY -> estimateSpeedFromAccelerometer()
            SpeedAlgorithm.GYROSCOPE_ONLY -> estimateSpeedFromGyroscope()
            SpeedAlgorithm.SENSOR_FUSION -> estimateSpeedFusion()
            SpeedAlgorithm.PEAK_ACCELERATION -> estimateSpeedFromPeak()
        }
        
        // 최대 속도 업데이트
        if (speed > maxSpeed) {
            maxSpeed = speed
            maxSpeedTimestamp = sensorData.timestamp
            _currentMaxSpeed.value = speed
        }

        // 실시간 스윙 감지: 속도가 1.0~2.0 m/s 범위에 도달하고 최소 간격이 지났으면 스윙으로 간주
        val isValidPuttingSpeed = speed in 1.0f..2.0f
        val timeSinceLastSwing = sensorData.timestamp - lastSwingTime

        if (isValidPuttingSpeed && timeSinceLastSwing > minSwingInterval) {
            // 새로운 스윙 감지
            val strokeSpeed = speed  // 현재 속도를 사용
            val predictedDistance = predictDistanceFromSpeed(strokeSpeed)

            val stroke = SimplePuttStroke(
                timestamp = sensorData.timestamp,
                maxSpeed = strokeSpeed,
                predictedDistance = predictedDistance.coerceIn(0.1f, 15.0f),
                swingTime = 300L // 기본 스윙 시간
            )

            _detectedStroke.value = stroke

            // 다음 스윙을 위해 변수들 리셋
            maxSpeed = 0f
            lastSwingTime = sensorData.timestamp
        }
        
        // 움직임 감지 (타임아웃 없이 계속 측정)
        if (smoothedMagnitude > 0.3f) { // 움직임 임계값 (완화)
            lastSignificantMotionTime = sensorData.timestamp
        }
    }
    
    /**
     * 알고리즘 1: 가속도계만 사용 (기존 방식)
     */
    private fun estimateSpeedFromAccelerometer(): Float {
        if (sensorBuffer.size < 5) return 0f  // 최소 샘플 수 감소

        val recentSamples = sensorBuffer.takeLast(5)  // 샘플 수 감소
        val accelerations = recentSamples.map {
            SignalFilter.removeGravity(it.acceleration).magnitude()
        }

        val maxAcceleration = accelerations.maxOrNull() ?: 0f  // 최대값 사용
        val dt = 0.01f

        // 더 간단한 속도 계산
        return maxAcceleration * dt * 5f  // 계수 조정
    }
    
    /**
     * 알고리즘 2: 자이로스코프만 사용
     */
    private fun estimateSpeedFromGyroscope(): Float {
        if (sensorBuffer.size < 5) return 0f
        
        val recentSamples = sensorBuffer.takeLast(5)
        val angularVelocities = recentSamples.map { 
            it.gyroscope.magnitude()  // rad/s
        }
        
        // 손목에서 퍼터 헤드까지 거리 (약 0.9m)
        val armLength = 0.9f
        
        // 선속도 = 각속도 × 반지름
        val maxAngularVel = angularVelocities.maxOrNull() ?: 0f
        return maxAngularVel * armLength
    }
    
    /**
     * 알고리즘 3: 센서 융합 (가속도 + 자이로)
     */
    private fun estimateSpeedFusion(): Float {
        if (sensorBuffer.size < 10) return 0f
        
        // 가속도 기반 속도
        val accelSpeed = estimateSpeedFromAccelerometer()
        
        // 자이로 기반 속도
        val gyroSpeed = estimateSpeedFromGyroscope()
        
        // 가중 평균 (자이로가 더 신뢰도 높음)
        return gyroSpeed * 0.7f + accelSpeed * 0.3f
    }
    
    /**
     * 알고리즘 4: 피크 가속도 기반
     */
    private fun estimateSpeedFromPeak(): Float {
        if (sensorBuffer.size < 20) return 0f
        
        val recentSamples = sensorBuffer.takeLast(20)
        val accelerations = recentSamples.map { 
            SignalFilter.removeGravity(it.acceleration).magnitude() 
        }
        
        // 최대 가속도 찾기
        val peakAccel = accelerations.maxOrNull() ?: 0f
        
        // 경험적 변환 계수 (캘리브레이션으로 조정)
        return peakAccel * 0.15f
    }
    
    /**
     * 스트로크 완료 처리
     */
    private fun completeStroke() {
        val elapsedTime = maxSpeedTimestamp - measurementStartTime
        
        // 최소 스윙 시간 체크
        if (elapsedTime < 100L) { // 100ms 미만은 무효 (완화)
            return
        }
        
        // 거리 예측: 새로운 속도-거리 관계 기반
        val predictedDistance = predictDistanceFromSpeed(maxSpeed)
        
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
     * 보정 계수 업데이트
     */
    fun updateCalibrationFactor(factor: Float) {
        calibrationFactor = factor
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
        // 스윙 감지 변수 초기화
        previousMaxSpeed = 0f
        lastSwingTime = 0L
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

