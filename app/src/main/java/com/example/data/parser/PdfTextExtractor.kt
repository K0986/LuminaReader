package com.example.data.parser

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.util.Log
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

/**
 * Text layer for a single PDF page.
 *
 * [text] is null when the page genuinely has no machine-readable text -- a scanned
 * image, a pure-vector diagram, or a document whose fonts we cannot decode. That is a
 * meaningfully different state from "empty page", and callers are expected to tell the
 * two apart rather than substituting a placeholder.
 */
data class PdfPageText(
    val pageIndex: Int,
    val text: String?,
    val paragraphs: List<String>,
    val source: Source
) {
    enum class Source {
        /** Platform text extraction (API 35+). Font-correct, honours /ToUnicode. */
        PLATFORM,

        /** Best-effort content-stream parsing. Approximate for unusual encodings. */
        CONTENT_STREAM,

        /** No text could be recovered. */
        NONE
    }

    val hasText: Boolean get() = !text.isNullOrBlank()
}

/**
 * Extracts the text layer of a PDF one page at a time.
 *
 * The previous implementation read the whole file into a single ISO-8859-1 `String`,
 * ran several regexes across it, and gave up entirely for documents over 30 pages or
 * 3 MB -- substituting the literal string "Page 3 of 412" as if it were the page's
 * prose. That placeholder then flowed into text mode, text-to-speech and in-book
 * search, so a 400-page textbook would be narrated as "page one of four hundred and
 * twelve, page two of four hundred and twelve".
 *
 * This version is page-scoped and honest: it asks the platform first, falls back to
 * parsing only the content streams belonging to the requested page, and reports
 * [PdfPageText.Source.NONE] when it has nothing, so the UI can say so.
 */
object PdfTextExtractor {

    private const val TAG = "PdfTextExtractor"

    /** Above this size we skip the legacy whole-file scan; the platform path still works. */
    private const val LEGACY_SCAN_BYTE_LIMIT = 16L * 1024 * 1024

    fun extractPage(context: Context, uri: Uri, pageIndex: Int): PdfPageText {
        platformExtract(context, uri, pageIndex)?.let { return it }
        return contentStreamExtract(context, uri, pageIndex)
    }

    // ---------------------------------------------------------------- platform path

    /**
     * Uses [PdfRenderer.Page.getTextContents], added in API 35. The platform applies the
     * page's font encoding and /ToUnicode CMap, so ligatures and non-Latin scripts come
     * out correct -- exactly what a hand-rolled parser gets wrong.
     */
    private fun platformExtract(context: Context, uri: Uri, pageIndex: Int): PdfPageText? {
        if (Build.VERSION.SDK_INT < 35) return null
        return try {
            PdfBookParser.openFileDescriptor(context, uri)?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (pageIndex !in 0 until renderer.pageCount) return null
                    renderer.openPage(pageIndex).use { page ->
                        val joined = page.textContents
                            .mapNotNull { it.text.takeIf(String::isNotBlank) }
                            .joinToString("\n")
                            .trim()
                        if (joined.isEmpty()) null
                        else PdfPageText(
                            pageIndex = pageIndex,
                            text = joined,
                            paragraphs = splitParagraphs(joined),
                            source = PdfPageText.Source.PLATFORM
                        )
                    }
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Platform text extraction failed for page $pageIndex", t)
            null
        }
    }

    // ----------------------------------------------------------- content-stream path

