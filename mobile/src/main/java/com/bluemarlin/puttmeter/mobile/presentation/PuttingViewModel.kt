package com.bluemarlin.puttmeter.mobile.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bluemarlin.puttmeter.data.wearable.*
import com.bluemarlin.puttmeter.mobile.data.WearableDataListenerService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * 모바일 앱의 퍼팅 데이터 ViewModel
 */
data class MobilePuttingUiState(
    val isWearConnected: Boolean = false,
    val currentPhase: PhaseData? = null,
    val isActive: Boolean = false,
    val currentMaxSpeed: Float = 0f,  // 현재 측정 중인 최대 속도
    val predictedDistance: Float = 0f,  // 실시간 예측 거리
    val calibrationFactor: Float = 1.0f,  // 보정 계수
    val lastStroke: StrokeResultData? = null,
    val sessionStats: SessionStatsData? = null,
    val strokeHistory: List<StrokeResultData> = emptyList(),
    val connectionStatus: String = "연결 대기 중..."
)

class PuttingViewModel : ViewModel() {
    
    private val _uiState = MutableStateFlow(MobilePuttingUiState())
    val uiState: StateFlow<MobilePuttingUiState> = _uiState.asStateFlow()
    
    companion object {
        private const val TAG = "PuttingViewModel"
    }
    
    init {
        Log.d(TAG, "PuttingViewModel initialized")
        // Wear OS 데이터 스트림 구독
        observeWearableData()
    }
    
    private fun observeWearableData() {
        Log.d(TAG, "Starting to observe wearable data streams")
        
        // 퍼팅 상태 관찰
        viewModelScope.launch {
            Log.d(TAG, "Subscribing to puttingStateFlow")
            WearableDataListenerService.puttingStateFlow.collect { puttingState ->
                Log.d(TAG, "Received PuttingState: ${puttingState.currentPhase.displayName}, maxSpeed: ${puttingState.currentMaxSpeed}")
                
                // 예측 거리 계산 (속도 x 보정 계수)
                val predictedDist = puttingState.currentMaxSpeed * _uiState.value.calibrationFactor
                
                _uiState.value = _uiState.value.copy(
                    isWearConnected = true,
                    currentPhase = puttingState.currentPhase,
                    isActive = puttingState.isActive,
                    currentMaxSpeed = puttingState.currentMaxSpeed,
                    predictedDistance = predictedDist.coerceIn(0f, 20f),
                    connectionStatus = if (puttingState.isActive) "측정 중" else "연결됨"
                )
            }
        }
        
        // 스트로크 결과 관찰
        viewModelScope.launch {
            WearableDataListenerService.strokeResultFlow.collect { strokeResult ->
                val currentHistory = _uiState.value.strokeHistory
                _uiState.value = _uiState.value.copy(
                    lastStroke = strokeResult,
                    strokeHistory = (currentHistory + strokeResult).takeLast(50) // 최근 50개만 유지
                )
            }
        }
        
        // 세션 통계 관찰
        viewModelScope.launch {
            WearableDataListenerService.sessionStatsFlow.collect { sessionStats ->
                _uiState.value = _uiState.value.copy(
                    sessionStats = sessionStats
                )
            }
        }
        
        // 앱 상태 관찰
        viewModelScope.launch {
            Log.d(TAG, "Subscribing to appStateFlow")
            WearableDataListenerService.appStateFlow.collect { appState ->
                Log.d(TAG, "Received AppState: measuring=${appState.isMeasuring}, active=${appState.isWearAppActive}")
                _uiState.value = _uiState.value.copy(
                    isWearConnected = true, // AppState를 받았다는 것은 연결되었다는 의미
                    connectionStatus = when {
                        appState.isMeasuring -> "측정 중"
                        else -> "연결됨"
                    }
                )
                Log.d(TAG, "UI State updated: connected=${_uiState.value.isWearConnected}, status=${_uiState.value.connectionStatus}")
            }
        }
    }
    
    /**
     * 스트로크 히스토리 클리어
     */
    fun clearHistory() {
        _uiState.value = _uiState.value.copy(
            strokeHistory = emptyList(),
            lastStroke = null
        )
    }
    
    /**
     * 연결 상태 새로고침
     */
    fun refreshConnection() {
        _uiState.value = _uiState.value.copy(
            connectionStatus = "연결 확인 중..."
        )
        // 실제 연결 확인 로직은 WearableDataListenerService에서 처리
    }
}
