package com.example.data.parser

import android.content.Context
import android.net.Uri
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

object PdfTextExtractor {

    data class ExtractedPage(
        val pageNumber: Int,
        val text: String,
        val paragraphs: List<String>
    )

    /**
     * Extracts text from a PDF file per page.
     */
    fun extractPages(context: Context, uri: Uri, pageCount: Int): List<ExtractedPage> {
        val result = mutableListOf<ExtractedPage>()

        // For books with many pages (e.g. Java textbook with 500+ pages), do not attempt whole-file regex scanning
        if (pageCount > 30) {
            for (i in 0 until pageCount) {
                val pageNum = i + 1
                result.add(
                    ExtractedPage(
                        pageNumber = pageNum,
                        text = "Page $pageNum of $pageCount",
                        paragraphs = listOf("Page $pageNum of $pageCount")
                    )
                )
            }
            return result
        }

        var inputStream: InputStream? = null

        try {
            // Check file size first to avoid reading large files into memory
            var fileSize = 0L
            try {
                if (uri.scheme == "file" || uri.scheme.isNullOrEmpty()) {
                    val f = java.io.File(uri.path ?: "")
                    if (f.exists()) fileSize = f.length()
                } else {
                    context.contentResolver.openFileDescriptor(uri, "r")?.use {
                        fileSize = it.statSize
                    }
                }
            } catch (ignored: Throwable) {}

            // If file is larger than 3MB, skip whole-file in-memory regex parsing
            if (fileSize > 3 * 1024 * 1024L) {
                for (i in 0 until pageCount) {
                    val pageNum = i + 1
                    result.add(
                        ExtractedPage(
                            pageNumber = pageNum,
                            text = "Page $pageNum of $pageCount",
                            paragraphs = listOf("Page $pageNum of $pageCount")
                        )
                    )
                }
                return result
            }

            inputStream = context.contentResolver.openInputStream(uri)
                ?: java.io.File(uri.path ?: "").takeIf { it.exists() }?.inputStream()

            if (inputStream != null) {
                val bytes = inputStream.use { it.readBytes() }
                val pagesText = parsePdfBytes(bytes, pageCount)
                for (i in 0 until pageCount) {
                    val pageNum = i + 1
                    val text = pagesText.getOrNull(i)?.trim() ?: ""
                    val cleanText = if (text.isNotBlank()) text else "Page $pageNum of $pageCount"
                    val paragraphs = cleanText.split("\n{2,}".toRegex())
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                    result.add(
                        ExtractedPage(
                            pageNumber = pageNum,
                            text = cleanText,
                            paragraphs = if (paragraphs.isNotEmpty()) paragraphs else listOf(cleanText)
                        )
                    )
                }
            }
        } catch (t: Throwable) {
            // Fallback gracefully on any error or OOM
            android.util.Log.w("PdfTextExtractor", "Safe fallback for PDF text: ${t.message}")
        } finally {
            try { inputStream?.close() } catch (e: Exception) {}
        }

        // Ensure at least pageCount entries
        if (result.size < pageCount) {
            for (i in result.size until pageCount) {
                val pageNum = i + 1
                result.add(
                    ExtractedPage(
                        pageNumber = pageNum,
                        text = "Page $pageNum of $pageCount",
                        paragraphs = listOf("Page $pageNum of $pageCount")
                    )
                )
            }
        }
        return result
    }

