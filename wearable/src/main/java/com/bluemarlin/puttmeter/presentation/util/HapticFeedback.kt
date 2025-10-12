package com.bluemarlin.puttmeter.wearable.presentation.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * 햅틱 피드백 유틸리티
 */
class HapticFeedback(private val context: Context) {
    
    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }
    
    /**
     * 짧은 탭 진동 (측정 시작)
     */
    fun tapStart() {
        vibrate(50, 0.3f)
    }
    
    /**
     * 임팩트 감지 진동 (강한 1회)
     */
    fun impactDetected() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10 이상: 사전 정의된 효과 사용
            val effect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
            vibrator.vibrate(effect)
        } else {
            // 그 이전: 강한 진동
            vibrate(100, 1.0f)
        }
    }
    
    /**
     * 어드레스 감지 진동 (부드러운 1회 - 준비 완료 알림)
     */
    fun addressDetected() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10 이상: 부드러운 클릭 효과
            val effect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
            vibrator.vibrate(effect)
        } else {
            // 그 이전: 부드러운 진동
            vibrate(80, 0.5f)
        }
    }
    
    /**
     * 측정 완료 진동 (부드러운 2회)
     */
    fun measurementComplete() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val pattern = longArrayOf(0, 50, 50, 50)
            val amplitudes = intArrayOf(0, 128, 0, 128)
            val effect = VibrationEffect.createWaveform(pattern, amplitudes, -1)
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 50, 50, 50), -1)
        }
    }
    
    /**
     * 오류 진동 (긴 3회)
     */
    fun error() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val pattern = longArrayOf(0, 100, 100, 100, 100, 100)
            val amplitudes = intArrayOf(0, 200, 0, 200, 0, 200)
            val effect = VibrationEffect.createWaveform(pattern, amplitudes, -1)
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 100, 100, 100, 100, 100), -1)
        }
    }
    
    /**
     * 일반 진동
     * @param duration 지속시간 (ms)
     * @param amplitude 강도 (0.0 ~ 1.0)
     */
    private fun vibrate(duration: Long, amplitude: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val amplitudeInt = (amplitude * 255).toInt().coerceIn(1, 255)
            val effect = VibrationEffect.createOneShot(duration, amplitudeInt)
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
    }
}

