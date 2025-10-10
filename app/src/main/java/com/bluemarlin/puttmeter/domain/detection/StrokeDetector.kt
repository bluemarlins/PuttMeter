package com.bluemarlin.puttmeter.domain.detection

import com.bluemarlin.puttmeter.domain.model.*
import com.bluemarlin.puttmeter.domain.processing.SignalFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 스트로크 감지 엔진
 * 센서 데이터를 분석하여 퍼팅 스트로크를 감지하고 측정
 */
class StrokeDetector(
    private val config: StrokeDetectionConfig = StrokeDetectionConfig()
) {
    private val _currentPhase = MutableStateFlow<StrokePhase>(StrokePhase.Idle)
    val currentPhase: StateFlow<StrokePhase> = _currentPhase.asStateFlow()
    
    private val _detectedStroke = MutableStateFlow<PuttStroke?>(null)
    val detectedStroke: StateFlow<PuttStroke?> = _detectedStroke.asStateFlow()
    
    // 센서 데이터 버퍼 (최근 2초분)
    private val sensorBuffer = mutableListOf<SensorData>()
    private val maxBufferSize = 200 // 2초 @ 100Hz
    
    // 스트로크 시작 시점
    private var strokeStartTime = 0L
    private var addressStartTime = 0L
    private var backswingStartTime = 0L
    private var downswingStartTime = 0L
    private var impactStartTime = 0L
    
    // 이전 평활화된 가속도
    private var previousSmoothedMagnitude = 0f
    
    // 메트릭 계산을 위한 버퍼
    private val gyroscopeBuffer = mutableListOf<Float>()
    private val phaseTransitions = mutableListOf<Long>()
    
    /**
     * 센서 데이터 처리
     */
    fun processSensorData(sensorData: SensorData) {
        // 버퍼에 추가
        sensorBuffer.add(sensorData)
        if (sensorBuffer.size > maxBufferSize) {
            sensorBuffer.removeAt(0)
        }
        
        // 가속도 크기 계산 (중력 보정 후)
        val linearAccel = SignalFilter.removeGravity(sensorData.acceleration)
        val magnitude = linearAccel.magnitude()
        
        // 자이로스코프 크기도 버퍼에 추가
        val gyroMagnitude = sensorData.gyroscope.magnitude()
        gyroscopeBuffer.add(gyroMagnitude)
        if (gyroscopeBuffer.size > maxBufferSize) {
            gyroscopeBuffer.removeAt(0)
        }
        
        // 이동 평균으로 평활화
        val smoothedMagnitude = SignalFilter.exponentialMovingAverage(
            current = magnitude,
            previous = previousSmoothedMagnitude,
            alpha = 0.2f
        )
        previousSmoothedMagnitude = smoothedMagnitude
        
        // 현재 상태에 따라 처리
        when (val phase = _currentPhase.value) {
            is StrokePhase.Idle -> {
                checkForAddress(sensorData.timestamp, smoothedMagnitude)
            }
            
            is StrokePhase.Address -> {
                checkForBackswing(sensorData.timestamp, smoothedMagnitude, phase)
            }
            
            is StrokePhase.Backswing -> {
                checkForDownswing(sensorData.timestamp, smoothedMagnitude, phase)
            }
            
            is StrokePhase.Downswing -> {
                checkForImpact(sensorData.timestamp, smoothedMagnitude, phase)
            }
            
            is StrokePhase.Impact -> {
                checkForFollowThrough(sensorData.timestamp, smoothedMagnitude, phase)
            }
            
            is StrokePhase.FollowThrough -> {
                checkForIdle(sensorData.timestamp, smoothedMagnitude, phase)
            }
        }
    }
    
    /**
     * 어드레스 단계 감지 (준비 자세)
     */
    private fun checkForAddress(timestamp: Long, magnitude: Float) {
        // 상대적으로 안정된 상태 감지 (완화된 조건)
        if (magnitude < config.addressThreshold) {
            if (addressStartTime == 0L) {
                addressStartTime = timestamp
                strokeStartTime = timestamp
                phaseTransitions.clear()
                phaseTransitions.add(timestamp)
            }
            
            val addressDuration = timestamp - addressStartTime
            val stabilityScore = calculateStabilityScore()
            
            _currentPhase.value = StrokePhase.Address(
                startTime = addressStartTime,
                stabilityScore = stabilityScore,
                duration = addressDuration
            )
            
            // 어드레스 시간이 너무 길면 리셋
            if (addressDuration > config.addressMaxDuration) {
                resetToIdle()
            }
        } else {
            // 큰 움직임이 감지되면 어드레스 리셋 (임계값 완화)
            if (magnitude > config.addressThreshold * 2.0f) {
                addressStartTime = 0L
            }
            // 작은 움직임은 허용 (시계 착용 시 미세한 움직임 고려)
        }
    }
    
    /**
     * 백스윙 시작 감지
     */
    private fun checkForBackswing(timestamp: Long, magnitude: Float, address: StrokePhase.Address) {
        // 어드레스 최소 시간 체크
        val addressDuration = timestamp - address.startTime
        
        if (magnitude > config.backswingThreshold && addressDuration >= config.addressMinDuration) {
            backswingStartTime = timestamp
            phaseTransitions.add(timestamp)
            
            _currentPhase.value = StrokePhase.Backswing(
                startTime = timestamp,
                maxAcceleration = magnitude
            )
        }
    }
    
    /**
     * 다운스윙 시작 감지 (방향 전환)
     */
    private fun checkForDownswing(
        timestamp: Long,
        magnitude: Float,
        backswing: StrokePhase.Backswing
    ) {
        val elapsedTime = timestamp - backswing.startTime
        
        // 최대 가속도 업데이트
        val newMax = maxOf(backswing.maxAcceleration, magnitude)
        
        // 가속도가 감소하기 시작하면 다운스윙 시작
        if (magnitude < backswing.maxAcceleration * 0.7f && elapsedTime > 100) {
            downswingStartTime = timestamp
            phaseTransitions.add(timestamp)
            val backswingDuration = timestamp - backswingStartTime
            
            // 백스윙 거리와 템포 계산
            val backswingDistance = calculateBackswingDistance()
            val tempo = calculateTempo(backswingDuration)
            
            _currentPhase.value = StrokePhase.Downswing(
                startTime = timestamp,
                backswingTime = backswingDuration,
                maxAcceleration = magnitude,
                accelerationRate = 0f,
                clubSpeed = 0f
            )
        } else {
            // 최대값과 추가 메트릭 업데이트
            val accelerationRate = calculateAccelerationRate()
            val clubSpeed = calculateClubSpeed()
            
            _currentPhase.value = backswing.copy(
                maxAcceleration = newMax,
                backswingDistance = calculateBackswingDistance(),
                tempo = calculateTempo(elapsedTime)
            )
        }
        
        // 타임아웃 체크
        if (elapsedTime > config.maximumSwingTime) {
            resetToIdle()
        }
    }
    
    /**
     * 임팩트 감지 (다운스윙에서 급격한 가속도 증가)
     */
    private fun checkForImpact(
        timestamp: Long,
        magnitude: Float,
        downswing: StrokePhase.Downswing
    ) {
        val elapsedTime = timestamp - downswing.startTime
        
        // 최대 가속도 업데이트
        val newMax = maxOf(downswing.maxAcceleration, magnitude)
        
        // 임팩트 조건: 높은 가속도 피크
        if (magnitude > config.impactThreshold && 
            magnitude > downswing.maxAcceleration * 1.2f) {
            
            impactStartTime = timestamp
            phaseTransitions.add(timestamp)
            
            // 임팩트 순간의 속도 계산
            val impactWindow = sensorBuffer.takeLast(20) // 최근 200ms
            val accelerations = impactWindow.map { 
                SignalFilter.removeGravity(it.acceleration).magnitude() 
            }
            val velocity = SignalFilter.calculateVelocity(accelerations, 100)
            
            // 임팩트 힘과 접촉 시간 계산
            val impactForce = calculateImpactForce(magnitude)
            val contactDuration = calculateContactDuration()
            
            _currentPhase.value = StrokePhase.Impact(
                impactTime = timestamp,
                peakAcceleration = magnitude,
                impactVelocity = velocity,
                impactForce = impactForce,
                contactDuration = contactDuration
            )
        } else {
            // 다운스윙 메트릭 업데이트
            val accelerationRate = calculateAccelerationRate()
            val clubSpeed = calculateClubSpeed()
            
            _currentPhase.value = downswing.copy(
                maxAcceleration = newMax,
                accelerationRate = accelerationRate,
                clubSpeed = clubSpeed
            )
        }
        
        // 타임아웃 체크
        if (elapsedTime > config.maximumSwingTime) {
            resetToIdle()
        }
    }
    
    /**
     * 팔로우스루 시작
     */
    private fun checkForFollowThrough(
        timestamp: Long,
        magnitude: Float,
        impact: StrokePhase.Impact
    ) {
        // 임팩트 직후 가속도 감소 확인
        if (magnitude < impact.peakAcceleration * 0.5f) {
            phaseTransitions.add(timestamp)
            
            // 팔로우스루 거리와 안정성 계산
            val followThroughDistance = calculateFollowThroughDistance()
            val finishStability = calculateFinishStability()
            
            _currentPhase.value = StrokePhase.FollowThrough(
                startTime = timestamp,
                followThroughDistance = followThroughDistance,
                finishStability = finishStability
            )
            
            // 스트로크 완료 - 메트릭 계산 및 거리 예측
            completeStroke(impact)
        }
    }
    
    /**
     * 정지 상태로 복귀
     */
    private fun checkForIdle(
        timestamp: Long,
        magnitude: Float,
        followThrough: StrokePhase.FollowThrough
    ) {
        val elapsedTime = timestamp - followThrough.startTime
        
        // 팔로우스루 후 정지 상태 확인
        if (magnitude < config.idleThreshold && elapsedTime > config.followThroughDuration) {
            resetToIdle()
        }
    }
    
    /**
     * 스트로크 완료 처리
     */
    private fun completeStroke(impact: StrokePhase.Impact) {
        val totalSwingTime = impact.impactTime - strokeStartTime
        val addressDuration = if (phaseTransitions.size > 1) phaseTransitions[1] - phaseTransitions[0] else 0L
        val backswingDuration = downswingStartTime - backswingStartTime
        val downswingDuration = impact.impactTime - downswingStartTime
        val followThroughDuration = System.currentTimeMillis() - impact.impactTime
        
        // 유효한 스트로크인지 확인 (연습 스윙 필터링)
        val isValidStroke = impact.peakAcceleration >= config.practiceSwingThreshold &&
                            totalSwingTime >= config.minimumSwingTime &&
                            totalSwingTime <= config.maximumSwingTime
        
        // 임팩트 구간 데이터 추출
        val impactWindow = sensorBuffer.takeLast(30) // 최근 300ms
        val accelerations = impactWindow.map { 
            SignalFilter.removeGravity(it.acceleration).magnitude() 
        }
        
        // 임펄스 계산
        val impulse = SignalFilter.calculateImpulse(accelerations, 100)
        
        // 템포 비율 계산
        val tempoRatio = if (downswingDuration > 0) {
            backswingDuration.toFloat() / downswingDuration.toFloat()
        } else {
            1f
        }
        
        // 향상된 메트릭 생성
        val metrics = StrokeMetrics(
            peakAcceleration = impact.peakAcceleration,
            impactVelocity = impact.impactVelocity,
            impulse = impulse,
            swingTime = totalSwingTime,
            addressTime = addressDuration,
            backswingTime = backswingDuration,
            downswingTime = downswingDuration,
            followThroughTime = followThroughDuration,
            tempoRatio = tempoRatio,
            backswingDistance = calculateBackswingDistance(),
            followThroughDistance = calculateFollowThroughDistance(),
            clubSpeed = calculateClubSpeed(),
            impactForce = impact.impactForce,
            addressStability = calculateStabilityScore(),
            finishStability = calculateFinishStability(),
            smoothness = calculateSmoothness(),
            accelerationSamples = accelerations,
            gyroscopeSamples = gyroscopeBuffer.takeLast(30),
            phaseTransitions = phaseTransitions.toList()
        )
        
        // 거리 예측 (간단한 선형 모델 - 캘리브레이션 전)
        val predictedDistance = predictDistance(metrics)
        
        // 스트로크 객체 생성
        val stroke = PuttStroke(
            timestamp = impact.impactTime,
            metrics = metrics,
            predictedDistance = predictedDistance,
            isValidStroke = isValidStroke
        )
        
        // 유효한 스트로크만 발행
        if (isValidStroke) {
            _detectedStroke.value = stroke
        }
    }
    
    /**
     * 거리 예측 (향상된 모델 - 다중 팩터 고려)
     */
    private fun predictDistance(metrics: StrokeMetrics): Float {
        // 다중 팩터 모델: 속도, 힘, 부드러움, 템포를 고려
        val velocityFactor = metrics.impactVelocity * 1.8f
        val forceFactor = metrics.impactForce * 0.1f
        val smoothnessFactor = metrics.smoothness * 0.5f
        val tempoFactor = if (metrics.tempoRatio > 2.5f) 1.2f else 1.0f // 좋은 템포 보너스
        
        val baseDistance = velocityFactor + forceFactor + smoothnessFactor
        val adjustedDistance = baseDistance * tempoFactor
        
        // 최소/최대 거리 제한 (퍼팅 범위)
        return adjustedDistance.coerceIn(0.1f, 15.0f)
    }
    
    /**
     * 정지 상태로 리셋
     */
    private fun resetToIdle() {
        _currentPhase.value = StrokePhase.Idle
        previousSmoothedMagnitude = 0f
        addressStartTime = 0L
        backswingStartTime = 0L
        downswingStartTime = 0L
        impactStartTime = 0L
        phaseTransitions.clear()
    }
    
    /**
     * 감지된 스트로크 초기화
     */
    fun clearDetectedStroke() {
        _detectedStroke.value = null
    }
    
    /**
     * 수동 리셋
     */
    fun reset() {
        resetToIdle()
        sensorBuffer.clear()
        gyroscopeBuffer.clear()
        _detectedStroke.value = null
    }
    
    // ===== 메트릭 계산 함수들 =====
    
    /**
     * 안정성 점수 계산 (0-1, 높을수록 안정)
     */
    private fun calculateStabilityScore(): Float {
        if (sensorBuffer.size < config.stabilityWindowSize) return 0f
        
        val recentSamples = sensorBuffer.takeLast(config.stabilityWindowSize)
        val accelerations = recentSamples.map { 
            SignalFilter.removeGravity(it.acceleration).magnitude() 
        }
        
        val mean = accelerations.average().toFloat()
        val variance = accelerations.map { (it - mean) * (it - mean) }.average().toFloat()
        val stability = 1f / (1f + variance) // 분산이 낮을수록 안정성 높음
        
        return stability.coerceIn(0f, 1f)
    }
    
    /**
     * 백스윙 거리 추정 (상대값)
     */
    private fun calculateBackswingDistance(): Float {
        if (sensorBuffer.size < 20) return 0f
        
        val backswingWindow = sensorBuffer.takeLast(20)
        val accelerations = backswingWindow.map { 
            SignalFilter.removeGravity(it.acceleration).magnitude() 
        }
        
        // 가속도 적분으로 거리 근사 (상대값)
        return accelerations.sum() * 0.01f // 스케일링 팩터
    }
    
    /**
     * 팔로우스루 거리 추정
     */
    private fun calculateFollowThroughDistance(): Float {
        return calculateBackswingDistance() * 0.8f // 일반적으로 백스윙보다 짧음
    }
    
    /**
     * 클럽 스피드 추정 (m/s)
     */
    private fun calculateClubSpeed(): Float {
        if (sensorBuffer.size < 10) return 0f
        
        val recentSamples = sensorBuffer.takeLast(10)
        val velocities = recentSamples.map { 
            SignalFilter.removeGravity(it.acceleration).magnitude() * 0.01f // 가속도를 속도로 근사
        }
        
        return velocities.maxOrNull() ?: 0f
    }
    
    /**
     * 가속도 증가율 계산
     */
    private fun calculateAccelerationRate(): Float {
        if (sensorBuffer.size < 5) return 0f
        
        val recent = sensorBuffer.takeLast(5)
        val accelerations = recent.map { 
            SignalFilter.removeGravity(it.acceleration).magnitude() 
        }
        
        if (accelerations.size < 2) return 0f
        
        val deltaAccel = accelerations.last() - accelerations.first()
        val deltaTime = (recent.last().timestamp - recent.first().timestamp) / 1000f
        
        return if (deltaTime > 0) deltaAccel / deltaTime else 0f
    }
    
    /**
     * 임팩트 힘 추정 (N)
     */
    private fun calculateImpactForce(peakAcceleration: Float): Float {
        // 간단한 추정: F = ma (클럽 헤드 질량을 약 300g으로 가정)
        val clubMass = 0.3f // kg
        return peakAcceleration * clubMass
    }
    
    /**
     * 접촉 시간 계산 (ms)
     */
    private fun calculateContactDuration(): Float {
        // 일반적인 골프 임팩트 접촉 시간은 0.4-0.5ms
        return 0.45f
    }
    
    /**
     * 피니시 안정성 계산
     */
    private fun calculateFinishStability(): Float {
        return calculateStabilityScore() // 현재는 동일한 로직 사용
    }
    
    /**
     * 스윙 부드러움 계산 (0-1, 높을수록 부드러움)
     */
    private fun calculateSmoothness(): Float {
        if (sensorBuffer.size < config.smoothnessWindowSize) return 0f
        
        val recentSamples = sensorBuffer.takeLast(config.smoothnessWindowSize)
        val accelerations = recentSamples.map { 
            SignalFilter.removeGravity(it.acceleration).magnitude() 
        }
        
        // 가속도 변화의 표준편차로 부드러움 측정
        val changes = accelerations.zipWithNext { a, b -> kotlin.math.abs(b - a) }
        val meanChange = changes.average().toFloat()
        val smoothness = 1f / (1f + meanChange) // 변화가 적을수록 부드러움
        
        return smoothness.coerceIn(0f, 1f)
    }
    
    /**
     * 템포 계산
     */
    private fun calculateTempo(duration: Long): Float {
        // 템포를 BPM으로 계산 (60초 / 스윙시간)
        return if (duration > 0) 60000f / duration else 0f
    }
}

