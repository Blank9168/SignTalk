package com.example.signtalk.data.dictionary

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Update
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DictionaryDao {

    @Query(
        """
        SELECT * FROM dictionary_entries
        WHERE :query = '' OR displayName LIKE '%' || :query || '%' OR category LIKE '%' || :query || '%'
        ORDER BY displayName ASC
        """
    )
    fun observeEntries(query: String): Flow<List<DictionaryEntity>>

    @Query("SELECT * FROM dictionary_entries WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): DictionaryEntity?

    @Query("SELECT * FROM dictionary_entries WHERE label = :label LIMIT 1")
    suspend fun getByLabel(label: String): DictionaryEntity?

    @Query("SELECT COUNT(*) FROM dictionary_entries")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DictionaryEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entities: List<DictionaryEntity>)

    // Used by the backend dictionary sync (see RoomDictionaryRepository.
    // upsertFromRemote) to refresh an existing row in place, matched by its
    // primary key -- keeps the row's id/videoUri/isUserAdded intact while
    // updating the text fields from the server.
    @Update
    suspend fun update(entity: DictionaryEntity)

    @Delete
    suspend fun delete(entity: DictionaryEntity)

    @Query("DELETE FROM dictionary_entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE dictionary_entries SET videoUri = :videoUri WHERE id = :id")
    suspend fun updateVideoUri(id: Long, videoUri: String?)
}
