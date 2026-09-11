package com.example.data.parser.pdf

/**
 * A rectangle expressed in PDF page coordinates (points, origin at the top-left of the page as
 * reported by the platform PDF APIs).
 *
 * Kept free of `android.graphics` types so the geometry helpers can be unit tested on the JVM.
 */
data class PdfRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun contains(x: Float, y: Float): Boolean =
        x >= left && x <= right && y >= top && y <= bottom

    fun inflate(dx: Float, dy: Float): PdfRect =
        PdfRect(left - dx, top - dy, right + dx, bottom + dy)
}

data class PdfPoint(val x: Float, val y: Float)

/** Size of a PDF page in points. */
data class PdfPageSize(val widthPt: Float, val heightPt: Float)

/** One run of text on a page together with the rectangles it occupies. */
data class PdfTextBlock(
    val text: String,
    val rects: List<PdfRect> = emptyList()
)

/** All text found on a single page. */
data class PdfPageText(
    val pageIndex: Int,
    val blocks: List<PdfTextBlock>
) {
    val text: String = blocks.joinToString("\n") { it.text }.trim()

    val hasText: Boolean get() = text.isNotBlank()
}

/**
 * A resolved selection on a page. [startChar] / [endChar] are the platform's character indices
 * within the page, which is what we hand back to the platform when the user drags a handle.
 */
data class PdfSelection(
    val pageIndex: Int,
    val text: String,
    val startChar: Int,
    val endChar: Int,
    val rects: List<PdfRect>
)

/** A search hit inside a page. */
data class PdfPageMatch(
    val pageIndex: Int,
    val startChar: Int,
    val rects: List<PdfRect>
)

/**
 * What the device can do with the text layer of a PDF.
 *
 * - [NONE]: no text could be read at all (scanned images, or an unsupported platform).
 * - [TEXT_ONLY]: we can read the words but not where they sit on the page, so selection has to
 *   happen in reflowed text mode.
 * - [TEXT_AND_SELECTION]: the platform gives us glyph boxes, so words can be selected directly on
 *   the rendered page.
 */
enum class PdfTextCapability {
    NONE,
    TEXT_ONLY,
    TEXT_AND_SELECTION
}