    private fun contentStreamExtract(context: Context, uri: Uri, pageIndex: Int): PdfPageText {
        val empty = PdfPageText(pageIndex, null, emptyList(), PdfPageText.Source.NONE)
        val bytes = readAllBytes(context, uri) ?: return empty

        return try {
            val document = LegacyPdfDocument.parse(bytes)
            val text = document.pageText(pageIndex)?.trim()
            if (text.isNullOrEmpty()) empty
            else PdfPageText(
                pageIndex = pageIndex,
                text = text,
                paragraphs = splitParagraphs(text),
                source = PdfPageText.Source.CONTENT_STREAM
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Content-stream extraction failed for page $pageIndex", t)
            empty
        }
    }

    private fun readAllBytes(context: Context, uri: Uri): ByteArray? {
        return try {
            val size = try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
            } catch (ignored: Throwable) {
                -1L
            }
            if (size > LEGACY_SCAN_BYTE_LIMIT) {
                Log.i(TAG, "Skipping legacy scan for $size byte document")
                return null
            }
            val stream = context.contentResolver.openInputStream(uri)
                ?: java.io.File(uri.path ?: return null).takeIf { it.exists() }?.inputStream()
            stream?.use { it.readBytes() }
        } catch (t: Throwable) {
            Log.w(TAG, "Could not read PDF bytes", t)
            null
        }
    }

    internal fun splitParagraphs(text: String): List<String> {
        val paragraphs = text.split(Regex("\n{2,}"))
            .map { it.replace(Regex("[ \t]+"), " ").trim() }
            .filter { it.isNotBlank() }
        return paragraphs.ifEmpty { listOf(text.trim()) }
    }

