package com.bluemarlin.puttmeter.wearable.presentation.measurement

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.bluemarlin.puttmeter.wearable.data.sensor.AndroidSensorDataSource

/**
 * MeasurementViewModel Factory
 */
class MeasurementViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {
    
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MeasurementViewModel::class.java)) {
            val sensorDataSource = AndroidSensorDataSource(context.applicationContext)
            return MeasurementViewModel(sensorDataSource, context.applicationContext) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

