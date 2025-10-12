package com.bluemarlin.puttmeter.wearable.domain.processing

import com.bluemarlin.puttmeter.wearable.domain.model.Vector3

/**
 * 신호 필터링 유틸리티 (단순화된 버전)
 */
object SignalFilter {
    
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
}