    private fun parsePdfBytes(bytes: ByteArray, pageCount: Int): List<String> {
        val pdfString = String(bytes, Charsets.ISO_8859_1)

        // 1. Locate all stream ... endstream chunks
        val streams = mutableListOf<String>()
        val streamRegex = Regex("""stream\r?\n([\s\S]*?)\r?\nendstream""")
        val objectRegex = Regex("""(\d+)\s+(\d+)\s+obj([\s\S]*?)endobj""")

        val objectStreams = mutableMapOf<Int, String>()

        for (match in objectRegex.findAll(pdfString)) {
            val objId = match.groupValues[1].toIntOrNull() ?: continue
            val objBody = match.groupValues[3]
            val isFlate = objBody.contains("/FlateDecode")

            val streamStartIdx = objBody.indexOf("stream")
            val streamEndIdx = objBody.lastIndexOf("endstream")

            if (streamStartIdx != -1 && streamEndIdx != -1 && streamEndIdx > streamStartIdx) {
                // Find actual byte offset in original bytes to prevent character encoding corruptions
                val streamMatch = streamRegex.find(objBody)
                if (streamMatch != null) {
                    val rawStreamContent = streamMatch.groupValues[1]
                    val streamBytes = rawStreamContent.toByteArray(Charsets.ISO_8859_1)
                    val decompressed = if (isFlate) {
                        decompressFlate(streamBytes) ?: String(streamBytes, Charsets.ISO_8859_1)
                    } else {
                        String(streamBytes, Charsets.ISO_8859_1)
                    }

                    val extractedText = extractTextFromPdfStream(decompressed)
                    if (extractedText.isNotBlank()) {
                        objectStreams[objId] = extractedText
                        streams.add(extractedText)
                    }
                }
            }
        }

        // 2. Try to map /Page objects to their /Contents stream
        val pageObjRegex = Regex("""/Type\s*/Page\b[\s\S]*?/Contents\s*(\d+)\s+\d+\s+R""")
        val pageMatches = pageObjRegex.findAll(pdfString).toList()

        if (pageMatches.size >= pageCount && pageMatches.isNotEmpty()) {
            val pageTexts = mutableListOf<String>()
            for (pm in pageMatches.take(pageCount)) {
                val contentObjId = pm.groupValues[1].toIntOrNull()
                val text = if (contentObjId != null) objectStreams[contentObjId] ?: "" else ""
                pageTexts.add(text)
            }
            if (pageTexts.any { it.isNotBlank() }) {
                return pageTexts
            }
        }

        // 3. Fallback: distribute extracted text streams across pages
        if (streams.isNotEmpty()) {
            if (streams.size == pageCount) {
                return streams
            }
            val chunkSize = (streams.size + pageCount - 1) / pageCount.coerceAtLeast(1)
            val result = mutableListOf<String>()
            for (i in 0 until pageCount) {
                val start = i * chunkSize
                val end = ((i + 1) * chunkSize).coerceAtMost(streams.size)
                if (start < streams.size) {
                    result.add(streams.subList(start, end).joinToString("\n\n"))
                } else {
                    result.add("")
                }
            }
            return result
        }

        return emptyList()
    }

    private fun decompressFlate(bytes: ByteArray): String? {
        // Try standard zlib header
        try {
            val inflater = InflaterInputStream(ByteArrayInputStream(bytes))
            val out = ByteArrayOutputStream()
            val buf = ByteArray(2048)
            var n: Int
            while (inflater.read(buf).also { n = it } > 0) {
                out.write(buf, 0, n)
            }
            return String(out.toByteArray(), Charsets.UTF_8)
        } catch (e: Exception) {}

        // Try raw deflate without header
        try {
            val inflater = Inflater(true)
            inflater.setInput(bytes)
            val out = ByteArrayOutputStream()
            val buf = ByteArray(2048)
            while (!inflater.finished()) {
                val count = inflater.inflate(buf)
                if (count == 0) break
                out.write(buf, 0, count)
            }
            inflater.end()
            return String(out.toByteArray(), Charsets.UTF_8)
        } catch (e: Exception) {}

        return null
    }

