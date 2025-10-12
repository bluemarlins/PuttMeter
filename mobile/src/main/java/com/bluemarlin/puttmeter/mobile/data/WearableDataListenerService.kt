package com.bluemarlin.puttmeter.mobile.data

import android.util.Log
import com.google.android.gms.wearable.*
import com.google.gson.Gson
import com.bluemarlin.puttmeter.data.wearable.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Wear OS로부터 데이터를 수신하는 서비스
 */
class WearableDataListenerService : WearableListenerService() {
    
    private val gson = Gson()
    
    companion object {
        private const val TAG = "WearableDataListener"
        
        // 데이터 스트림들 (replay=1로 설정하여 마지막 값을 캐시)
        private val _puttingStateFlow = MutableSharedFlow<PuttingStateData>(replay = 1)
        val puttingStateFlow: SharedFlow<PuttingStateData> = _puttingStateFlow.asSharedFlow()
        
        private val _strokeResultFlow = MutableSharedFlow<StrokeResultData>(replay = 1)
        val strokeResultFlow: SharedFlow<StrokeResultData> = _strokeResultFlow.asSharedFlow()
        
        private val _sessionStatsFlow = MutableSharedFlow<SessionStatsData>(replay = 1)
        val sessionStatsFlow: SharedFlow<SessionStatsData> = _sessionStatsFlow.asSharedFlow()
        
        private val _appStateFlow = MutableSharedFlow<AppStateData>(replay = 1)
        val appStateFlow: SharedFlow<AppStateData> = _appStateFlow.asSharedFlow()
    }
    
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        super.onDataChanged(dataEvents)
        
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED) {
                val dataItem = event.dataItem
                handleDataItem(dataItem)
            }
        }
    }
    
    private fun handleDataItem(dataItem: DataItem) {
        val path = dataItem.uri.path
        val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
        val jsonData = dataMap.getString("data")
        
        if (jsonData == null) {
            Log.w(TAG, "No data found in path: $path")
            return
        }
        
        try {
            when (path) {
                PuttingStateData.PATH -> {
                    val puttingState = gson.fromJson(jsonData, PuttingStateData::class.java)
                    _puttingStateFlow.tryEmit(puttingState)
                    Log.d(TAG, "Received putting state: ${puttingState.currentPhase.displayName}")
                }
                
                StrokeResultData.PATH -> {
                    val strokeResult = gson.fromJson(jsonData, StrokeResultData::class.java)
                    _strokeResultFlow.tryEmit(strokeResult)
                    Log.d(TAG, "Received stroke result: ${strokeResult.predictedDistance}m")
                }
                
                SessionStatsData.PATH -> {
                    val sessionStats = gson.fromJson(jsonData, SessionStatsData::class.java)
                    _sessionStatsFlow.tryEmit(sessionStats)
                    Log.d(TAG, "Received session stats: ${sessionStats.totalStrokes} strokes")
                }
                
                AppStateData.PATH -> {
                    val appState = gson.fromJson(jsonData, AppStateData::class.java)
                    val emitted = _appStateFlow.tryEmit(appState)
                    Log.d(TAG, "Received app state: measuring=${appState.isMeasuring}, emitted=$emitted, subscribers=${_appStateFlow.subscriptionCount.value}")
                }
                
                else -> {
                    Log.w(TAG, "Unknown data path: $path")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse data for path: $path", e)
        }
    }
    
    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)
        
        val message = String(messageEvent.data)
        Log.d(TAG, "Received message: ${messageEvent.path} - $message")
        
        // 긴급 메시지 처리 (필요시)
        when (messageEvent.path) {
            "/urgent/impact" -> {
                // 임팩트 긴급 알림 처리
                Log.i(TAG, "Urgent impact notification received")
            }
        }
    }
    
    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        super.onCapabilityChanged(capabilityInfo)
        Log.d(TAG, "Capability changed: ${capabilityInfo.name}")
    }
}
