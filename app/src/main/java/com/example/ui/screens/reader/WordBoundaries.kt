package com.example.ui.screens.reader

import java.text.BreakIterator

/**
 * Word and sentence boundaries over a paragraph of text.
 *
 * Selection is expressed as a half-open character range `[start, end)` into the
 * *original* paragraph string. The previous implementation instead split the paragraph
 * on whitespace and tracked a pair of word indices, reconstructing the selected text
 * with `joinToString(" ")`. That reconstruction was lossy: double spaces, non-breaking
 * spaces, em dashes surrounded by thin spaces and soft-hyphenated line breaks all came
 * back as single ASCII spaces, so a copied or highlighted quote never matched the book.
 *
 * Character offsets avoid that entirely -- the selected text is always a literal
 * `substring` of the source -- and they are what Compose's text layout speaks natively.
 *
 * [BreakIterator] is used rather than a whitespace regex so that word boundaries are
 * correct for languages that do not separate words with spaces.
 */
object WordBoundaries {

    /** The word range containing [offset], or an empty range if there is no word nearby. */
    fun wordRangeAt(text: String, offset: Int): IntRange {
        if (text.isEmpty()) return IntRange.EMPTY

        // Anchor on an actual word character before consulting the break iterator.
        // Fingers land on spaces and punctuation constantly, and `preceding`/`following`
        // happily return a boundary pair spanning ". " -- a "word" with no letters in it.
        val anchor = anchorWordCharacter(text, offset) ?: return IntRange.EMPTY

        val iterator = BreakIterator.getWordInstance().apply { setText(text) }
        var start = iterator.preceding(anchor + 1).let { if (it == BreakIterator.DONE) 0 else it }
        var end = iterator.following(anchor).let { if (it == BreakIterator.DONE) text.length else it }

        // Trim so that tapping "word," selects "word".
        while (start < end && !text[start].isLetterOrDigit()) start++
        while (end > start && !text[end - 1].isLetterOrDigit()) end--

        return if (start >= end) IntRange.EMPTY else start until end
    }

    /** The nearest word character to [offset], preferring forwards on a tie. */
    private fun anchorWordCharacter(text: String, offset: Int): Int? {
        val clamped = offset.coerceIn(0, text.length - 1)
        if (text[clamped].isLetterOrDigit()) return clamped

        var forward = clamped
        while (forward < text.length && !text[forward].isLetterOrDigit()) forward++
        var backward = clamped
        while (backward >= 0 && !text[backward].isLetterOrDigit()) backward--

        val forwardFound = forward < text.length
        val backwardFound = backward >= 0
        return when {
            forwardFound && (!backwardFound || forward - clamped <= clamped - backward) -> forward
            backwardFound -> backward
            else -> null
        }
    }

    /** The sentence range containing [offset]. */
    fun sentenceRangeAt(text: String, offset: Int): IntRange {
        if (text.isEmpty()) return IntRange.EMPTY
        val clamped = offset.coerceIn(0, text.length)

        val iterator = BreakIterator.getSentenceInstance().apply { setText(text) }
        var start = iterator.preceding(clamped.coerceAtMost(text.length))
        if (start == BreakIterator.DONE) start = 0
        var end = iterator.following(clamped)
        if (end == BreakIterator.DONE) end = text.length

        while (end > start && text[end - 1].isWhitespace()) end--
        return if (start >= end) IntRange.EMPTY else start until end
    }

    /**
     * Moves a selection edge by [words] whole words. Negative values move left.
     * Returns the new offset, clamped to the text.
     */
    fun nudgeByWord(text: String, offset: Int, words: Int): Int {
        if (text.isEmpty() || words == 0) return offset.coerceIn(0, text.length)
        val iterator = BreakIterator.getWordInstance().apply { setText(text) }
        var current = offset.coerceIn(0, text.length)

        repeat(kotlin.math.abs(words)) {
            val next = if (words > 0) iterator.following(current) else iterator.preceding(current)
            if (next == BreakIterator.DONE) return current
            current = next
            // Skip boundaries that sit inside whitespace so one press moves one word.
            while (current in 1 until text.length && text[current - 1].isWhitespace()) {
                val skip = if (words > 0) iterator.following(current) else iterator.preceding(current)
                if (skip == BreakIterator.DONE) break
                current = skip
            }
        }
        return current.coerceIn(0, text.length)
    }
}
