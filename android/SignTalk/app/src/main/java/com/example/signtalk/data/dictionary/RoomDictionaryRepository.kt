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

    /**
     * Refreshes dictionary content from the backend (GET /api/dictionary --
     * see AppContainer.syncDictionaryFromBackend), matched by [label] (the
     * server's `slug`). Existing rows are updated in place rather than
     * replaced, so a locally-attached [DictionaryEntity.videoUri] and the
     * row's [DictionaryEntity.isUserAdded] flag survive a sync. A remote
     * entry with no matching local row is inserted as new. Entries only
     * present locally (e.g. anything the user added via "Add entry", or a
     * row the server has since removed) are left untouched -- this is a
     * one-way, additive/overwrite sync, not a mirror, so nothing the user
     * added on-device can be deleted by a background sync.
     */
    suspend fun upsertFromRemote(remoteEntries: List<DictionaryEntry>) {
        for (remote in remoteEntries) {
            val existing = dao.getByLabel(remote.label)
            if (existing != null) {
                dao.update(
                    existing.copy(
                        displayName = remote.displayName,
                        category = remote.category,
                        description = remote.description,
                        emoji = remote.emoji
                    )
                )
            } else {
                dao.insert(remote.toEntity())
            }
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

    override suspend fun setVideoUri(id: Long, videoUri: String?) {
        dao.updateVideoUri(id, videoUri)
    }
}
