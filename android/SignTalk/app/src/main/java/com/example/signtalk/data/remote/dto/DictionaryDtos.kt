package com.example.signtalk.data.remote.dto

/**
 * Mirrors backend/sign-talk-api's `DictionaryEntry` Mongoose model
 * (src/models/DictionaryEntry.js) -- see GET /api/dictionary in
 * dictionary.controller.js. Field names match the JSON keys exactly.
 *
 * The metadata fields are nullable on purpose: Gson ignores Kotlin default values, so a
 * response from an older backend without them would otherwise put null into a non-null field.
 */
data class DictionaryEntryDto(
    val slug: String?,
    val label: String?,
    // "FSL" or "ASL"
    val language: String? = null,
    val category: String?,
    val description: String? = null,
    val emoji: String? = null,
    val hasVideo: Boolean = false,
    val hasTrainingData: Boolean = false,
    val gloss: String? = null,
    val meaningEn: String? = null,
    val meaningFil: String? = null,
    val synonyms: List<String>? = null,
    val signType: String? = null,
    val hands: String? = null,
    val source: String? = null,
    val validationStatus: String? = null,
    val validatedBy: String? = null
)

/** Shape of GET /api/dictionary's response body: `{ count, entries }`. */
data class DictionaryListResponseDto(
    val count: Int = 0,
    val entries: List<DictionaryEntryDto>? = null
)

/**
 * Body of POST /api/submissions ("Add a sign" sent for review). Required on the backend:
 * name, meaningEn, meaningFil, category, signType ("static"/"dynamic"), hands ("one"/"two"),
 * source. Unknown keys are rejected with 400, so keep this in sync with
 * backend/sign-talk-api/src/middleware/validators.js.
 */
data class SubmissionRequestDto(
    val name: String,
    val meaningEn: String,
    val meaningFil: String,
    val category: String,
    val signType: String,
    val hands: String,
    val source: String,
    val gloss: String? = null,
    val language: String? = null,
    val description: String? = null,
    /** Optional http(s) link to a demonstration video reviewers can open. */
    val mediaUrl: String? = null,
    val notes: String? = null,
    val submitterName: String? = null
)

/** Subset of the 201 response we use. */
data class SubmissionResponseDto(
    val _id: String? = null,
    val slug: String? = null,
    val status: String? = null
)
