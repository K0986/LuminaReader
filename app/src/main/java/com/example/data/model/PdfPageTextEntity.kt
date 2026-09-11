package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Text of a single PDF page, extracted once and then reused.
 *
 * Extracting text from a PDF is far too slow to redo on every swipe (and impossible to do at all
 * while the user is offline from the file, e.g. after the shared content URI is revoked), so the
 * reader indexes a book once and reads from this table afterwards. It is what makes reflowed text
 * mode, narration and whole-book search work on PDFs.
 */
@Entity(
    tableName = "pdf_page_text",
    primaryKeys = ["bookId", "pageIndex"],
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
data class PdfPageTextEntity(
    val bookId: Long,
    val pageIndex: Int,
    val text: String,
    val extractedAt: Long = System.currentTimeMillis()
)
