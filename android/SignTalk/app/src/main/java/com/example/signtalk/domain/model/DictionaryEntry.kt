package com.example.signtalk.domain.model

/**
 * A single entry in the FSL dictionary: one recognizable sign plus
 * human-readable info about it. [isUserAdded] distinguishes entries the
 * user created via "Add entry" from the seeded starter set.
 */
data class DictionaryEntry(
    val id: Long = 0L,
    val label: String,
    val displayName: String,
    val category: String,
    val description: String,
    val emoji: String,
    val isUserAdded: Boolean = false,
    // Content-resolver URI (string form) of a video the user attached showing
    // this sign performed, or null if none has been added yet. Works for any
    // entry -- including the seeded FSL-105 placeholders, which otherwise have
    // no reference video/image at all.
    val videoUri: String? = null
)
