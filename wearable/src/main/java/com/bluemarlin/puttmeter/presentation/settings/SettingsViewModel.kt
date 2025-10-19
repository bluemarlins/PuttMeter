package com.bluemarlin.puttmeter.wearable.presentation.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import com.bluemarlin.puttmeter.wearable.domain.detection.SpeedAlgorithm
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 설정 UI 상태
 */
data class SettingsUiState(
    val calibrationCount: Int = 5,         // 거리 보정 횟수 (기본값 5)
    val calibrationFactor: Float = 1.0f,   // 보정 계수
    val speedAlgorithm: SpeedAlgorithm = SpeedAlgorithm.SENSOR_FUSION,  // 속도 측정 알고리즘
    val countdownDuration: Int = 3,        // 카운트다운 시간 (0, 1, 2, 3초 중 선택, 기본값 3)
    val idleSensitivity: Int = 3,          // 정지 감도 (1~5, 기본값 3)
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
        const val PREF_SPEED_ALGORITHM = "speed_algorithm"
        const val PREF_COUNTDOWN_DURATION = "countdown_duration"
        const val PREF_IDLE_SENSITIVITY = "idle_sensitivity"
        const val PREF_LAST_UPDATED = "last_updated"
        
        const val DEFAULT_CALIBRATION_COUNT = 5
        const val DEFAULT_CALIBRATION_FACTOR = 1.0f
        val DEFAULT_SPEED_ALGORITHM = SpeedAlgorithm.SENSOR_FUSION
        const val DEFAULT_COUNTDOWN_DURATION = 3
        const val DEFAULT_IDLE_SENSITIVITY = 3
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
        val algorithmName = sharedPreferences.getString(PREF_SPEED_ALGORITHM, DEFAULT_SPEED_ALGORITHM.name)
        val algorithm = try {
            SpeedAlgorithm.valueOf(algorithmName ?: DEFAULT_SPEED_ALGORITHM.name)
        } catch (e: Exception) {
            DEFAULT_SPEED_ALGORITHM
        }
        val countdownDuration = sharedPreferences.getInt(PREF_COUNTDOWN_DURATION, DEFAULT_COUNTDOWN_DURATION)
        val idleSensitivity = sharedPreferences.getInt(PREF_IDLE_SENSITIVITY, DEFAULT_IDLE_SENSITIVITY)
        val lastUpdated = sharedPreferences.getLong(PREF_LAST_UPDATED, 0L)
        
        _uiState.value = SettingsUiState(
            calibrationCount = count,
            calibrationFactor = factor,
            speedAlgorithm = algorithm,
            countdownDuration = countdownDuration,
            idleSensitivity = idleSensitivity,
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
     * 속도 측정 알고리즘 변경
     */
    fun setSpeedAlgorithm(algorithm: SpeedAlgorithm) {
        _uiState.value = _uiState.value.copy(
            speedAlgorithm = algorithm,
            lastUpdated = System.currentTimeMillis()
        )
        saveSettings()
    }
    
    /**
     * 카운트다운 시간 변경
     */
    fun setCountdownDuration(duration: Int) {
        val validDuration = duration.coerceIn(0, 3)
        _uiState.value = _uiState.value.copy(
            countdownDuration = validDuration,
            lastUpdated = System.currentTimeMillis()
        )
        saveSettings()
    }
    
    /**
     * 정지 감도 변경
     */
    fun setIdleSensitivity(sensitivity: Int) {
        val validSensitivity = sensitivity.coerceIn(1, 5)
        _uiState.value = _uiState.value.copy(
            idleSensitivity = validSensitivity,
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
            putString(PREF_SPEED_ALGORITHM, _uiState.value.speedAlgorithm.name)
            putInt(PREF_COUNTDOWN_DURATION, _uiState.value.countdownDuration)
            putInt(PREF_IDLE_SENSITIVITY, _uiState.value.idleSensitivity)
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
            speedAlgorithm = DEFAULT_SPEED_ALGORITHM,
            countdownDuration = DEFAULT_COUNTDOWN_DURATION,
            idleSensitivity = DEFAULT_IDLE_SENSITIVITY,
            lastUpdated = System.currentTimeMillis()
        )
        saveSettings()
    }
}

