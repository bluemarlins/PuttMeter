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
    private var backswingStartTime = 0L
    private var downswingStartTime = 0L
    
    // 이전 평활화된 가속도
    private var previousSmoothedMagnitude = 0f
    
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
                checkForBackswing(sensorData.timestamp, smoothedMagnitude)
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
     * 백스윙 시작 감지
     */
    private fun checkForBackswing(timestamp: Long, magnitude: Float) {
        if (magnitude > config.backswingThreshold) {
            strokeStartTime = timestamp
            backswingStartTime = timestamp
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
            val backswingDuration = timestamp - backswingStartTime
            
            _currentPhase.value = StrokePhase.Downswing(
                startTime = timestamp,
                backswingTime = backswingDuration,
                maxAcceleration = magnitude
            )
        } else {
            // 최대값 업데이트
            _currentPhase.value = backswing.copy(maxAcceleration = newMax)
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
            
            // 임팩트 순간의 속도 계산
            val impactWindow = sensorBuffer.takeLast(20) // 최근 200ms
            val accelerations = impactWindow.map { 
                SignalFilter.removeGravity(it.acceleration).magnitude() 
            }
            val velocity = SignalFilter.calculateVelocity(accelerations, 100)
            
            _currentPhase.value = StrokePhase.Impact(
                impactTime = timestamp,
                peakAcceleration = magnitude,
                impactVelocity = velocity
            )
        } else {
            _currentPhase.value = downswing.copy(maxAcceleration = newMax)
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
            _currentPhase.value = StrokePhase.FollowThrough(startTime = timestamp)
            
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
        val backswingDuration = downswingStartTime - backswingStartTime
        val downswingDuration = impact.impactTime - downswingStartTime
        
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
        
        // 메트릭 생성
        val metrics = StrokeMetrics(
            peakAcceleration = impact.peakAcceleration,
            impactVelocity = impact.impactVelocity,
            impulse = impulse,
            swingTime = totalSwingTime,
            backswingTime = backswingDuration,
            tempoRatio = tempoRatio,
            accelerationSamples = accelerations
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
     * 거리 예측 (임시 - 캘리브레이션 없이 간단한 모델)
     */
    private fun predictDistance(metrics: StrokeMetrics): Float {
        // 모델 A: 임팩트 속도 기반
        // 캘리브레이션 전 기본 계수 (평균적인 값)
        val k = 2.0f
        return k * metrics.impactVelocity
    }
    
    /**
     * 정지 상태로 리셋
     */
    private fun resetToIdle() {
        _currentPhase.value = StrokePhase.Idle
        previousSmoothedMagnitude = 0f
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
        _detectedStroke.value = null
    }
}

