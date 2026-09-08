package com.example.data.repository

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.model.ReadingSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class ReadingStats(
    val totalTimeMinutes: Int,
    val totalPagesRead: Int,
    val currentStreakDays: Int,
    val booksFinishedCount: Int,
    val achievements: List<Achievement>
)

data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val isUnlocked: Boolean,
    val iconName: String
)

class ReadingSessionRepository(context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val sessionDao = db.readingSessionDao()
    private val bookDao = db.bookDao()

    private var currentSessionStartTime: Long = 0L
    private var currentBookId: Long = 0L

    fun startSession(bookId: Long) {
        currentBookId = bookId
        currentSessionStartTime = System.currentTimeMillis()
    }

    suspend fun stopSession(pagesRead: Int = 1) = withContext(Dispatchers.IO) {
        if (currentSessionStartTime > 0 && currentBookId > 0) {
            val endTime = System.currentTimeMillis()
            val durationMs = endTime - currentSessionStartTime
            if (durationMs > 10_000) { // Only record if read for at least 10 seconds
                val session = ReadingSession(
                    bookId = currentBookId,
                    startTime = currentSessionStartTime,
                    endTime = endTime,
                    pagesRead = pagesRead.coerceAtLeast(1)
                )
                sessionDao.insertSession(session)
            }
            currentSessionStartTime = 0L
            currentBookId = 0L
        }
    }

    val stats: Flow<ReadingStats> = sessionDao.getAllSessions().map { sessions ->
        val totalMs = sessions.sumOf { it.endTime - it.startTime }
        val totalMins = (totalMs / 60_000).toInt()
        val totalPages = sessions.sumOf { it.pagesRead }

        // Streak calculation (distinct days read)
        val streak = calculateStreakDays(sessions)

        val achievements = listOf(
            Achievement("first_read", "First Steps", "Completed your first reading session", sessions.isNotEmpty(), "book"),
            Achievement("streak_3", "Reader on Fire", "Maintained a 3-day reading streak", streak >= 3, "fire"),
            Achievement("pages_50", "Page Turner", "Read over 50 pages across all books", totalPages >= 50, "auto_stories"),
            Achievement("time_60", "Marathon Reader", "Spent 1 hour immersed in reading", totalMins >= 60, "timer")
        )

        ReadingStats(
            totalTimeMinutes = totalMins,
            totalPagesRead = totalPages,
            currentStreakDays = streak,
            booksFinishedCount = 0,
            achievements = achievements
        )
    }

    private fun calculateStreakDays(sessions: List<ReadingSession>): Int {
        if (sessions.isEmpty()) return 0
        val dayTimestamps = sessions.map { it.startTime / (1000 * 60 * 60 * 24) }.distinct().sortedDescending()
        val todayDay = System.currentTimeMillis() / (1000 * 60 * 60 * 24)

        var streak = 0
        var expectedDay = todayDay

        // If not read today yet, allow yesterday as start of streak
        if (dayTimestamps.isNotEmpty() && dayTimestamps.first() == todayDay - 1) {
            expectedDay = todayDay - 1
        }

        for (day in dayTimestamps) {
            if (day == expectedDay) {
                streak++
                expectedDay--
            } else if (day < expectedDay) {
                break
            }
        }
        return streak
    }
}
