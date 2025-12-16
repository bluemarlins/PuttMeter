package com.bluemarlin.puttmeter.wearable.domain.detection

import android.util.Log
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
    private var slope: Float = 3.0f,       // 이차 계수 a (속도²-거리 관계): distance = a*speed² + b*speed + c
    private var intercept: Float = -1.0f,   // 일차 계수 b (속도-거리 관계): 기본값 0 m/s=0m, 1 m/s=2m, 2 m/s=10m
    private var constant: Float = 0.0f,    // 상수 c (거리 관계): 기본값 0
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
    
    // 가속도 방향 추적 정보 (디버깅/표시용)
    private val _accelDirectionDotProduct = MutableStateFlow(1.0f)  // 가속도 방향 내적 (-1 ~ 1)
    val accelDirectionDotProduct: StateFlow<Float> = _accelDirectionDotProduct.asStateFlow()
    
    private val _isAccelDirectionStable = MutableStateFlow(false)  // 가속도 방향이 안정적인지
    val isAccelDirectionStable: StateFlow<Boolean> = _isAccelDirectionStable.asStateFlow()
    
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
    
    // 백스윙/다운스윙 구분용 변수들
    private var isInBackswing = false  // 백스윙 중인지
    private var isInDownswing = false  // 다운스윙 중인지
    private var downswingStartTime = 0L  // 다운스윙 시작 시간
    private var previousAngle = 0f  // 이전 각도 (백스윙 종료 감지용)
    private val angleChangeThreshold = 0.05f  // 각도 변화 임계값 (라디안, 약 3도)
    
    // 가속도 방향 변화 추적 (종료 감지용)
    private var previousLinearAccel: Vector3? = null  // 이전 선형 가속도 벡터
    private var downswingAccelDirection: Vector3? = null  // 다운스윙 시작 시 가속도 방향
    private var accelDirectionStableTime = 0L  // 가속도 방향이 안정된 시간
    private val accelDirectionChangeThreshold = 0.7f  // 가속도 방향 변화 임계값 (코사인, 약 45도)
    private val stableAccelTimeThreshold = 200L  // 가속도 방향 안정화 시간 (200ms)
    
    // 로깅 관련 변수
    private val TAG = "StrokeDetector"
    private var lastLogTime = 0L
    private val logInterval = 50L  // 50ms마다 로그 출력 (20Hz)
    
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
        
        // 백스윙/다운스윙 상태 초기화
        isInBackswing = false
        isInDownswing = false
        downswingStartTime = 0L
        previousAngle = 0f
        
        // 가속도 방향 추적 초기화
        previousLinearAccel = null
        downswingAccelDirection = null
        accelDirectionStableTime = 0L
        
        sensorBuffer.clear()
        _currentMaxSpeed.value = 0f
        lastLogTime = 0L
    }
    
    /**
     * 측정 중지
     */
    fun stopMeasurement() {
        // 속도 조건 제거: 다운스윙이 완료되면 속도에 관계없이 측정 완료
        if (_isActive.value && isInDownswing) {
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
        
        // 스윙 중일 때 센서 데이터 로깅 (백스윙/다운스윙 구분 분석용)
        if (isInSwing && (sensorData.timestamp - lastLogTime >= logInterval)) {
            logSwingData(
                sensorData = sensorData,
                linearAccel = linearAccel,
                magnitude = magnitude,
                currentAngle = currentAngle,
                speed = speed,
                isInBackswing = isInBackswing,
                isInDownswing = isInDownswing,
                backswingAngle = if (isInSwing) kotlin.math.abs(currentAngle - addressAngle) else 0f,
                maxBackswingAngle = maxBackswingAngle,
                previousAngle = previousAngle,
                angleDelta = kotlin.math.abs(currentAngle - previousAngle)
            )
            lastLogTime = sensorData.timestamp
        }
        
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
            
            // 백스윙 상태로 시작 (다운스윙은 아직 시작하지 않음)
            isInBackswing = true
            isInDownswing = false
            downswingStartTime = 0L
            previousAngle = currentAngle
            
            // 가속도 방향 추적 초기화
            previousLinearAccel = linearAccel
            downswingAccelDirection = null
            accelDirectionStableTime = 0L
            
            // 스윙 시작 로그
            val addressAngleDegrees = Math.toDegrees(currentAngle.toDouble())
            Log.i(TAG, "[SWING START] 스윙 시작! speed=%.3f m/s > threshold=%.3f m/s, addressAngle=%.3f rad (%.1f°), accel(x=%.2f,y=%.2f,z=%.2f) m/s², gyro(x=%.2f,y=%.2f,z=%.2f) rad/s"
                .format(speed, swingStartThreshold, currentAngle, addressAngleDegrees,
                    sensorData.acceleration.x, sensorData.acceleration.y, sensorData.acceleration.z,
                    sensorData.gyroscope.x, sensorData.gyroscope.y, sensorData.gyroscope.z))
            lastLogTime = sensorData.timestamp
        }
        
        // 스윙 진행 중
        if (isInSwing) {
            val swingElapsedTime = sensorData.timestamp - swingStartTime
            
            // 타임아웃 체크 (5초)
            if (swingElapsedTime >= maxSwingDuration) {
                // 타임아웃 - 다운스윙이 시작되었으면 속도에 관계없이 스윙 완료 처리
                if (isInDownswing) {
                    completeSwingDetection(sensorData.timestamp)
                } else {
                    // 유효하지 않은 스윙 - 리셋만 (다운스윙이 시작되지 않음)
                    isInSwing = false
                    _isInSwing.value = false
                    maxSpeed = 0f
                    _currentMaxSpeed.value = 0f
                    isInBackswing = false
                    isInDownswing = false
                }
                return
            }
            
            // 백스윙 각도 추적
            val backswingAngle = kotlin.math.abs(currentAngle - addressAngle)
            
            // 백스윙 중인 경우
            if (isInBackswing) {
                if (backswingAngle > maxBackswingAngle) {
                    // 백스윙 각도가 계속 증가 중
                    maxBackswingAngle = backswingAngle
                    previousAngle = currentAngle
                    Log.d(TAG, "[BACKSWING] 각도 증가: backswingAngle=%.3f rad (%.1f°), maxBackswingAngle=%.3f rad (%.1f°), speed=%.3f m/s"
                        .format(backswingAngle, Math.toDegrees(backswingAngle.toDouble()),
                            maxBackswingAngle, Math.toDegrees(maxBackswingAngle.toDouble()), speed))
                } else {
                    // 백스윙 각도가 줄어들기 시작 = 다운스윙 시작
                    val angleDelta = kotlin.math.abs(currentAngle - previousAngle)
                    Log.d(TAG, "[BACKSWING->DOWNSWING?] 각도 감소 감지: backswingAngle=%.3f rad (%.1f°), previousAngle=%.3f rad (%.1f°), angleDelta=%.3f rad (%.1f°), threshold=%.3f rad (%.1f°), speed=%.3f m/s"
                        .format(backswingAngle, Math.toDegrees(backswingAngle.toDouble()),
                            previousAngle, Math.toDegrees(previousAngle.toDouble()),
                            angleDelta, Math.toDegrees(angleDelta.toDouble()),
                            angleChangeThreshold, Math.toDegrees(angleChangeThreshold.toDouble()), speed))
                    
                    if (angleDelta > angleChangeThreshold) {
                        // 각도 변화가 충분히 크면 다운스윙 시작으로 판단
                        Log.i(TAG, "[DOWNSWING START] 다운스윙 시작! angleDelta=%.3f rad (%.1f°) > threshold=%.3f rad (%.1f°), 시간=%d ms"
                            .format(angleDelta, Math.toDegrees(angleDelta.toDouble()),
                                angleChangeThreshold, Math.toDegrees(angleChangeThreshold.toDouble()),
                                sensorData.timestamp - swingStartTime))
                        
                        isInBackswing = false
                        isInDownswing = true
                        downswingStartTime = sensorData.timestamp
                        // 다운스윙 시작 시 최대 속도 리셋 (다운스윙에서만 측정)
                        maxSpeed = 0f
                        maxSpeedTimestamp = 0L
                        _currentMaxSpeed.value = 0f
                        isPeakReached = false
                        speedPeakReachedTime = 0L
                        
                        // 다운스윙 시작 시 가속도 방향 저장 (종료 감지용)
                        downswingAccelDirection = linearAccel.normalized()
                        accelDirectionStableTime = sensorData.timestamp
                    }
                }
            }
            
            // 다운스윙 중에만 속도 측정 및 최대값 추적
            if (isInDownswing) {
                // 다운스윙 중 최대 속도 업데이트
                if (speed > maxSpeed) {
                    maxSpeed = speed
                    maxSpeedTimestamp = sensorData.timestamp
                    _currentMaxSpeed.value = speed
                    isPeakReached = false  // 새로운 피크이므로 리셋
                }
                
                // 가속도 방향 변화 추적 (종료 감지용)
                val normalizedAccel = if (magnitude > 0.1f) linearAccel.normalized() else null
                var dotProduct = 1.0f  // 기본값
                var directionStable = false
                
                if (normalizedAccel != null && downswingAccelDirection != null) {
                    // 다운스윙 시작 시 가속도 방향과 현재 가속도 방향의 내적 계산
                    dotProduct = normalizedAccel.x * downswingAccelDirection!!.x +
                                     normalizedAccel.y * downswingAccelDirection!!.y +
                                     normalizedAccel.z * downswingAccelDirection!!.z
                    
                    // UI 업데이트용
                    _accelDirectionDotProduct.value = dotProduct
                    
                    // 방향이 반대로 바뀌었거나 크게 변화했는지 확인 (코사인 < 임계값 = 45도 이상 변화)
                    val directionChanged = dotProduct < accelDirectionChangeThreshold
                    
                    if (directionChanged) {
                        // 가속도 방향이 바뀌면 안정화 시간 리셋
                        accelDirectionStableTime = sensorData.timestamp
                        _isAccelDirectionStable.value = false
                    } else {
                        // 방향이 안정적이면 안정화 시간 업데이트
                        val timeSinceStable = sensorData.timestamp - accelDirectionStableTime
                        directionStable = timeSinceStable >= stableAccelTimeThreshold
                        _isAccelDirectionStable.value = directionStable
                        
                        if (directionStable) {
                            // 가속도 방향이 안정되면 피크 도달로 간주 (속도 조건 제거)
                            if (!isPeakReached) {
                                isPeakReached = true
                                speedPeakReachedTime = sensorData.timestamp
                                Log.i(TAG, "[PEAK REACHED] 가속도 방향 안정화: maxSpeed=%.3f m/s, currentSpeed=%.3f m/s, dotProduct=%.3f, 시간=%d ms"
                                    .format(maxSpeed, speed, dotProduct, sensorData.timestamp - downswingStartTime))
                            }
                        }
                    }
                } else {
                    _accelDirectionDotProduct.value = 1.0f
                    _isAccelDirectionStable.value = false
                }
                
                // 이전 가속도 업데이트
                previousLinearAccel = normalizedAccel ?: previousLinearAccel
            } else {
                // 백스윙 중이거나 다운스윙이 시작되지 않았으면 속도 측정 안 함
                // (최대 속도 업데이트 안 함)
                previousLinearAccel = if (magnitude > 0.1f) linearAccel.normalized() else previousLinearAccel
            }
            
            // previousAngle 업데이트
            previousAngle = currentAngle
            
            // 스윙 종료 감지
            // 다운스윙이 시작되어야만 유효한 스윙으로 간주
            if (isInDownswing) {
                val idleTime = sensorData.timestamp - lastSignificantMotionTime
                val timeSincePeak = if (isPeakReached) sensorData.timestamp - speedPeakReachedTime else 0L
                val timeSinceDownswingStart = sensorData.timestamp - downswingStartTime
                
                // 종료 조건 개선:
                // 1. 다운스윙 시작 후 2초 경과 (타임아웃)
                // 2. 피크 도달 후 500ms 정지, 또는
                // 3. 피크 도달 후 1초 경과, 또는
                // 4. 가속도 방향이 안정되고 최대 속도가 있고 다운스윙 시작 후 500ms 이상 경과
                val shouldComplete = when {
                    // 타임아웃: 다운스윙 시작 후 2초 경과
                    timeSinceDownswingStart >= 2000L -> {
                        true
                    }
                    // 피크가 감지된 경우: 정지 시간 또는 경과 시간으로 종료
                    isPeakReached -> {
                        idleTime >= swingEndIdleTime || timeSincePeak >= 1000L
                    }
                    // 피크가 감지되지 않았지만 다운스윙 시작 후 500ms 경과 (속도 조건 제거)
                    timeSinceDownswingStart >= 500L -> {
                        // 가속도 방향이 안정되었거나 정지 시간이 충분하면 종료
                        _isAccelDirectionStable.value || idleTime >= swingEndIdleTime
                    }
                    else -> false
                }
                
                if (shouldComplete) {
                    Log.i(TAG, "[SWING END] 스윙 종료 감지: isPeakReached=$isPeakReached, maxSpeed=%.3f m/s, idleTime=%d ms, timeSincePeak=%d ms, timeSinceDownswingStart=%d ms"
                        .format(maxSpeed, idleTime, timeSincePeak, timeSinceDownswingStart))
                    completeSwingDetection(sensorData.timestamp)
                }
            } else if (isInBackswing) {
                // 백스윙만 있고 다운스윙이 시작되지 않았는데 타임아웃되면 무효 스윙으로 처리
                val idleTime = sensorData.timestamp - lastSignificantMotionTime
                if (idleTime >= swingEndIdleTime * 2) {  // 백스윙만 있을 경우 더 긴 대기
                    // 유효하지 않은 스윙 - 리셋만
                    isInSwing = false
                    _isInSwing.value = false
                    maxSpeed = 0f
                    _currentMaxSpeed.value = 0f
                    isInBackswing = false
                    isInDownswing = false
                }
            }
        }
    }
    
    /**
     * 스윙 완료 처리
     */
    @Suppress("UNUSED_PARAMETER")
    private fun completeSwingDetection(currentTime: Long) {
        val swingDuration = maxSpeedTimestamp - swingStartTime
        
        // 유효한 스윙인지 검증: 다운스윙이 시작되어야 하고, 최소 스윙 시간 충족 (속도 조건 제거)
        if (isInDownswing && swingDuration >= minSwingDuration) {
            // 백스윙 각도를 도(degree)로 변환
            val backswingAngleDegrees = Math.toDegrees(maxBackswingAngle.toDouble()).toFloat()
            
            // 거리 예측 (다운스윙에서 측정된 최대 속도 사용)
            val predictedDistance = predictDistanceFromSpeed(maxSpeed)
            
            val stroke = SimplePuttStroke(
                timestamp = maxSpeedTimestamp,
                maxSpeed = maxSpeed,
                predictedDistance = predictedDistance,  // 계산된 값 그대로 사용 (음수 제한 제거)
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
        isInBackswing = false
        isInDownswing = false
        downswingStartTime = 0L
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
        
        // 최소 스윙 시간 체크 및 다운스윙이 시작되었는지 확인
        if (elapsedTime < 100L || !isInDownswing) { // 100ms 미만이거나 다운스윙이 시작되지 않았으면 무효
            return
        }
        
        // 거리 예측: 새로운 속도-거리 관계 기반 (다운스윙에서 측정된 최대 속도 사용)
        val predictedDistance = predictDistanceFromSpeed(maxSpeed)
        
        // 백스윙 각도를 도(degree)로 변환
        val backswingAngleDegrees = Math.toDegrees(maxBackswingAngle.toDouble()).toFloat()
        
        val stroke = SimplePuttStroke(
            timestamp = maxSpeedTimestamp,
            maxSpeed = maxSpeed,
            predictedDistance = predictedDistance,  // 계산된 값 그대로 사용 (음수 제한 제거)
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
     * 캘리브레이션 파라미터 업데이트 (회귀분석 결과)
     */
    fun updateCalibration(newSlope: Float, newIntercept: Float) {
        slope = newSlope
        intercept = newIntercept
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
     * 이차 방정식 사용: distance = slope × speed² + intercept × speed + constant
     * 기본값: 0 m/s = 0 m, 1 m/s = 2 m, 2 m/s = 10 m
     * 계산: distance = 3 × speed² - 1 × speed + 0
     * 항상 계산된 거리 값을 반환
     */
    private fun predictDistanceFromSpeed(speed: Float): Float {
        // 이차 방정식: distance = a × speed² + b × speed + c
        // 기본값: a = 3.0, b = -1.0, c = 0.0
        // (0, 0): c = 0
        // (1, 2): 2 = a + b → b = 2 - a → b = -1 (a = 3)
        // (2, 10): 10 = 4a + 2b → 10 = 12 - 2 = 10 ✓
        val distance = slope * speed * speed + intercept * speed + constant
        return distance
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
        
        // 백스윙/다운스윙 상태 초기화
        isInBackswing = false
        isInDownswing = false
        downswingStartTime = 0L
        previousAngle = 0f
        lastLogTime = 0L
        
        // 가속도 방향 추적 초기화
        previousLinearAccel = null
        downswingAccelDirection = null
        accelDirectionStableTime = 0L
    }
    
    /**
     * 스윙 데이터 로깅 (백스윙/다운스윙 구분 분석용)
     */
    private fun logSwingData(
        sensorData: SensorData,
        linearAccel: Vector3,
        magnitude: Float,
        currentAngle: Float,
        speed: Float,
        isInBackswing: Boolean,
        isInDownswing: Boolean,
        backswingAngle: Float,
        maxBackswingAngle: Float,
        previousAngle: Float,
        angleDelta: Float
    ) {
        val state = when {
            isInDownswing -> "DOWNSWING"
            isInBackswing -> "BACKSWING"
            else -> "SWING"
        }
        
        val angleDegrees = Math.toDegrees(currentAngle.toDouble())
        val backswingAngleDegrees = Math.toDegrees(backswingAngle.toDouble())
        val maxBackswingAngleDegrees = Math.toDegrees(maxBackswingAngle.toDouble())
        val angleDeltaDegrees = Math.toDegrees(angleDelta.toDouble())
        val addressAngleDegrees = Math.toDegrees(addressAngle.toDouble())
        
        val timeMs = sensorData.timestamp - swingStartTime
        Log.d(TAG, String.format(
            "[%s] time=%d ms, " +
            "accel(x=%.2f,y=%.2f,z=%.2f,mag=%.2f) m/s², " +
            "gyro(x=%.2f,y=%.2f,z=%.2f,mag=%.2f) rad/s, " +
            "angle=%.3f rad (%.1f°), " +
            "backswingAngle=%.3f rad (%.1f°), " +
            "maxBackswingAngle=%.3f rad (%.1f°), " +
            "angleDelta=%.3f rad (%.1f°), " +
            "speed=%.3f m/s, " +
            "addressAngle=%.3f rad (%.1f°)",
            state,
            timeMs,
            linearAccel.x, linearAccel.y, linearAccel.z, magnitude,
            sensorData.gyroscope.x, sensorData.gyroscope.y, sensorData.gyroscope.z, sensorData.gyroscope.magnitude(),
            currentAngle, angleDegrees,
            backswingAngle, backswingAngleDegrees,
            maxBackswingAngle, maxBackswingAngleDegrees,
            angleDelta, angleDeltaDegrees,
            speed,
            addressAngle, addressAngleDegrees
        ))
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
    val slope: Float = 3.0f,       // 이차 계수 a (기본값: 3.0, 0 m/s=0m, 1 m/s=2m, 2 m/s=10m)
    val intercept: Float = -1.0f,   // 일차 계수 b (기본값: -1.0, 이차 방정식: distance = 3*speed² - 1*speed)
    val requiredCount: Int = 5,    // 필요한 측정 횟수
    val lastUpdated: Long = 0L
) {
    val isValid: Boolean
        get() = requiredCount == 0 || measurements.size >= requiredCount
    
    /**
     * 새로운 측정 추가 및 회귀분석
     */
    fun addMeasurement(maxSpeed: Float, actualDistance: Float, maxCount: Int): CalibrationData {
        val newMeasurement = CalibrationMeasurement(
            maxSpeed = maxSpeed,
            actualDistance = actualDistance,
            timestamp = System.currentTimeMillis()
        )
        
        val newMeasurements = (measurements + newMeasurement).let {
            if (maxCount > 0) it.takeLast(maxCount) else it
        }
        
        // 선형 회귀분석으로 a(기울기)와 b(절편) 계산
        val (newSlope, newIntercept) = if (newMeasurements.size >= 2) {
            calculateLinearRegression(newMeasurements)
        } else if (newMeasurements.size == 1) {
            // 1개만 있으면 기본 이차 방정식 유지하고 일차 계수만 조정
            val measurement = newMeasurements.first()
            // 기본값: distance = 3*speed² - 1*speed
            // actualDistance = 3*speed² + b*speed → b = (actualDistance - 3*speed²) / speed
            val speed = measurement.maxSpeed
            val newIntercept = if (speed > 0.01f) {
                (measurement.actualDistance - 3.0f * speed * speed) / speed
            } else {
                -1.0f  // 속도가 너무 작으면 기본값 유지
            }
            Pair(3.0f, newIntercept)
        } else {
            // 기본값: 0 m/s = 0 m, 1 m/s = 2 m, 2 m/s = 10 m (이차 방정식)
            Pair(3.0f, -1.0f)
        }
        
        return copy(
            measurements = newMeasurements,
            slope = newSlope,
            intercept = newIntercept,
            requiredCount = maxCount,
            lastUpdated = System.currentTimeMillis()
        )
    }
    
    /**
     * 선형 회귀분석 (최소제곱법)
     * y = ax + b를 계산
     */
    private fun calculateLinearRegression(measurements: List<CalibrationMeasurement>): Pair<Float, Float> {
        val n = measurements.size
        // 기본값: 0 m/s = 0 m, 1 m/s = 2 m, 2 m/s = 10 m (이차 방정식)
        // 주의: 이 함수는 선형 회귀분석을 수행하지만, 이차 방정식 계수를 저장하기 위해 사용됨
        // TODO: 나중에 이차 회귀분석으로 변경 필요
        if (n < 2) return Pair(3.0f, -1.0f)
        
        val sumX = measurements.sumOf { it.maxSpeed.toDouble() }
        val sumY = measurements.sumOf { it.actualDistance.toDouble() }
        val sumXY = measurements.sumOf { (it.maxSpeed * it.actualDistance).toDouble() }
        val sumX2 = measurements.sumOf { (it.maxSpeed * it.maxSpeed).toDouble() }
        
        // 기울기 a = (n * Σ(xy) - Σx * Σy) / (n * Σ(x²) - (Σx)²)
        val numerator = n * sumXY - sumX * sumY
        val denominator = n * sumX2 - sumX * sumX
        
        val slope = if (denominator != 0.0) {
            (numerator / denominator).toFloat()
        } else {
            3.0f  // 분모가 0이면 기본값 (이차 방정식: 3.0)
        }
        
        // 절편 b = (Σy - a * Σx) / n
        val intercept = ((sumY - slope * sumX) / n).toFloat()
        
        return Pair(slope, intercept)
    }
}

/**
 * 개별 캘리브레이션 측정
 */
data class CalibrationMeasurement(
    val maxSpeed: Float,       // 측정된 속도 (m/s)
    val actualDistance: Float,  // 실제 거리 (m)
    val timestamp: Long
)

