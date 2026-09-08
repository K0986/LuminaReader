package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.ReadingSession
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingSessionDao {

    @Query("SELECT * FROM reading_sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<ReadingSession>>

    @Query("SELECT * FROM reading_sessions WHERE bookId = :bookId ORDER BY startTime DESC")
    fun getSessionsForBook(bookId: Long): Flow<List<ReadingSession>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ReadingSession): Long

    @Query("SELECT SUM(endTime - startTime) FROM reading_sessions")
    fun getTotalReadingTimeMs(): Flow<Long?>

    @Query("SELECT SUM(pagesRead) FROM reading_sessions")
    fun getTotalPagesRead(): Flow<Int?>
}
