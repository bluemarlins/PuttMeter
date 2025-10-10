package com.bluemarlin.puttmeter.domain.processing

import com.bluemarlin.puttmeter.domain.model.Vector3
import kotlin.math.sqrt

/**
 * 신호 필터링 유틸리티
 */
object SignalFilter {
    
    /**
     * 이동 평균 필터 (Simple Moving Average)
     * 고주파 노이즈 제거
     */
    fun movingAverage(values: List<Float>, windowSize: Int = 5): List<Float> {
        if (values.size < windowSize) return values
        
        val result = mutableListOf<Float>()
        for (i in values.indices) {
            val start = maxOf(0, i - windowSize / 2)
            val end = minOf(values.size, i + windowSize / 2 + 1)
            val avg = values.subList(start, end).average().toFloat()
            result.add(avg)
        }
        return result
    }
    
    /**
     * 중력 보정 - 가속도에서 중력 성분 제거
     * 간단한 버전: Z축 중력 가정
     */
    fun removeGravity(acceleration: Vector3): Vector3 {
        // 정지 상태에서 Z축 ≈ 9.8 m/s²
        // 움직임이 있을 때는 실제 가속도만 남김
        val gravity = 9.8f
        
        // 전체 크기가 중력보다 작으면 거의 정지 상태
        val magnitude = acceleration.magnitude()
        if (magnitude < gravity * 1.2f) {
            // 중력만 제거
            return Vector3(
                x = acceleration.x,
                y = acceleration.y,
                z = acceleration.z - gravity
            )
        }
        
        // 움직임이 있으면 그대로 반환 (중력보다 움직임 가속도가 큼)
        return acceleration
    }
    
    /**
     * 지수 이동 평균 (Exponential Moving Average)
     * 최근 값에 더 큰 가중치
     */
    fun exponentialMovingAverage(
        current: Float,
        previous: Float,
        alpha: Float = 0.3f
    ): Float {
        return alpha * current + (1 - alpha) * previous
    }
    
    /**
     * 속도 계산 (가속도 적분)
     * dt: 시간 간격 (초)
     */
    fun calculateVelocity(
        accelerations: List<Float>,
        samplingRate: Int = 100
    ): Float {
        if (accelerations.isEmpty()) return 0f
        
        val dt = 1f / samplingRate
        var velocity = 0f
        
        // 사다리꼴 적분
        for (i in 0 until accelerations.size - 1) {
            velocity += (accelerations[i] + accelerations[i + 1]) * 0.5f * dt
        }
        
        return velocity
    }
    
    /**
     * 임펄스 계산 (가속도의 시간 적분)
     */
    fun calculateImpulse(
        accelerations: List<Float>,
        samplingRate: Int = 100,
        mass: Float = 1f // 손목+클럽 상대 질량
    ): Float {
        if (accelerations.isEmpty()) return 0f
        
        val dt = 1f / samplingRate
        var impulse = 0f
        
        for (accel in accelerations) {
            impulse += accel * mass * dt
        }
        
        return impulse
    }
    
    /**
     * 피크 감지 - 최대값과 그 인덱스 찾기
     */
    fun findPeak(values: List<Float>): Pair<Int, Float> {
        if (values.isEmpty()) return Pair(0, 0f)
        
        var maxIndex = 0
        var maxValue = values[0]
        
        for (i in values.indices) {
            if (values[i] > maxValue) {
                maxValue = values[i]
                maxIndex = i
            }
        }
        
        return Pair(maxIndex, maxValue)
    }
    
    /**
     * 크로스오버 감지 - 값이 임계값을 넘는 순간 찾기
     */
    fun findCrossover(
        values: List<Float>,
        threshold: Float,
        crossingUp: Boolean = true
    ): Int? {
        for (i in 0 until values.size - 1) {
            val before = values[i]
            val after = values[i + 1]
            
            if (crossingUp && before < threshold && after >= threshold) {
                return i + 1
            } else if (!crossingUp && before > threshold && after <= threshold) {
                return i + 1
            }
        }
        return null
    }
}

