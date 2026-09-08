package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents an eBook in the library with reading progress and metadata.
 */
@Entity(
    tableName = "books",
    indices = [
        Index(value = ["fileHash"], unique = true),
        Index(value = ["readingStatus"]),
        Index(value = ["lastOpened"])
    ]
)
data class Book(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val filePath: String,
    val title: String,
    val author: String,
    val format: String, // "EPUB", "PDF", "TXT", "CBZ"
    val coverPath: String? = null,
    val dateAdded: Long = System.currentTimeMillis(),
    val lastOpened: Long = 0L,
    val progressPercent: Float = 0f, // 0.0 to 100.0
    val progressLocation: String = "0", // Chapter index, page number, or CFI
    val readingStatus: String = "UNREAD", // "UNREAD", "READING", "FINISHED"
    val isFavorite: Boolean = false,
    val fileHash: String = "",
    val totalPages: Int = 1,
    val fileSizeBytes: Long = 0L,
    val currentChapterTitle: String = ""
)