    /**
     * Parses PDF operators inside a decompressed content stream (BT ... ET).
     */
    private fun extractTextFromPdfStream(content: String): String {
        val result = StringBuilder()
        val btRegex = Regex("""BT([\s\S]*?)ET""")

        for (bt in btRegex.findAll(content)) {
            val block = bt.groupValues[1]
            var lineBuilder = StringBuilder()

            // 1. Array TJ operator: [(str) 20 (str2)] TJ
            val tjArrayRegex = Regex("""\[([\s\S]*?)\]\s*TJ""")
            // 2. Single string Tj operator: (str) Tj or <hex> Tj
            val singleTjRegex = Regex("""(\((?:[^()\\]|\\.)*\)|<[0-9a-fA-F]+>)\s*Tj""")
            // 3. Move and show: (str) '
            val quoteRegex = Regex("""(\((?:[^()\\]|\\.)*\)|<[0-9a-fA-F]+>)\s*'""")

            var pos = 0
            val blockLength = block.length

            while (pos < blockLength) {
                // Line breaks in PDF
                if (block.startsWith("T*", pos) || block.startsWith("TD", pos) || block.startsWith("Td", pos)) {
                    if (lineBuilder.isNotEmpty()) {
                        result.append(lineBuilder.toString().trim()).append("\n")
                        lineBuilder = StringBuilder()
                    }
                    pos += 2
                    continue
                }

                // Check for [ ... ] TJ
                val arrayMatch = tjArrayRegex.find(block, pos)
                val singleMatch = singleTjRegex.find(block, pos)
                val quoteMatch = quoteRegex.find(block, pos)

                val nextMatch = listOfNotNull(arrayMatch, singleMatch, quoteMatch)
                    .filter { it.range.first >= pos }
                    .minByOrNull { it.range.first }

                if (nextMatch != null && nextMatch.range.first == pos) {
                    when (nextMatch) {
                        arrayMatch -> {
                            val inner = arrayMatch.groupValues[1]
                            val textFromTJ = parseTJArray(inner)
                            if (textFromTJ.isNotBlank()) {
                                if (lineBuilder.isNotEmpty() && !lineBuilder.endsWith(" ")) {
                                    lineBuilder.append(" ")
                                }
                                lineBuilder.append(textFromTJ)
                            }
                            pos = arrayMatch.range.last + 1
                        }
                        singleMatch -> {
                            val raw = singleMatch.groupValues[1]
                            val decoded = decodePdfString(raw)
                            if (decoded.isNotBlank()) {
                                if (lineBuilder.isNotEmpty() && !lineBuilder.endsWith(" ")) {
                                    lineBuilder.append(" ")
                                }
                                lineBuilder.append(decoded)
                            }
                            pos = singleMatch.range.last + 1
                        }
                        quoteMatch -> {
                            val raw = quoteMatch.groupValues[1]
                            val decoded = decodePdfString(raw)
                            if (lineBuilder.isNotEmpty()) {
                                result.append(lineBuilder.toString().trim()).append("\n")
                                lineBuilder = StringBuilder()
                            }
                            lineBuilder.append(decoded)
                            pos = quoteMatch.range.last + 1
                        }
                        else -> pos++
                    }
                } else {
                    pos++
                }
            }

            if (lineBuilder.isNotEmpty()) {
                result.append(lineBuilder.toString().trim()).append("\n\n")
            }
        }

        return result.toString().trim()
    }

    private fun parseTJArray(tjContent: String): String {
        val sb = StringBuilder()
        val tokenRegex = Regex("""(\((?:[^()\\]|\\.)*\)|<[0-9a-fA-F]+>|[-+]?\d*\.?\d+)""")

        for (token in tokenRegex.findAll(tjContent)) {
            val str = token.value
            when {
                str.startsWith("(") || str.startsWith("<") -> {
                    sb.append(decodePdfString(str))
                }
                else -> {
                    val num = str.toDoubleOrNull() ?: 0.0
                    // Kerning threshold in PDF points indicating word space
                    if (num < -120.0 && sb.isNotEmpty() && !sb.endsWith(" ")) {
                        sb.append(" ")
                    }
                }
            }
        }
        return sb.toString()
    }

    private fun decodePdfString(raw: String): String {
        if (raw.startsWith("<") && raw.endsWith(">")) {
            val hex = raw.substring(1, raw.length - 1).trim()
            val sb = StringBuilder()
            var i = 0
            while (i < hex.length - 1) {
                try {
                    val code = hex.substring(i, i + 2).toInt(16)
                    sb.append(code.toChar())
                } catch (e: Exception) {}
                i += 2
            }
            return sb.toString()
        }

        if (raw.startsWith("(") && raw.endsWith(")")) {
            val content = raw.substring(1, raw.length - 1)
            val sb = StringBuilder()
            var i = 0
            val len = content.length
            while (i < len) {
                val c = content[i]
                if (c == '\\' && i + 1 < len) {
                    when (val next = content[i + 1]) {
                        'n' -> { sb.append('\n'); i += 2 }
                        'r' -> { sb.append('\r'); i += 2 }
                        't' -> { sb.append('\t'); i += 2 }
                        'b' -> { sb.append('\b'); i += 2 }
                        'f' -> { sb.append('\u000C'); i += 2 }
                        '(' -> { sb.append('('); i += 2 }
                        ')' -> { sb.append(')'); i += 2 }
                        '\\' -> { sb.append('\\'); i += 2 }
                        in '0'..'7' -> {
                            var oct = "$next"
                            var j = i + 2
                            while (j < len && j < i + 4 && content[j] in '0'..'7') {
                                oct += content[j]
                                j++
                            }
                            try {
                                sb.append(oct.toInt(8).toChar())
                            } catch (e: Exception) {
                                sb.append(oct)
                            }
                            i = j
                        }
                        else -> { sb.append(next); i += 2 }
                    }
                } else {
                    sb.append(c)
                    i++
                }
            }
            return sb.toString()
        }

        return raw
    }
}
