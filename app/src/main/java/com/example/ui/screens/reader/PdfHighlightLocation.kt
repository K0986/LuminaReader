package com.example.ui.screens.reader

/**
 * Encodes where a highlight lives inside a PDF.
 *
 * A PDF has no chapters or CFIs to anchor to, but the platform text layer does give stable
 * character indices per page, so a highlight is stored as `pdf:<page>:<startChar>-<endChar>` and
 * re-resolved to rectangles when the page is shown.
 */
object PdfHighlightLocation {

    private const val PREFIX = "pdf"

    fun encode(pageIndex: Int, startChar: Int, endChar: Int): String =
        "$PREFIX:$pageIndex:$startChar-$endChar"

    data class Decoded(val pageIndex: Int, val startChar: Int, val endChar: Int)

    fun decode(location: String): Decoded? {
        val parts = location.split(":")
        if (parts.size != 3 || parts[0] != PREFIX) return null
        val page = parts[1].toIntOrNull() ?: return null
        val range = parts[2].split("-")
        if (range.size != 2) return null
        val start = range[0].toIntOrNull() ?: return null
        val end = range[1].toIntOrNull() ?: return null
        return Decoded(page, minOf(start, end), maxOf(start, end))
    }
}
