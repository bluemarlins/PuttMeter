package com.bluemarlin.puttmeter.wearable.presentation.debug

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.bluemarlin.puttmeter.wearable.data.sensor.AndroidSensorDataSource

/**
 * SensorDebugViewModel Factory
 */
class SensorDebugViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {
    
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SensorDebugViewModel::class.java)) {
            val sensorDataSource = AndroidSensorDataSource(context.applicationContext)
            return SensorDebugViewModel(sensorDataSource) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

