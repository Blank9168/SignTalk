package com.example.signtalk.recognition

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Measures what the expert review asked for, instead of relying on "it feels fast":
 *
 * - **Latency** (latency.csv), one row per accepted sign: first hand frame -> sign accepted
 *   (stability rule passed) -> recognized text drawn on screen -> speech actually started.
 * - **Session** (sessions.csv), one row per recognition session: duration, mean FPS, mean
 *   inference time, battery level and battery temperature at the start and end.
 *
 * Only active when Settings > "Performance logging" is on. Files are written to
 * `Android/data/com.example.signtalk/files/performance/` on the phone (copy them off with
 * Android Studio's Device Explorer or `adb pull`). `ai/evaluation/README.md` explains how
 * to summarize them (mean and p95) for the manuscript.
 */
class PerformanceLogger(context: Context) {

    private val appContext = context.applicationContext
    private val io = Executors.newSingleThreadExecutor()
    private val dir: File? get() = appContext.getExternalFilesDir("performance")
    private val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    @Volatile var enabled: Boolean = false

    private var sessionStartMs = 0L
    private var sessionStartBattery: BatterySnapshot? = null
    private var fpsSum = 0.0
    private var inferenceSum = 0.0
    private var samples = 0

    data class BatterySnapshot(val levelPercent: Int, val temperatureC: Float)

    /**
     * One row per accepted sign. All times are `SystemClock.elapsedRealtime()` values;
     * [speechStartMs] is null when speech is muted.
     */
    fun logLatency(
        label: String,
        confidence: Float,
        firstHandMs: Long,
        acceptedMs: Long,
        textShownMs: Long,
        speechStartMs: Long?
    ) {
        if (!enabled) return
        fun d(a: Long, b: Long?) = if (a > 0 && b != null && b >= a) (b - a).toString() else ""
        append(
            "latency.csv",
            "date,device,label,confidence,hand_to_accept_ms,accept_to_text_ms,accept_to_speech_ms,hand_to_text_ms",
            listOf(
                stamp.format(Date()), "${Build.MANUFACTURER} ${Build.MODEL}".replace(",", " "),
                label, "%.3f".format(Locale.US, confidence),
                d(firstHandMs, acceptedMs), d(acceptedMs, textShownMs),
                d(acceptedMs, speechStartMs), d(firstHandMs, textShownMs)
            ).joinToString(",")
        )
    }

    fun startSession() {
        if (!enabled) return
        sessionStartMs = SystemClock.elapsedRealtime()
        sessionStartBattery = battery()
        fpsSum = 0.0
        inferenceSum = 0.0
        samples = 0
    }

    fun addSample(fps: Float, inferenceMs: Float) {
        if (!enabled || sessionStartMs == 0L || fps <= 0f) return
        fpsSum += fps
        inferenceSum += inferenceMs
        samples++
    }

    fun endSession() {
        if (!enabled || sessionStartMs == 0L) return
        val start = sessionStartBattery
        val end = battery()
        val durationS = (SystemClock.elapsedRealtime() - sessionStartMs) / 1000.0
        val meanFps = if (samples > 0) fpsSum / samples else 0.0
        val meanInference = if (samples > 0) inferenceSum / samples else 0.0
        sessionStartMs = 0L
        append(
            "sessions.csv",
            "date,device,duration_s,mean_fps,mean_inference_ms,battery_start_pct,battery_end_pct,temp_start_c,temp_end_c",
            listOf(
                stamp.format(Date()), "${Build.MANUFACTURER} ${Build.MODEL}".replace(",", " "),
                "%.1f".format(Locale.US, durationS), "%.1f".format(Locale.US, meanFps),
                "%.1f".format(Locale.US, meanInference),
                start?.levelPercent ?: "", end?.levelPercent ?: "",
                start?.temperatureC ?: "", end?.temperatureC ?: ""
            ).joinToString(",")
        )
    }

    private fun append(fileName: String, header: String, row: String) {
        io.execute {
            try {
                val folder = dir ?: return@execute
                folder.mkdirs()
                val file = File(folder, fileName)
                if (!file.exists()) file.writeText(header + "\n")
                file.appendText(row + "\n")
            } catch (e: Exception) {
                Log.w("PerformanceLogger", "Could not write $fileName: ${e.message}")
            }
        }
    }

    private fun battery(): BatterySnapshot? = try {
        val intent: Intent? = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val tempTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        BatterySnapshot(
            levelPercent = if (level >= 0 && scale > 0) level * 100 / scale else -1,
            temperatureC = if (tempTenths != Int.MIN_VALUE) tempTenths / 10f else Float.NaN
        )
    } catch (e: Exception) {
        null
    }

    fun shutdown() {
        io.shutdown()
    }
}
