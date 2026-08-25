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
    val isUserAdded: Boolean
)

fun DictionaryEntity.toDomain() = DictionaryEntry(
    id = id,
    label = label,
    displayName = displayName,
    category = category,
    description = description,
    emoji = emoji,
    isUserAdded = isUserAdded
)

fun DictionaryEntry.toEntity() = DictionaryEntity(
    id = id,
    label = label,
    displayName = displayName,
    category = category,
    description = description,
    emoji = emoji,
    isUserAdded = isUserAdded
)
