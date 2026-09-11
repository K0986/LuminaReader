package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "highlights",
    foreignKeys = [
        ForeignKey(
            entity = Book::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["bookId"])]
)
data class Highlight(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bookId: Long,
    val location: String, // paragraph or page index
    val pageIndex: Int = 0,
    /**
     * Where the highlight sits inside its page, as a paragraph index plus a half-open
     * character range. Previously only [selectedText] was stored, and the reader
     * re-discovered highlights with `highlights.find { it.selectedText == paragraph }`
     * -- whole-paragraph string equality. Any highlight narrower than a full paragraph
     * therefore saved fine but never rendered again.
     */
    val paragraphIndex: Int = -1,
    val startOffset: Int = -1,
    val endOffset: Int = -1,
    val colorHex: String = "#FFE082", // Amber Gold, Mint Green, Sky Blue, Rose
    val selectedText: String,
    val note: String? = null,
    val style: String = "HIGHLIGHT", // "HIGHLIGHT", "UNDERLINE", "STRIKETHROUGH"
    val chapterTitle: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
