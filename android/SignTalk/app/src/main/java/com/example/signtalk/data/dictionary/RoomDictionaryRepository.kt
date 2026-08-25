package com.example.signtalk.data.dictionary

import com.example.signtalk.domain.model.DictionaryEntry
import com.example.signtalk.domain.repository.DictionaryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomDictionaryRepository(
    private val dao: DictionaryDao
) : DictionaryRepository {

    suspend fun seedIfEmpty() {
        if (dao.count() == 0) {
            dao.insertAll(seedDictionaryEntries)
        }
    }

    override fun observeEntries(query: String): Flow<List<DictionaryEntry>> =
        dao.observeEntries(query.trim()).map { list -> list.map { it.toDomain() } }

    override suspend fun getEntry(id: Long): DictionaryEntry? =
        dao.getById(id)?.toDomain()

    override suspend fun findByLabel(label: String): DictionaryEntry? =
        dao.getByLabel(label)?.toDomain()

    override suspend fun addEntry(entry: DictionaryEntry): Long =
        dao.insert(entry.copy(isUserAdded = true).toEntity())

    override suspend fun deleteEntry(id: Long) {
        dao.deleteById(id)
    }
}
