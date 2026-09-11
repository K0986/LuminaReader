package com.example.ui.screens.reader

/**
 * Character-range arithmetic behind text selection.
 *
 * Selection used to be stored as *word indices* into a whitespace-split copy of the paragraph,
 * which meant every renderer had to split the text the same way and punctuation drifted between the
 * displayed text and the selected text. Storing a character range instead keeps a single source of
 * truth — the paragraph string itself — and lets the renderer map touches to offsets with
 * Compose's own text layout.
 *
 * All functions here are pure so the tricky boundary cases are covered by unit tests.
 */
object TextSelectionMath {

    /** Characters that belong to a word (letters, digits and intra-word punctuation). */
    private fun isWordChar(c: Char): Boolean = c.isLetterOrDigit() || c == '\'' || c == '’' || c == '-'

    /**
     * Expands [offset] to the word around it.
     *
     * Returns null when the offset does not land on a word (e.g. the reader tapped a run of
     * spaces), so callers can leave the current selection alone instead of selecting nothing.
     */
    fun wordRangeAt(text: String, offset: Int): IntRange? {
        if (text.isEmpty()) return null
        val index = offset.coerceIn(0, text.length - 1)

        // A tap just past the end of a word (the common case when tapping its right edge) should
        // still select that word.
        val anchor = when {
            isWordChar(text[index]) -> index
            index > 0 && isWordChar(text[index - 1]) -> index - 1
            else -> return null
        }

        var start = anchor
        while (start > 0 && isWordChar(text[start - 1])) start--
        var end = anchor
        while (end < text.length - 1 && isWordChar(text[end + 1])) end++
        return start..end
    }

    /** The sentence containing [offset], used by the "sentence" shortcut in the action bar. */
    fun sentenceRangeAt(text: String, offset: Int): IntRange? {
        if (text.isEmpty()) return null
        val index = offset.coerceIn(0, text.length - 1)

        var start = index
        while (start > 0 && !isSentenceEnd(text[start - 1])) start--
        while (start < text.length && text[start].isWhitespace()) start++

        var end = index
        while (end < text.length - 1 && !isSentenceEnd(text[end])) end++

        return if (start <= end) start..end else null
    }

    private fun isSentenceEnd(c: Char): Boolean = c == '.' || c == '!' || c == '?'

    /**
     * Moves the start of a selection by [words] whole words (negative grows the selection to the
     * left, positive shrinks it to the right). The start never moves past the end.
     */
    fun nudgeStart(text: String, range: IntRange, words: Int): IntRange {
        if (words == 0) return range
        var start = range.first
        repeat(kotlin.math.abs(words)) {
            start = if (words < 0) previousWordStart(text, start) else nextWordStart(text, start)
        }
        val clamped = start.coerceIn(0, range.last)
        return clamped..range.last
    }

    /**
     * Moves the end of a selection by [words] whole words (positive grows to the right, negative
     * shrinks to the left). The end never moves before the start.
     */
    fun nudgeEnd(text: String, range: IntRange, words: Int): IntRange {
        if (words == 0) return range
        var end = range.last
        repeat(kotlin.math.abs(words)) {
            end = if (words > 0) nextWordEnd(text, end) else previousWordEnd(text, end)
        }
        val clamped = end.coerceIn(range.first, (text.length - 1).coerceAtLeast(0))
        return range.first..clamped
    }

    private fun previousWordStart(text: String, from: Int): Int {
        var i = (from - 1).coerceAtLeast(0)
        while (i > 0 && !isWordChar(text[i])) i--
        while (i > 0 && isWordChar(text[i - 1])) i--
        return i
    }

    private fun nextWordStart(text: String, from: Int): Int {
        var i = from
        while (i < text.length && isWordChar(text[i])) i++
        while (i < text.length && !isWordChar(text[i])) i++
        return i.coerceAtMost((text.length - 1).coerceAtLeast(0))
    }

    private fun nextWordEnd(text: String, from: Int): Int {
        var i = (from + 1).coerceAtMost(text.length - 1)
        while (i < text.length - 1 && !isWordChar(text[i])) i++
        while (i < text.length - 1 && isWordChar(text[i + 1])) i++
        return i
    }

    private fun previousWordEnd(text: String, from: Int): Int {
        var i = (from - 1).coerceAtLeast(0)
        while (i > 0 && !isWordChar(text[i])) i--
        return i
    }

    /** Number of words inside [range], used for the "Selected: N words" label. */
    fun wordCount(text: String, range: IntRange): Int {
        val safe = substring(text, range)
        return safe.split(Regex("\\s+")).count { it.isNotBlank() }
    }

    fun substring(text: String, range: IntRange): String {
        if (text.isEmpty()) return ""
        val start = range.first.coerceIn(0, text.length - 1)
        val end = range.last.coerceIn(start, text.length - 1)
        return text.substring(start, end + 1)
    }

    /** Splits page text into display paragraphs, collapsing the single newlines PDFs are full of. */
    fun toParagraphs(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        return text.split(Regex("\n{2,}"))
            .map { it.replace(Regex("[ \t]*\n[ \t]*"), " ").trim() }
            .filter { it.isNotBlank() }
    }
}
