package com.bluemarlin.puttmeter.domain.model

/**
 * 실시간 센서 데이터
 */
data class SensorData(
    val timestamp: Long,                    // System.currentTimeMillis()
    val acceleration: Vector3,              // 가속도 (m/s²)
    val gyroscope: Vector3,                 // 각속도 (rad/s)
    val accelerationMagnitude: Float = acceleration.magnitude(),
    val gyroscopeMagnitude: Float = gyroscope.magnitude()
) {
    companion object {
        val EMPTY = SensorData(
            timestamp = 0L,
            acceleration = Vector3.ZERO,
            gyroscope = Vector3.ZERO
        )
    }
}

/**
 * 센서 상태 정보
 */
data class SensorStatus(
    val isAccelerometerAvailable: Boolean = false,
    val isGyroscopeAvailable: Boolean = false,
    val samplingRate: Int = 0,              // Hz
    val isCollecting: Boolean = false
) {
    val isReady: Boolean
        get() = isAccelerometerAvailable && isGyroscopeAvailable
}

