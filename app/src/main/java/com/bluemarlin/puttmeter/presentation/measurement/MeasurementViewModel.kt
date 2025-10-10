package com.bluemarlin.puttmeter.presentation.measurement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bluemarlin.puttmeter.data.sensor.SensorDataSource
import com.bluemarlin.puttmeter.domain.detection.StrokeDetector
import com.bluemarlin.puttmeter.domain.model.PuttStroke
import com.bluemarlin.puttmeter.domain.model.StrokePhase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * 측정 화면 UI 상태
 */
data class MeasurementUiState(
    val isActive: Boolean = false,
    val currentPhase: StrokePhase = StrokePhase.Idle,
    val lastStroke: PuttStroke? = null,
    val sessionStrokes: List<PuttStroke> = emptyList(),
    val averageDistance: Float = 0f,
    val strokeCount: Int = 0,
    val error: String? = null
)

/**
 * 측정 ViewModel
 */
class MeasurementViewModel(
    private val sensorDataSource: SensorDataSource,
    private val strokeDetector: StrokeDetector = StrokeDetector()
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(MeasurementUiState())
    val uiState: StateFlow<MeasurementUiState> = _uiState.asStateFlow()
    
    init {
        // 스트로크 감지 상태 관찰
        viewModelScope.launch {
            strokeDetector.currentPhase.collect { phase ->
                _uiState.value = _uiState.value.copy(currentPhase = phase)
            }
        }
        
        // 스트로크 감지 결과 관찰
        viewModelScope.launch {
            strokeDetector.detectedStroke.collect { stroke ->
                stroke?.let {
                    onStrokeDetected(it)
                }
            }
        }
    }
    
    /**
     * 측정 시작
     */
    fun startMeasurement() {
        if (_uiState.value.isActive) return
        
        _uiState.value = _uiState.value.copy(
            isActive = true,
            error = null
        )
        
        // 센서 데이터 수집 시작
        viewModelScope.launch {
            sensorDataSource.getSensorDataStream()
                .catch { e ->
                    _uiState.value = _uiState.value.copy(
                        isActive = false,
                        error = "센서 오류: ${e.message}"
                    )
                }
                .collect { sensorData ->
                    if (_uiState.value.isActive) {
                        strokeDetector.processSensorData(sensorData)
                    }
                }
        }
    }
    
    /**
     * 측정 중지
     */
    fun stopMeasurement() {
        _uiState.value = _uiState.value.copy(isActive = false)
        strokeDetector.reset()
    }
    
    /**
     * 스트로크 감지 콜백
     */
    private fun onStrokeDetected(stroke: PuttStroke) {
        val currentStrokes = _uiState.value.sessionStrokes + stroke
        val avgDistance = currentStrokes.map { it.predictedDistance }.average().toFloat()
        
        _uiState.value = _uiState.value.copy(
            lastStroke = stroke,
            sessionStrokes = currentStrokes,
            averageDistance = avgDistance,
            strokeCount = currentStrokes.size
        )
        
        // 스트로크 처리 완료
        strokeDetector.clearDetectedStroke()
    }
    
    /**
     * 마지막 스트로크 제거
     */
    fun removeLastStroke() {
        val strokes = _uiState.value.sessionStrokes
        if (strokes.isEmpty()) return
        
        val newStrokes = strokes.dropLast(1)
        val avgDistance = if (newStrokes.isNotEmpty()) {
            newStrokes.map { it.predictedDistance }.average().toFloat()
        } else {
            0f
        }
        
        _uiState.value = _uiState.value.copy(
            lastStroke = newStrokes.lastOrNull(),
            sessionStrokes = newStrokes,
            averageDistance = avgDistance,
            strokeCount = newStrokes.size
        )
    }
    
    /**
     * 세션 초기화
     */
    fun clearSession() {
        _uiState.value = _uiState.value.copy(
            lastStroke = null,
            sessionStrokes = emptyList(),
            averageDistance = 0f,
            strokeCount = 0
        )
        strokeDetector.reset()
    }
    
    /**
     * 오류 메시지 지우기
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

