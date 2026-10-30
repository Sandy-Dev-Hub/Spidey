package com.spidey.assistant.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AliasDao {
    @Query("SELECT * FROM contact_aliases ORDER BY alias ASC")
    fun getAllAsFlow(): Flow<List<ContactAlias>>

    @Query("SELECT * FROM contact_aliases")
    suspend fun getAll(): List<ContactAlias>

    @Query("SELECT * FROM contact_aliases WHERE LOWER(alias) = LOWER(:alias) LIMIT 1")
    suspend fun findByAlias(alias: String): ContactAlias?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alias: ContactAlias)

    @Delete
    suspend fun delete(alias: ContactAlias)

    @Query("DELETE FROM contact_aliases")
    suspend fun clearAll()
}
