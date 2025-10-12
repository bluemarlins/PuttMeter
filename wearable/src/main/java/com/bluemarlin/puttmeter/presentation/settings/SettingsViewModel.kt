package com.bluemarlin.puttmeter.wearable.presentation.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 설정 UI 상태
 */
data class SettingsUiState(
    val calibrationCount: Int = 5,         // 거리 보정 횟수 (기본값 5)
    val calibrationFactor: Float = 1.0f,   // 보정 계수
    val lastUpdated: Long = 0L
)

/**
 * 설정 ViewModel
 */
class SettingsViewModel(
    private val context: Context
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("putt_meter_prefs", Context.MODE_PRIVATE)
    
    companion object {
        const val PREF_CALIBRATION_COUNT = "calibration_count"
        const val PREF_CALIBRATION_FACTOR = "calibration_factor"
        const val PREF_LAST_UPDATED = "last_updated"
        
        const val DEFAULT_CALIBRATION_COUNT = 5
        const val DEFAULT_CALIBRATION_FACTOR = 1.0f
    }
    
    init {
        loadSettings()
    }
    
    /**
     * 설정 로드
     */
    private fun loadSettings() {
        val count = sharedPreferences.getInt(PREF_CALIBRATION_COUNT, DEFAULT_CALIBRATION_COUNT)
        val factor = sharedPreferences.getFloat(PREF_CALIBRATION_FACTOR, DEFAULT_CALIBRATION_FACTOR)
        val lastUpdated = sharedPreferences.getLong(PREF_LAST_UPDATED, 0L)
        
        _uiState.value = SettingsUiState(
            calibrationCount = count,
            calibrationFactor = factor,
            lastUpdated = lastUpdated
        )
    }
    
    /**
     * 보정 횟수 변경
     */
    fun setCalibrationCount(count: Int) {
        val validCount = count.coerceIn(0, 10)
        _uiState.value = _uiState.value.copy(
            calibrationCount = validCount,
            lastUpdated = System.currentTimeMillis()
        )
        saveSettings()
    }
    
    /**
     * 보정 계수 변경
     */
    fun setCalibrationFactor(factor: Float) {
        val validFactor = factor.coerceIn(0.1f, 10.0f)
        _uiState.value = _uiState.value.copy(
            calibrationFactor = validFactor,
            lastUpdated = System.currentTimeMillis()
        )
        saveSettings()
    }
    
    /**
     * 설정 저장
     */
    private fun saveSettings() {
        sharedPreferences.edit().apply {
            putInt(PREF_CALIBRATION_COUNT, _uiState.value.calibrationCount)
            putFloat(PREF_CALIBRATION_FACTOR, _uiState.value.calibrationFactor)
            putLong(PREF_LAST_UPDATED, _uiState.value.lastUpdated)
            apply()
        }
    }
    
    /**
     * 설정 초기화
     */
    fun resetSettings() {
        _uiState.value = SettingsUiState(
            calibrationCount = DEFAULT_CALIBRATION_COUNT,
            calibrationFactor = DEFAULT_CALIBRATION_FACTOR,
            lastUpdated = System.currentTimeMillis()
        )
        saveSettings()
    }
}

