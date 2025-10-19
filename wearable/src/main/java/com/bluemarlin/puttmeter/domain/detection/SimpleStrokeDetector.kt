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
    private val algorithm: SpeedAlgorithm = SpeedAlgorithm.SENSOR_FUSION,  // 속도 측정 알고리즘
    swingStartThreshold: Float = 0.3f  // 스윙 시작 임계값 (외부 설정 가능)
) {
    private val _detectedStroke = MutableStateFlow<SimplePuttStroke?>(null)
    val detectedStroke: StateFlow<SimplePuttStroke?> = _detectedStroke.asStateFlow()
    
    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()
    
    private val _currentMaxSpeed = MutableStateFlow(0f)
    val currentMaxSpeed: StateFlow<Float> = _currentMaxSpeed.asStateFlow()
    
    private val _currentSpeed = MutableStateFlow(0f)
    val currentSpeed: StateFlow<Float> = _currentSpeed.asStateFlow()
    
    private val _isInSwing = MutableStateFlow(false)
    val isInSwingState: StateFlow<Boolean> = _isInSwing.asStateFlow()
    
    // 센서 데이터 버퍼
    private val sensorBuffer = mutableListOf<SensorData>()
    private val maxBufferSize = 200 // 2초 @ 100Hz
    
    // 측정 상태
    private var measurementStartTime = 0L
    private var maxSpeed = 0f
    private var maxSpeedTimestamp = 0L
    
    // 스윙 상태 추적
    private var isInSwing = false  // 현재 스윙 중인지
    private var swingStartTime = 0L
    private var swingEndDetectionTime = 0L
    private var lastSignificantMotionTime = 0L
    private var speedPeakReachedTime = 0L  // 최대 속도 도달 시간
    private var isPeakReached = false  // 최대 속도에 도달했는지
    
    // 스윙 감지 임계값
    private var swingStartThreshold = swingStartThreshold  // 스윙 시작 속도 (m/s, 외부 설정)
    private val swingEndIdleTime = 500L  // 스윙 종료 판단 시간 (500ms 정지)
    private val minSwingDuration = 150L  // 최소 스윙 시간 (150ms)
    private val motionThreshold = 1.0f  // 움직임 감지 임계값 (1.0 m/s²)
    private val maxSwingDuration = 5000L  // 최대 스윙 시간 (5초 타임아웃)
    
    // 초기 안정화 시간 (새 UX에서는 필요 없음)
    private val stabilizationTime = 0L  // 0ms - 즉시 측정 시작

    // 이전 평활화된 값
    private var previousSmoothedMagnitude = 0f
    
    // 스윙 각도 계산용 변수들
    private var addressAngle = 0f    // 어드레스 자세 각도 (라디안)
    private var maxBackswingAngle = 0f   // 백스윙 최대 각도 (라디안)
    
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
        
        // 스윙 상태 초기화
        isInSwing = false
        swingStartTime = 0L
        swingEndDetectionTime = 0L
        speedPeakReachedTime = 0L
        isPeakReached = false
        
        // 스윙 각도 변수 초기화
        addressAngle = 0f
        maxBackswingAngle = 0f
        
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
        
        // 스윙 각도 계산 (가속도계 기반 - 지면 대비 절대 각도)
        val currentAngle = calculateTiltAngle(sensorData.acceleration)
        
        // 선택된 알고리즘으로 속도 추정
        val speed = when (algorithm) {
            SpeedAlgorithm.ACCELEROMETER_ONLY -> estimateSpeedFromAccelerometer()
            SpeedAlgorithm.GYROSCOPE_ONLY -> estimateSpeedFromGyroscope()
            SpeedAlgorithm.SENSOR_FUSION -> estimateSpeedFusion()
            SpeedAlgorithm.PEAK_ACCELERATION -> estimateSpeedFromPeak()
        }
        
        // 현재 속도 업데이트 (디버깅용)
        _currentSpeed.value = speed
        
        // 스윙 상태 관리
        val hasMotion = smoothedMagnitude > motionThreshold  // 1.0f로 높임
        
        if (hasMotion) {
            lastSignificantMotionTime = sensorData.timestamp
        }
        
        // 스윙 시작 감지
        if (!isInSwing && speed > swingStartThreshold) {
            // 스윙 시작 - 현재 각도를 어드레스 각도로 저장
            isInSwing = true
            _isInSwing.value = true
            swingStartTime = sensorData.timestamp
            maxSpeed = 0f
            maxSpeedTimestamp = 0L
            addressAngle = currentAngle  // 어드레스 자세 각도 기준
            maxBackswingAngle = 0f
            isPeakReached = false
            speedPeakReachedTime = 0L
            _currentMaxSpeed.value = 0f
        }
        
        // 스윙 진행 중
        if (isInSwing) {
            val swingElapsedTime = sensorData.timestamp - swingStartTime
            
            // 타임아웃 체크 (5초)
            if (swingElapsedTime >= maxSwingDuration) {
                // 타임아웃 - 스윙 강제 종료
                if (maxSpeed >= 0.5f) {
                    completeSwingDetection(sensorData.timestamp)
                } else {
                    // 유효하지 않은 스윙 - 리셋만
                    isInSwing = false
                    _isInSwing.value = false
                    maxSpeed = 0f
                    _currentMaxSpeed.value = 0f
                }
                return
            }
            
            // 백스윙 각도 추적
            val backswingAngle = kotlin.math.abs(currentAngle - addressAngle)
            if (backswingAngle > maxBackswingAngle) {
                maxBackswingAngle = backswingAngle
            }
            
            // 최대 속도 업데이트 및 피크 감지
            if (speed > maxSpeed) {
                maxSpeed = speed
                maxSpeedTimestamp = sensorData.timestamp
                _currentMaxSpeed.value = speed
                isPeakReached = false  // 새로운 피크이므로 리셋
            } else if (!isPeakReached && maxSpeed >= 0.5f && speed < maxSpeed * 0.7f) {
                // 피크 도달 감지: 최대 속도의 70% 이하로 떨어지면
                isPeakReached = true
                speedPeakReachedTime = sensorData.timestamp
            }
            
            // 스윙 종료 감지
            val idleTime = sensorData.timestamp - lastSignificantMotionTime
            val timeSincePeak = if (isPeakReached) sensorData.timestamp - speedPeakReachedTime else 0L
            
            // 종료 조건: 
            // 1. 피크 도달 후 500ms 정지, 또는
            // 2. 피크 도달 후 1초 경과
            if (isPeakReached && (idleTime >= swingEndIdleTime || timeSincePeak >= 1000L)) {
                completeSwingDetection(sensorData.timestamp)
            }
        }
    }
    
    /**
     * 스윙 완료 처리
     */
    private fun completeSwingDetection(currentTime: Long) {
        val swingDuration = maxSpeedTimestamp - swingStartTime
        
        // 유효한 스윙인지 검증
        if (swingDuration >= minSwingDuration && maxSpeed >= 0.5f) {
            // 백스윙 각도를 도(degree)로 변환
            val backswingAngleDegrees = Math.toDegrees(maxBackswingAngle.toDouble()).toFloat()
            
            // 거리 예측
            val predictedDistance = predictDistanceFromSpeed(maxSpeed)
            
            val stroke = SimplePuttStroke(
                timestamp = maxSpeedTimestamp,
                maxSpeed = maxSpeed,
                predictedDistance = predictedDistance.coerceAtLeast(0f),  // 음수만 방지
                swingTime = swingDuration,
                swingAngle = backswingAngleDegrees
            )
            
            _detectedStroke.value = stroke
        }
        
        // 스윙 상태 리셋
        isInSwing = false
        _isInSwing.value = false
        maxSpeed = 0f
        _currentMaxSpeed.value = 0f
        isPeakReached = false
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
        
        // 백스윙 각도를 도(degree)로 변환
        val backswingAngleDegrees = Math.toDegrees(maxBackswingAngle.toDouble()).toFloat()
        
        val stroke = SimplePuttStroke(
            timestamp = maxSpeedTimestamp,
            maxSpeed = maxSpeed,
            predictedDistance = predictedDistance.coerceAtLeast(0f),  // 음수만 방지
            swingTime = elapsedTime,
            swingAngle = backswingAngleDegrees
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
     * 스윙 시작 임계값 업데이트
     */
    fun updateSwingStartThreshold(threshold: Float) {
        swingStartThreshold = threshold
    }
    
    /**
     * 가속도계를 사용하여 지면 대비 기울기 각도 계산
     * 퍼터가 지면과 수직(어드레스 자세) = 0도
     * 백스윙 시 기울어진 각도를 측정
     */
    private fun calculateTiltAngle(acceleration: Vector3): Float {
        // 가속도 벡터의 크기
        val magnitude = acceleration.magnitude()
        if (magnitude < 1f) return 0f  // 너무 작으면 무시
        
        // X-Z 평면에서의 각도 계산 (백스윙 동작)
        // atan2(x, z)를 사용하여 중력 방향 대비 기울기 계산
        val angleRadians = kotlin.math.atan2(acceleration.x, acceleration.z)
        
        return angleRadians  // 라디안 반환
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
        _currentSpeed.value = 0f
        _isInSwing.value = false
        sensorBuffer.clear()
        maxSpeed = 0f
        maxSpeedTimestamp = 0L
        measurementStartTime = 0L
        lastSignificantMotionTime = 0L
        previousSmoothedMagnitude = 0f
        
        // 스윙 상태 초기화
        isInSwing = false
        swingStartTime = 0L
        swingEndDetectionTime = 0L
        speedPeakReachedTime = 0L
        isPeakReached = false
        
        // 스윙 각도 변수 초기화
        addressAngle = 0f
        maxBackswingAngle = 0f
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
    val swingTime: Long = 0L,      // 스윙 시간 (ms)
    val swingAngle: Float = 0f     // 스윙 각도 (도)
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

