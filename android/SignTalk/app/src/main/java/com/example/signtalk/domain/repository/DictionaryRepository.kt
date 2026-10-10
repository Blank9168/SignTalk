package com.example.signtalk.domain.repository

import com.example.signtalk.domain.model.DictionaryEntry
import kotlinx.coroutines.flow.Flow

interface DictionaryRepository {
    fun observeEntries(query: String = ""): Flow<List<DictionaryEntry>>
    suspend fun getAll(): List<DictionaryEntry>
    suspend fun getEntry(id: Long): DictionaryEntry?
    suspend fun findByLabel(label: String): DictionaryEntry?
    /** Exact synonym match, e.g. "blue" -> Asul, "hindi ko alam" -> Don't Know. */
    suspend fun findBySynonym(phrase: String): DictionaryEntry?
    suspend fun addEntry(entry: DictionaryEntry): Long
    suspend fun deleteEntry(id: Long)
    /** Attaches (or clears, with null) a video URI to any entry, seeded or user-added. */
    suspend fun setVideoUri(id: Long, videoUri: String?)
    suspend fun setValidationStatus(id: Long, status: String)
}