    /**
     * A deliberately small PDF object model: enough to walk from the page tree to a
     * page's content streams, and no more.
     *
     * Working on the raw [ByteArray] (rather than a lossy `String`) matters, because
     * stream payloads are binary and `endobj` can legitimately appear inside them.
     */
    internal class LegacyPdfDocument private constructor(
        private val bytes: ByteArray,
        private val objects: Map<Int, IntRange>
    ) {

        companion object {
            private val OBJ_HEADER = Regex("""(\d+)\s+(\d+)\s+obj""")

            fun parse(bytes: ByteArray): LegacyPdfDocument {
                // Index every "N G obj" header by scanning the byte array once. Offsets
                // are recovered directly rather than trusting the xref table, which may
                // itself be a compressed stream we cannot read.
                val ascii = String(bytes, Charsets.ISO_8859_1)
                val offsets = LinkedHashMap<Int, Int>()
                for (match in OBJ_HEADER.findAll(ascii)) {
                    val id = match.groupValues[1].toIntOrNull() ?: continue
                    offsets[id] = match.range.last + 1
                }
                val ends = HashMap<Int, Int>()
                for ((id, start) in offsets) {
                    val end = ascii.indexOf("endobj", start).let { if (it == -1) ascii.length else it }
                    ends[id] = end
                }
                return LegacyPdfDocument(bytes, offsets.mapValues { (id, start) -> start until ends.getValue(id) })
            }
        }

        private val ascii: String = String(bytes, Charsets.ISO_8859_1)

        private fun body(id: Int): String? = objects[id]?.let { ascii.substring(it.first, it.last + 1) }

        /** Page objects in document order, as object ids. */
        private val pageObjectIds: List<Int> by lazy {
            objects.keys.filter { id ->
                val b = body(id) ?: return@filter false
                Regex("""/Type\s*/Page[^s]""").containsMatchIn(b) ||
                    Regex("""/Type\s*/Page\s*(>>|/)""").containsMatchIn(b)
            }
        }

        fun pageText(pageIndex: Int): String? {
            val pageId = pageObjectIds.getOrNull(pageIndex) ?: return null
            val pageBody = body(pageId) ?: return null

            val builder = StringBuilder()
            for (contentId in contentStreamIds(pageBody)) {
                val decoded = decodeStream(contentId) ?: continue
                val pageChunk = extractTextOperators(decoded)
                if (pageChunk.isNotBlank()) {
                    if (builder.isNotEmpty()) builder.append('\n')
                    builder.append(pageChunk)
                }
            }
            return builder.toString().takeIf { it.isNotBlank() }
        }

        /** `/Contents 12 0 R` or `/Contents [12 0 R 13 0 R]`. */
        private fun contentStreamIds(pageBody: String): List<Int> {
            val single = Regex("""/Contents\s+(\d+)\s+\d+\s+R""").find(pageBody)
            if (single != null) return listOf(single.groupValues[1].toInt())

            val array = Regex("""/Contents\s*\[([^\]]*)\]""").find(pageBody) ?: return emptyList()
            return Regex("""(\d+)\s+\d+\s+R""").findAll(array.groupValues[1])
                .mapNotNull { it.groupValues[1].toIntOrNull() }
                .toList()
        }

        /**
         * Returns the decompressed payload of an object's stream.
         *
         * The stream's byte offset is computed from the object's position in the *byte*
         * array so that binary payloads survive intact; the old code round-tripped them
         * through a `String`, which silently mangled anything outside ISO-8859-1.
         */
        private fun decodeStream(id: Int): String? {
            val range = objects[id] ?: return null
            val body = body(id) ?: return null

            val keywordIdx = body.indexOf("stream")
            if (keywordIdx == -1) return null

            var payloadStart = range.first + keywordIdx + "stream".length
            if (payloadStart < bytes.size && bytes[payloadStart] == '\r'.code.toByte()) payloadStart++
            if (payloadStart < bytes.size && bytes[payloadStart] == '\n'.code.toByte()) payloadStart++

            val endIdx = ascii.indexOf("endstream", payloadStart)
            if (endIdx == -1 || endIdx <= payloadStart) return null

            val declaredLength = streamLength(body)
            val payloadEnd = when {
                declaredLength != null && payloadStart + declaredLength <= endIdx -> payloadStart + declaredLength
                else -> endIdx
            }
            val payload = bytes.copyOfRange(payloadStart, payloadEnd)

            val filters = Regex("""/Filter\s*(/\w+|\[[^\]]*\])""").find(body)?.groupValues?.get(1) ?: ""
            return when {
                filters.contains("FlateDecode") -> inflate(payload)
                filters.isEmpty() -> String(payload, Charsets.ISO_8859_1)
                // ASCIIHex/LZW/DCT and friends are out of scope; report nothing rather
                // than emitting binary noise that looks like text.
                else -> null
            }
        }

        /**
         * The stream's `/Length`, resolving the indirect form.
         *
         * `/Length 3866` is a direct integer, but `/Length 5 0 R` points at another
         * object that holds the number. Reading the first integer in either case -- as
         * the previous code did -- turns `/Length 5 0 R` into a five-byte payload, and
         * inflating five bytes of a deflate stream fails. That single mistake was enough
         * to lose the text of any PDF written with indirect stream lengths.
         */
        private fun streamLength(body: String): Int? {
            val indirect = Regex("""/Length\s+(\d+)\s+(\d+)\s+R""").find(body)
            if (indirect != null) {
                val target = indirect.groupValues[1].toIntOrNull() ?: return null
                return body(target)?.let { Regex("""\d+""").find(it)?.value?.toIntOrNull() }
            }
            return Regex("""/Length\s+(\d+)""").find(body)?.groupValues?.get(1)?.toIntOrNull()
        }

        private fun inflate(data: ByteArray): String? {
            runCatching {
                InflaterInputStream(ByteArrayInputStream(data)).use { input ->
                    return String(input.readBytes(), Charsets.ISO_8859_1)
                }
            }
            runCatching {
                val inflater = Inflater(true)
                inflater.setInput(data)
                val out = ByteArrayOutputStream()
                val buf = ByteArray(8192)
                while (!inflater.finished()) {
                    val n = inflater.inflate(buf)
                    if (n == 0) break
                    out.write(buf, 0, n)
                }
                inflater.end()
                return String(out.toByteArray(), Charsets.ISO_8859_1)
            }
            return null
        }
    }

    // -------------------------------------------------------------- operator parsing

    private val BT_BLOCK = Regex("""BT([\s\S]*?)ET""")
    private val SHOW_OPERATOR = Regex(
        """\[([\s\S]*?)\]\s*TJ|(\((?:[^()\\]|\\[\s\S])*\)|<[0-9a-fA-F\s]*>)\s*(Tj|'|")|(T\*|Td|TD)"""
    )
    private val TJ_TOKEN = Regex("""\((?:[^()\\]|\\[\s\S])*\)|<[0-9a-fA-F\s]*>|-?\d*\.?\d+""")

