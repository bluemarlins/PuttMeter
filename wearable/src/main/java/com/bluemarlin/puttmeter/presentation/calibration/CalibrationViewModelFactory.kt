package com.bluemarlin.puttmeter.wearable.presentation.calibration

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.bluemarlin.puttmeter.wearable.data.sensor.AndroidSensorDataSource

class CalibrationViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {
    
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CalibrationViewModel::class.java)) {
            return CalibrationViewModel(
                sensorDataSource = AndroidSensorDataSource(context),
                context = context
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

