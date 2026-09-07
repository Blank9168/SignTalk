package com.example.signtalk.data.remote.dto

/**
 * Mirrors backend/sign-talk-api's `DictionaryEntry` Mongoose model
 * (src/models/DictionaryEntry.js) -- see GET /api/dictionary in
 * dictionary.controller.js. Field names match the JSON keys exactly, so no
 * @SerializedName is needed except where noted.
 */
data class DictionaryEntryDto(
    val slug: String,
    val label: String,
    // "FSL" or "ASL" -- not currently surfaced separately in the Android UI
    // (the bundled DictionarySeed.kt entries fold this into `category`,
    // e.g. "FSL - Numbers"), kept here for when that changes.
    val language: String = "",
    val category: String,
    val description: String = "",
    val emoji: String = "",
    val hasVideo: Boolean = false,
    val hasTrainingData: Boolean = false
)

/** Shape of GET /api/dictionary's response body: `{ count, entries }`. */
data class DictionaryListResponseDto(
    val count: Int = 0,
    val entries: List<DictionaryEntryDto> = emptyList()
)
