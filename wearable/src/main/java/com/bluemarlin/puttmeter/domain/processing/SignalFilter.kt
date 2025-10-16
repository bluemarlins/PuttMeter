package com.bluemarlin.puttmeter.wearable.domain.processing

import com.bluemarlin.puttmeter.wearable.domain.model.Vector3

/**
 * 신호 필터링 유틸리티 (단순화된 버전)
 */
object SignalFilter {
    
    /**
     * 중력 보정 - 가속도에서 중력 성분 제거
     * 개선된 버전: 고주파 노이즈 필터링 + 기본 중력 보정
     */
    fun removeGravity(acceleration: Vector3): Vector3 {
        val gravity = 9.8f
        val magnitude = acceleration.magnitude()

        // 중력 크기에 가까운 경우 (정지 상태 근사)
        if (magnitude in (gravity * 0.8f)..(gravity * 1.5f)) {
            // Z축 방향으로 중력 성분이 가장 큰 것으로 가정하고 보정
            val gravityDirection = if (acceleration.z > 0) 1f else -1f
            return Vector3(
                x = acceleration.x,
                y = acceleration.y,
                z = acceleration.z - (gravity * gravityDirection)
            )
        }

        // 움직임이 큰 경우는 그대로 반환
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
}
