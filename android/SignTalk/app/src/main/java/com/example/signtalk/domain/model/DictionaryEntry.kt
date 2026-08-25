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
    val isUserAdded: Boolean = false
)
