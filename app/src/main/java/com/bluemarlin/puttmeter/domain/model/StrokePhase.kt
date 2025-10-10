package com.bluemarlin.puttmeter.domain.model

/**
 * 퍼팅 스트로크의 단계
 */
sealed class StrokePhase {
    /** 정지 상태 - 측정 대기 중 */
    object Idle : StrokePhase()
    
    /** 백스윙 - 테이크백 시작 */
    data class Backswing(
        val startTime: Long,
        val maxAcceleration: Float = 0f
    ) : StrokePhase()
    
    /** 다운스윙 - 임팩트로 향하는 중 */
    data class Downswing(
        val startTime: Long,
        val backswingTime: Long,
        val maxAcceleration: Float = 0f
    ) : StrokePhase()
    
    /** 임팩트 - 볼과 접촉 순간 */
    data class Impact(
        val impactTime: Long,
        val peakAcceleration: Float,
        val impactVelocity: Float
    ) : StrokePhase()
    
    /** 팔로우스루 - 임팩트 후 */
    data class FollowThrough(
        val startTime: Long
    ) : StrokePhase()
}

/**
 * 측정된 퍼팅 스트로크 결과
 */
data class PuttStroke(
    val timestamp: Long,
    val metrics: StrokeMetrics,
    val predictedDistance: Float,      // meters
    val actualDistance: Float? = null, // meters (캘리브레이션용)
    val isValidStroke: Boolean = true  // 연습 스윙 필터링
)

/**
 * 스트로크 측정 메트릭
 */
data class StrokeMetrics(
    val peakAcceleration: Float,        // m/s² - 최대 가속도
    val impactVelocity: Float,          // m/s - 임팩트 순간 속도
    val impulse: Float,                 // N·s (근사) - 충격량
    val swingTime: Long,                // ms - 전체 스윙 시간
    val backswingTime: Long,            // ms - 백스윙 시간
    val tempoRatio: Float,              // 백스윙/다운스윙 비율
    val accelerationSamples: List<Float> = emptyList() // 디버그용
)

/**
 * 스트로크 감지 설정
 */
data class StrokeDetectionConfig(
    val idleThreshold: Float = 0.5f,           // m/s² - 정지 상태 임계값
    val backswingThreshold: Float = 1.5f,      // m/s² - 백스윙 시작 임계값
    val impactThreshold: Float = 8.0f,         // m/s² - 임팩트 최소 임계값
    val minimumSwingTime: Long = 200,          // ms - 최소 스윙 시간
    val maximumSwingTime: Long = 2000,         // ms - 최대 스윙 시간
    val followThroughDuration: Long = 500,     // ms - 팔로우스루 대기 시간
    val practiceSwingThreshold: Float = 5.0f   // m/s² - 연습 스윙 구분 임계값
)

