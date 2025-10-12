package com.bluemarlin.puttmeter.wearable.data.wearable

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.*
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await

/**
 * Wear OS에서 Mobile로 데이터를 전송하는 서비스
 */
class WearableDataSender(private val context: Context) {
    
    private val dataClient: DataClient = Wearable.getDataClient(context)
    private val messageClient: MessageClient = Wearable.getMessageClient(context)
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    companion object {
        private const val TAG = "WearableDataSender"
        private const val CAPABILITY_MOBILE_APP = "putt_meter_mobile"
    }
    
    /**
     * 실시간 퍼팅 상태 전송
     */
    suspend fun sendPuttingState(stateData: PuttingStateData) {
        try {
            val isConnected = isConnectedToMobile()
            Log.d(TAG, "Connection status: $isConnected")
            
            val json = gson.toJson(stateData)
            val putDataReq = PutDataMapRequest.create(PuttingStateData.PATH).apply {
                dataMap.putString("data", json)
                dataMap.putLong("timestamp", System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()
            
            dataClient.putDataItem(putDataReq).await()
            Log.d(TAG, "Putting state sent: ${stateData.currentPhase.displayName}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send putting state", e)
        }
    }
    
    /**
     * 스트로크 결과 전송
     */
    suspend fun sendStrokeResult(strokeData: StrokeResultData) {
        try {
            val json = gson.toJson(strokeData)
            val putDataReq = PutDataMapRequest.create(StrokeResultData.PATH).apply {
                dataMap.putString("data", json)
                dataMap.putLong("timestamp", System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()
            
            dataClient.putDataItem(putDataReq).await()
            Log.d(TAG, "Stroke result sent: ${strokeData.predictedDistance}m")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send stroke result", e)
        }
    }
    
    /**
     * 세션 통계 전송
     */
    suspend fun sendSessionStats(statsData: SessionStatsData) {
        try {
            val json = gson.toJson(statsData)
            val putDataReq = PutDataMapRequest.create(SessionStatsData.PATH).apply {
                dataMap.putString("data", json)
                dataMap.putLong("timestamp", System.currentTimeMillis())
            }.asPutDataRequest()
            
            dataClient.putDataItem(putDataReq).await()
            Log.d(TAG, "Session stats sent: ${statsData.totalStrokes} strokes")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send session stats", e)
        }
    }
    
    /**
     * 앱 상태 전송 (하트비트)
     */
    suspend fun sendAppState(appState: AppStateData) {
        try {
            val json = gson.toJson(appState)
            val putDataReq = PutDataMapRequest.create(AppStateData.PATH).apply {
                dataMap.putString("data", json)
                dataMap.putLong("timestamp", System.currentTimeMillis())
            }.asPutDataRequest()
            
            dataClient.putDataItem(putDataReq).await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send app state", e)
        }
    }
    
    /**
     * 긴급 메시지 전송 (즉시 알림용)
     */
    suspend fun sendUrgentMessage(path: String, message: String) {
        try {
            val nodes = getConnectedNodes()
            nodes.forEach { node ->
                messageClient.sendMessage(node.id, path, message.toByteArray()).await()
            }
            Log.d(TAG, "Urgent message sent: $path")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send urgent message", e)
        }
    }
    
    /**
     * 연결된 노드 목록 가져오기
     */
    private suspend fun getConnectedNodes(): List<Node> {
        return try {
            val capabilityInfo = Wearable.getCapabilityClient(context)
                .getCapability(CAPABILITY_MOBILE_APP, CapabilityClient.FILTER_REACHABLE)
                .await()
            capabilityInfo.nodes.toList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get connected nodes", e)
            emptyList()
        }
    }
    
    /**
     * 연결 상태 확인
     */
    suspend fun isConnectedToMobile(): Boolean {
        return getConnectedNodes().isNotEmpty()
    }
    
    /**
     * 리소스 정리
     */
    fun cleanup() {
        scope.cancel()
    }
}
