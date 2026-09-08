package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.Highlight
import kotlinx.coroutines.flow.Flow

@Dao
interface HighlightDao {

    @Query("SELECT * FROM highlights WHERE bookId = :bookId ORDER BY pageIndex ASC, createdAt DESC")
    fun getHighlightsForBook(bookId: Long): Flow<List<Highlight>>

    @Query("SELECT * FROM highlights ORDER BY createdAt DESC")
    fun getAllHighlights(): Flow<List<Highlight>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHighlight(highlight: Highlight): Long

    @Update
    suspend fun updateHighlight(highlight: Highlight)

    @Delete
    suspend fun deleteHighlight(highlight: Highlight)

    @Query("DELETE FROM highlights WHERE id = :id")
    suspend fun deleteHighlightById(id: Long)
}
