package com.example.data.repository

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.model.ReadingSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

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

    /**
     * Sessions end exactly when the reader screen leaves the composition, so the write
     * cannot be tied to that screen's `rememberCoroutineScope` -- it is cancelled at the
     * same instant. This repository-owned scope outlives any single screen.
     */
    private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var currentSessionStartTime: Long = 0L
    private var currentBookId: Long = 0L
    private var startPageIndex: Int = 0

    fun startSession(bookId: Long, startPage: Int = 0) {
        currentBookId = bookId
        startPageIndex = startPage
        currentSessionStartTime = System.currentTimeMillis()
    }

    /**
     * Records the finished session. [endPage] is the page the reader left off on; pages
     * read is derived from how far they actually travelled rather than the hard-coded
     * `1` the previous implementation always passed, which made the "pages read"
     * statistic count visits instead of pages.
     */
    fun stopSession(endPage: Int) {
        val startedAt = currentSessionStartTime
        val bookId = currentBookId
        val pagesRead = (endPage - startPageIndex).coerceAtLeast(0) + 1

        currentSessionStartTime = 0L
        currentBookId = 0L
        startPageIndex = 0

        if (startedAt <= 0L || bookId <= 0L) return
        val endTime = System.currentTimeMillis()
        if (endTime - startedAt <= 10_000) return // ignore incidental taps into a book

        writeScope.launch {
            sessionDao.insertSession(
                ReadingSession(
                    bookId = bookId,
                    startTime = startedAt,
                    endTime = endTime,
                    pagesRead = pagesRead
                )
            )
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
