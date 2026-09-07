package com.example.signtalk.data.remote.dto

/**
 * Request body for POST /api/logs/recognition (logs.controller.js /
 * RecognitionLog.js). Sent best-effort whenever the on-device recognizer
 * settles on a sign -- see RecognitionViewModel. `wasCorrect` stays null for
 * now since there's no "was this right?" feedback UI yet; the backend
 * accepts it as optional.
 */
data class RecognitionLogRequestDto(
    val predictedSlug: String,
    val confidence: Double,
    val wasCorrect: Boolean? = null,
    val modelVersion: String? = null,
    val deviceInfo: String? = null
)
