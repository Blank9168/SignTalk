package com.example.signtalk.data.dictionary

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.signtalk.domain.model.DictionaryEntry
import com.example.signtalk.domain.model.ValidationStatus

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
    val videoUri: String? = null,
    // --- Added in schema v3 (expert review): see MIGRATION_2_3. ---
    @ColumnInfo(defaultValue = "") val language: String = "",
    @ColumnInfo(defaultValue = "") val gloss: String = "",
    @ColumnInfo(defaultValue = "") val meaningEn: String = "",
    @ColumnInfo(defaultValue = "") val meaningFil: String = "",
    /** Synonyms joined with [SYNONYM_SEPARATOR], lowercase, e.g. "one|1|uno". */
    @ColumnInfo(defaultValue = "") val synonyms: String = "",
    @ColumnInfo(defaultValue = "") val signType: String = "",
    @ColumnInfo(defaultValue = "") val hands: String = "",
    @ColumnInfo(defaultValue = "") val source: String = "",
    @ColumnInfo(defaultValue = "unverified") val validationStatus: String = ValidationStatus.UNVERIFIED,
    @ColumnInfo(defaultValue = "") val validatedBy: String = ""
)

const val SYNONYM_SEPARATOR = "|"

fun DictionaryEntity.toDomain() = DictionaryEntry(
    id = id,
    label = label,
    displayName = displayName,
    category = category,
    description = description,
    emoji = emoji,
    isUserAdded = isUserAdded,
    videoUri = videoUri,
    language = language,
    gloss = gloss,
    meaningEn = meaningEn,
    meaningFil = meaningFil,
    synonyms = synonyms.split(SYNONYM_SEPARATOR).filter { it.isNotBlank() },
    signType = signType,
    hands = hands,
    source = source,
    validationStatus = validationStatus,
    validatedBy = validatedBy
)

fun DictionaryEntry.toEntity() = DictionaryEntity(
    id = id,
    label = label,
    displayName = displayName,
    category = category,
    description = description,
    emoji = emoji,
    isUserAdded = isUserAdded,
    videoUri = videoUri,
    language = language,
    gloss = gloss,
    meaningEn = meaningEn,
    meaningFil = meaningFil,
    synonyms = synonyms.joinToString(SYNONYM_SEPARATOR) { it.trim().lowercase() },
    signType = signType,
    hands = hands,
    source = source,
    validationStatus = validationStatus,
    validatedBy = validatedBy
)

/** Fills the linguistic/provenance columns of a bundled sign from [signMetadata]. */
fun DictionaryEntity.withBundledMetadata(): DictionaryEntity {
    val meta = signMetadata[label] ?: return this
    return copy(
        language = meta.language,
        gloss = meta.gloss,
        meaningEn = meta.meaningEn,
        meaningFil = meta.meaningFil,
        synonyms = meta.synonyms.joinToString(SYNONYM_SEPARATOR),
        signType = meta.signType,
        hands = meta.hands,
        source = meta.source,
        validationStatus = meta.validationStatus,
        validatedBy = meta.validatedBy
    )
}
