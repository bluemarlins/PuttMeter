package com.bluemarlin.puttmeter.domain.model

/**
 * 퍼팅 스트로크의 5단계
 */
sealed class StrokePhase {
    /** 정지 상태 - 측정 대기 중 */
    object Idle : StrokePhase()
    
    /** 어드레스 - 퍼터를 잡고 공을 치기 위해 준비하는 단계 */
    data class Address(
        val startTime: Long,
        val stabilityScore: Float = 0f,  // 안정성 점수 (0-1)
        val duration: Long = 0L
    ) : StrokePhase()
    
    /** 백스윙 - 퍼터를 뒤로 빼는 동작, 스트로크 에너지 축적 */
    data class Backswing(
        val startTime: Long,
        val maxAcceleration: Float = 0f,
        val backswingDistance: Float = 0f,  // 백스윙 거리 (근사)
        val tempo: Float = 0f  // 백스윙 템포
    ) : StrokePhase()
    
    /** 다운스윙 - 퍼터가 공을 향해 내려오는 동작 */
    data class Downswing(
        val startTime: Long,
        val backswingTime: Long,
        val maxAcceleration: Float = 0f,
        val accelerationRate: Float = 0f,  // 가속도 증가율
        val clubSpeed: Float = 0f  // 클럽 스피드 (근사)
    ) : StrokePhase()
    
    /** 임팩트 - 퍼터가 공에 맞는 순간 */
    data class Impact(
        val impactTime: Long,
        val peakAcceleration: Float,
        val impactVelocity: Float,
        val impactForce: Float,  // 임팩트 힘 (근사)
        val contactDuration: Float  // 접촉 시간 (ms)
    ) : StrokePhase()
    
    /** 팔로우스루 & 피니시 - 임팩트 후 퍼터가 목표 방향으로 자연스럽게 움직이며 마무리 */
    data class FollowThrough(
        val startTime: Long,
        val followThroughDistance: Float = 0f,  // 팔로우스루 거리
        val finishStability: Float = 0f  // 피니시 안정성
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
    // 기본 메트릭
    val peakAcceleration: Float,        // m/s² - 최대 가속도
    val impactVelocity: Float,          // m/s - 임팩트 순간 속도
    val impulse: Float,                 // N·s (근사) - 충격량
    
    // 시간 메트릭
    val swingTime: Long,                // ms - 전체 스윙 시간
    val addressTime: Long,              // ms - 어드레스 시간
    val backswingTime: Long,            // ms - 백스윙 시간
    val downswingTime: Long,            // ms - 다운스윙 시간
    val followThroughTime: Long,        // ms - 팔로우스루 시간
    val tempoRatio: Float,              // 백스윙/다운스윙 비율
    
    // 거리 및 움직임 메트릭
    val backswingDistance: Float,       // 백스윙 거리 추정 (상대값)
    val followThroughDistance: Float,   // 팔로우스루 거리 추정 (상대값)
    val clubSpeed: Float,               // 클럽 헤드 스피드 추정 (m/s)
    
    // 힘과 안정성 메트릭
    val impactForce: Float,             // 임팩트 힘 추정 (N)
    val addressStability: Float,        // 어드레스 안정성 (0-1)
    val finishStability: Float,         // 피니시 안정성 (0-1)
    val smoothness: Float,              // 스윙 부드러움 (0-1)
    
    // 디버그 및 분석용
    val accelerationSamples: List<Float> = emptyList(), // 가속도 샘플
    val gyroscopeSamples: List<Float> = emptyList(),    // 자이로스코프 샘플
    val phaseTransitions: List<Long> = emptyList()      // 각 단계 전환 시점
)

/**
 * 스트로크 감지 설정
 */
data class StrokeDetectionConfig(
    val idleThreshold: Float = 0.5f,           // m/s² - 정지 상태 임계값
    val addressThreshold: Float = 1.0f,        // m/s² - 어드레스 상태 임계값 (완화)
    val addressMinDuration: Long = 500,        // ms - 최소 어드레스 시간 (단축)
    val addressMaxDuration: Long = 10000,      // ms - 최대 어드레스 시간
    val backswingThreshold: Float = 1.5f,      // m/s² - 백스윙 시작 임계값
    val impactThreshold: Float = 8.0f,         // m/s² - 임팩트 최소 임계값
    val minimumSwingTime: Long = 200,          // ms - 최소 스윙 시간
    val maximumSwingTime: Long = 2000,         // ms - 최대 스윙 시간
    val followThroughDuration: Long = 500,     // ms - 팔로우스루 대기 시간
    val practiceSwingThreshold: Float = 5.0f,  // m/s² - 연습 스윙 구분 임계값
    val stabilityWindowSize: Int = 50,         // 안정성 계산을 위한 윈도우 크기 (샘플 수)
    val smoothnessWindowSize: Int = 30         // 부드러움 계산을 위한 윈도우 크기 (샘플 수)
)

