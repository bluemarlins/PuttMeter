package com.bluemarlin.puttmeter.wearable.data.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.bluemarlin.puttmeter.wearable.domain.model.SensorData
import com.bluemarlin.puttmeter.wearable.domain.model.SensorStatus
import com.bluemarlin.puttmeter.wearable.domain.model.Vector3
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * 센서 데이터 수집 인터페이스
 */
interface SensorDataSource {
    fun getSensorDataStream(samplingRate: Int = SensorManager.SENSOR_DELAY_GAME): Flow<SensorData>
    fun getSensorStatus(): SensorStatus
}

/**
 * Android SensorManager 기반 구현
 */
class AndroidSensorDataSource(
    private val context: Context
) : SensorDataSource {

    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val accelerometer: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val gyroscope: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    override fun getSensorStatus(): SensorStatus {
        return SensorStatus(
            isAccelerometerAvailable = accelerometer != null,
            isGyroscopeAvailable = gyroscope != null,
            samplingRate = 0,
            isCollecting = false
        )
    }

    override fun getSensorDataStream(samplingRate: Int): Flow<SensorData> = callbackFlow {
        // 최신 센서 값을 저장
        var latestAcceleration = Vector3.ZERO
        var latestGyroscope = Vector3.ZERO
        var lastTimestamp = 0L

        fun emitSensorData() {
            val now = System.currentTimeMillis()
            // 최소 10ms 간격으로 emit (100Hz)
            if (now - lastTimestamp >= 10) {
                lastTimestamp = now
                trySend(
                    SensorData(
                        timestamp = now,
                        acceleration = latestAcceleration,
                        gyroscope = latestGyroscope
                    )
                )
            }
        }

        val accelerometerListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event?.let { e ->
                    latestAcceleration = Vector3(
                        x = e.values[0],
                        y = e.values[1],
                        z = e.values[2]
                    )
                    emitSensorData()
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        val gyroscopeListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event?.let { e ->
                    latestGyroscope = Vector3(
                        x = e.values[0],
                        y = e.values[1],
                        z = e.values[2]
                    )
                    emitSensorData()
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        // 센서 리스너 등록
        accelerometer?.let {
            sensorManager.registerListener(
                accelerometerListener,
                it,
                samplingRate
            )
        }

        gyroscope?.let {
            sensorManager.registerListener(
                gyroscopeListener,
                it,
                samplingRate
            )
        }

        // Flow가 취소되면 센서 리스너 해제
        awaitClose {
            sensorManager.unregisterListener(accelerometerListener)
            sensorManager.unregisterListener(gyroscopeListener)
        }
    }
}

