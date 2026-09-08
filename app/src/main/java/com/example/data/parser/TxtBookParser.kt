package com.example.data.parser

import android.content.Context
import android.net.Uri
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader

object TxtBookParser {

    fun parse(context: Context, uri: Uri, fallbackTitle: String): ParsedBook {
        val inputStream: InputStream? = if (uri.scheme == "file") {
            File(uri.path ?: "").inputStream()
        } else {
            context.contentResolver.openInputStream(uri)
        }

        val textLines = mutableListOf<String>()
        inputStream?.use { stream ->
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).useLines { lines ->
                lines.forEach { textLines.add(it) }
            }
        }

        if (textLines.isEmpty()) {
            return ParsedBook(
                title = fallbackTitle,
                author = "Unknown Author",
                format = "TXT"
            )
        }

        // Title and author heuristic from first 10 lines
        var title = fallbackTitle
        var author = "Unknown Author"

        for (i in 0 until minOf(15, textLines.size)) {
            val line = textLines[i].trim()
            if (line.startsWith("Title:", ignoreCase = true)) {
                title = line.substringAfter(":").trim()
            } else if (line.startsWith("Author:", ignoreCase = true) || line.startsWith("By:", ignoreCase = true)) {
                author = line.substringAfter(":").trim()
            }
        }

        // Detect chapters by regex
        val chapterRegex = "^(?:Chapter|CHAPTER|Book|BOOK|Part|PART|ACT|Act|SCENE|Scene|SECTION|Section|PROLOGUE|EPILOGUE)\\b.*".toRegex()
        val romanNumeralRegex = "^(?:[IVXLCDM]+)[.:]?$".toRegex()

        val chapters = mutableListOf<SpineChapter>()
        val tocItems = mutableListOf<TocItem>()

        var currentChapterTitle = "Beginning"
        val currentParagraphs = mutableListOf<String>()
        var currentParagraphBuilder = StringBuilder()

        fun flushChapter() {
            if (currentParagraphBuilder.isNotBlank()) {
                currentParagraphs.add(currentParagraphBuilder.toString().trim())
                currentParagraphBuilder = StringBuilder()
            }
            if (currentParagraphs.isNotEmpty()) {
                val fullText = currentParagraphs.joinToString("\n\n")
                val chapterIndex = chapters.size
                chapters.add(
                    SpineChapter(
                        id = "txt_ch_$chapterIndex",
                        title = currentChapterTitle,
                        plainText = fullText,
                        formattedParagraphs = currentParagraphs.toList(),
                        wordCount = fullText.split("\\s+".toRegex()).count()
                    )
                )
                tocItems.add(
                    TocItem(
                        id = "txt_ch_$chapterIndex",
                        title = currentChapterTitle,
                        targetIndex = chapterIndex
                    )
                )
                currentParagraphs.clear()
            }
        }

        for (line in textLines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                if (currentParagraphBuilder.isNotBlank()) {
                    currentParagraphs.add(currentParagraphBuilder.toString().trim())
                    currentParagraphBuilder = StringBuilder()
                }
            } else if ((chapterRegex.matches(trimmed) || romanNumeralRegex.matches(trimmed)) && trimmed.length < 80) {
                flushChapter()
                currentChapterTitle = trimmed
            } else {
                if (currentParagraphBuilder.isNotEmpty()) {
                    currentParagraphBuilder.append(" ")
                }
                currentParagraphBuilder.append(trimmed)
            }
        }
        flushChapter()

        if (chapters.isEmpty()) {
            val fullText = textLines.joinToString("\n")
            chapters.add(
                SpineChapter(
                    id = "txt_single",
                    title = title,
                    plainText = fullText,
                    formattedParagraphs = listOf(fullText),
                    wordCount = fullText.split("\\s+".toRegex()).count()
                )
            )
            tocItems.add(TocItem("txt_single", title, 0))
        }

        val totalPages = (chapters.sumOf { it.plainText.length } / 1500).coerceAtLeast(chapters.size)

        return ParsedBook(
            title = title,
            author = author,
            format = "TXT",
            tableOfContents = tocItems,
            chapters = chapters,
            totalPagesEstimate = totalPages
        )
    }
}
