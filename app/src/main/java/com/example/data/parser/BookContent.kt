package com.example.data.parser

import android.graphics.Bitmap

data class TocItem(
    val id: String,
    val title: String,
    val targetIndex: Int, // Chapter index or page index
    val level: Int = 0
)

data class SpineChapter(
    val id: String,
    val title: String,
    val plainText: String,
    val formattedParagraphs: List<String>,
    val wordCount: Int = 0
)

data class ParsedBook(
    val title: String,
    val author: String,
    val format: String,
    val coverBitmap: Bitmap? = null,
    val coverBytes: ByteArray? = null,
    val tableOfContents: List<TocItem> = emptyList(),
    val chapters: List<SpineChapter> = emptyList(),
    val totalPagesEstimate: Int = 1
)
