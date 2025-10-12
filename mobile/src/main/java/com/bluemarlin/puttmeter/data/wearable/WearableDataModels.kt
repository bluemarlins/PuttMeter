package com.bluemarlin.puttmeter.data.wearable

/**
 * Wear OS와 Mobile 간 통신을 위한 데이터 모델들 (Mobile 버전)
 * 단순화된 최대 속도 기반 측정에 맞게 정리됨
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
    val type: String,           // "Simple", "Calibration", "Sensor" 등
    val displayName: String,    // "측정 중", "보정 중" 등
    val color: String,          // "Green", "Gray" 등
    val subtitle: String        // 상태 설명
)

/**
 * 완료된 스트로크 데이터
 */
data class StrokeResultData(
    val timestamp: Long,
    val predictedDistance: Float,      // 예측 거리 (m)
    val metrics: MetricsData,
    val isValidStroke: Boolean = true
) {
    companion object {
        const val PATH = "/stroke_result"
    }
}

/**
 * 메트릭 데이터 (단순화된 버전)
 * 호환성을 위해 모든 필드를 유지하지만, 실제로는 clubSpeed와 swingTime만 사용
 */
data class MetricsData(
    val peakAcceleration: Float,
    val impactVelocity: Float,
    val impulse: Float,
    val swingTime: Long,            // 실제 사용: 스윙 시간 (ms)
    val addressTime: Long,
    val backswingTime: Long,
    val downswingTime: Long,
    val followThroughTime: Long,
    val tempoRatio: Float,
    val backswingDistance: Float,
    val followThroughDistance: Float,
    val clubSpeed: Float,           // 실제 사용: 최대 속도 (m/s)
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
    val averageStability: Float,    // 단순화 버전에서는 1.0 고정
    val averageSmoothness: Float,   // 단순화 버전에서는 1.0 고정
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
