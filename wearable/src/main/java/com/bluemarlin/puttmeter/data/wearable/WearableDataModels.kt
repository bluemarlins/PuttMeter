package com.bluemarlin.puttmeter.wearable.data.wearable

/**
 * Wear OS와 Mobile 간 통신을 위한 데이터 모델들
 */

/**
 * 실시간 퍼팅 상태 데이터
 */
data class PuttingStateData(
    val isActive: Boolean,
    val currentPhase: PhaseData,
    val sessionCount: Int,
    val averageDistance: Float,
    val currentMaxSpeed: Float = 0f,  // 현재 측정 중인 최대 속도
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        const val PATH = "/putting_state"
    }
}

/**
 * 단계 정보 (단순화된 버전)
 */
data class PhaseData(
    val type: String,
    val displayName: String,
    val color: String,
    val subtitle: String
)

/**
 * 완료된 스트로크 데이터 (단순화된 버전)
 */
data class StrokeResultData(
    val timestamp: Long,
    val predictedDistance: Float,
    val metrics: MetricsData,
    val isValidStroke: Boolean
) {
    companion object {
        const val PATH = "/stroke_result"
    }
}

/**
 * 메트릭 데이터 (단순화된 버전)
 */
data class MetricsData(
    val peakAcceleration: Float,
    val impactVelocity: Float,
    val impulse: Float,
    val swingTime: Long,
    val addressTime: Long,
    val backswingTime: Long,
    val downswingTime: Long,
    val followThroughTime: Long,
    val tempoRatio: Float,
    val backswingDistance: Float,
    val followThroughDistance: Float,
    val clubSpeed: Float,          // 실제로는 maxSpeed를 저장
    val impactForce: Float,
    val addressStability: Float,
    val finishStability: Float,
    val smoothness: Float
)

/**
 * 세션 통계 데이터
 */
data class SessionStatsData(
    val totalStrokes: Int,
    val averageDistance: Float,
    val bestDistance: Float,
    val averageStability: Float,
    val averageSmoothness: Float,
    val sessionStartTime: Long,
    val lastUpdateTime: Long = System.currentTimeMillis()
) {
    companion object {
        const val PATH = "/session_stats"
    }
}

/**
 * 앱 상태 데이터
 */
data class AppStateData(
    val isWearAppActive: Boolean,
    val isMeasuring: Boolean,
    val lastHeartbeat: Long = System.currentTimeMillis()
) {
    companion object {
        const val PATH = "/app_state"
    }
}
