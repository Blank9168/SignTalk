package com.example.signtalk.data.dictionary

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.signtalk.domain.model.DictionaryEntry

@Entity(tableName = "dictionary_entries")
data class DictionaryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val label: String,
    val displayName: String,
    val category: String,
    val description: String,
    val emoji: String,
    val isUserAdded: Boolean,
    // Nullable so every one of the 105 seeded rows (inserted with the old
    // 7-arg constructor call, before this column existed) gets NULL here --
    // see MIGRATION_1_2 in SignTalkDatabase.kt for how existing installs
    // pick this column up.
    val videoUri: String? = null
)

fun DictionaryEntity.toDomain() = DictionaryEntry(
    id = id,
    label = label,
    displayName = displayName,
    category = category,
    description = description,
    emoji = emoji,
    isUserAdded = isUserAdded,
    videoUri = videoUri
)

fun DictionaryEntry.toEntity() = DictionaryEntity(
    id = id,
    label = label,
    displayName = displayName,
    category = category,
    description = description,
    emoji = emoji,
    isUserAdded = isUserAdded,
    videoUri = videoUri
)
