package com.example.signtalk.data.remote.dto

/**
 * Mirrors backend/sign-talk-api's `ModelVersion` Mongoose model, as returned
 * by GET /api/model/version -- registered via `npm run seed:model-version`
 * from ai/models/metadata.json (see model.controller.js / registerModelVersion.js).
 * Only the fields the app actually displays are declared; Gson silently
 * ignores the rest of the JSON object (labels, dataSource, etc.).
 */
data class ModelVersionDto(
    val version: String = "",
    val numClasses: Int = 0,
    val architecture: String? = null,
    val overallValAccuracy: Double? = null,
    val macroF1: Double? = null,
    val epochsTrained: Int? = null,
    val trainedAt: String? = null,
    val isActive: Boolean = false
)
