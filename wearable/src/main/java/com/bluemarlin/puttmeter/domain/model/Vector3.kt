package com.bluemarlin.puttmeter.wearable.domain.model

import kotlin.math.sqrt

/**
 * 3D 벡터 (x, y, z)
 * 센서 데이터의 기본 단위
 */
data class Vector3(
    val x: Float,
    val y: Float,
    val z: Float
) {
    /**
     * 벡터의 크기 (magnitude)
     */
    fun magnitude(): Float = sqrt(x * x + y * y + z * z)

    /**
     * 정규화된 벡터
     */
    fun normalized(): Vector3 {
        val mag = magnitude()
        return if (mag > 0f) {
            Vector3(x / mag, y / mag, z / mag)
        } else {
            this
        }
    }

    companion object {
        val ZERO = Vector3(0f, 0f, 0f)
    }
}

