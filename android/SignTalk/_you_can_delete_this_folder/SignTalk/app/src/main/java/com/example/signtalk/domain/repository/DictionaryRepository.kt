package com.example.signtalk.domain.repository

import com.example.signtalk.domain.model.DictionaryEntry
import kotlinx.coroutines.flow.Flow

interface DictionaryRepository {
    fun observeEntries(query: String = ""): Flow<List<DictionaryEntry>>
    suspend fun getEntry(id: Long): DictionaryEntry?
    suspend fun findByLabel(label: String): DictionaryEntry?
    suspend fun addEntry(entry: DictionaryEntry): Long
    suspend fun deleteEntry(id: Long)
}