    /**
     * Walks the text-showing operators of a content stream in a single left-to-right
     * pass. The previous implementation restarted three separate regex searches at every
     * character position, which was quadratic and could emit operators out of order.
     */
    internal fun extractTextOperators(content: String): String {
        val out = StringBuilder()
        for (block in BT_BLOCK.findAll(content)) {
            val line = StringBuilder()
            for (op in SHOW_OPERATOR.findAll(block.groupValues[1])) {
                when {
                    op.groupValues[1].isNotEmpty() -> line.append(decodeTjArray(op.groupValues[1]))
                    op.groupValues[2].isNotEmpty() -> {
                        // ' and " move to the next line before showing their string.
                        if (op.groupValues[3] != "Tj" && line.isNotEmpty()) {
                            out.append(line.toString().trim()).append('\n')
                            line.setLength(0)
                        }
                        line.append(decodePdfString(op.groupValues[2]))
                    }
                    op.groupValues[4].isNotEmpty() -> {
                        if (line.isNotEmpty()) {
                            out.append(line.toString().trim()).append('\n')
                            line.setLength(0)
                        }
                    }
                }
            }
            if (line.isNotEmpty()) out.append(line.toString().trim()).append('\n')
            out.append('\n')
        }
        return out.toString().trim()
    }

    private fun decodeTjArray(inner: String): String {
        val sb = StringBuilder()
        for (token in TJ_TOKEN.findAll(inner)) {
            val value = token.value
            if (value.startsWith("(") || value.startsWith("<")) {
                sb.append(decodePdfString(value))
            } else {
                // A large negative kern is how PDF encodes an inter-word gap.
                val kern = value.toDoubleOrNull() ?: 0.0
                if (kern < -120.0 && sb.isNotEmpty() && !sb.endsWith(" ")) sb.append(' ')
            }
        }
        return sb.toString()
    }

    internal fun decodePdfString(raw: String): String {
        if (raw.startsWith("<") && raw.endsWith(">")) {
            val hex = raw.substring(1, raw.length - 1).filter { !it.isWhitespace() }
            val sb = StringBuilder()
            // Hex strings are usually UTF-16BE glyph indices. Without the font's
            // /ToUnicode map we cannot resolve those, so only accept the byte pairs that
            // land in printable ASCII and drop the rest -- better a short string than
            // the mojibake the old code produced.
            var i = 0
            while (i + 1 < hex.length) {
                val code = hex.substring(i, i + 2).toIntOrNull(16)
                if (code != null && (code == 0x0A || code == 0x09 || code in 0x20..0x7E)) {
                    sb.append(code.toChar())
                }
                i += 2
            }
            return sb.toString()
        }

        if (!raw.startsWith("(") || !raw.endsWith(")")) return raw

        val content = raw.substring(1, raw.length - 1)
        val sb = StringBuilder()
        var i = 0
        while (i < content.length) {
            val c = content[i]
            if (c != '\\' || i + 1 >= content.length) {
                sb.append(c)
                i++
                continue
            }
            when (val next = content[i + 1]) {
                'n' -> { sb.append('\n'); i += 2 }
                'r' -> { sb.append('\r'); i += 2 }
                't' -> { sb.append('\t'); i += 2 }
                'b' -> { sb.append('\b'); i += 2 }
                'f' -> { sb.append('\u000C'); i += 2 }
                '\n' -> i += 2 // escaped newline: line continuation, emits nothing
                in '0'..'7' -> {
                    var oct = "$next"
                    var j = i + 2
                    while (j < content.length && oct.length < 3 && content[j] in '0'..'7') {
                        oct += content[j]
                        j++
                    }
                    oct.toIntOrNull(8)?.let { sb.append(it.toChar()) }
                    i = j
                }
                else -> { sb.append(next); i += 2 }
            }
        }
        return sb.toString()
    }
}
