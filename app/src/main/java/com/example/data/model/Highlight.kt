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
    val colorHex: String = "#FFE082", // Amber Gold, Mint Green, Sky Blue, Rose
    val selectedText: String,
    val note: String? = null,
    val style: String = "HIGHLIGHT", // "HIGHLIGHT", "UNDERLINE", "STRIKETHROUGH"
    val chapterTitle: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
