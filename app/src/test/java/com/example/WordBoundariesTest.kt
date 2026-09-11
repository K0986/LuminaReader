package com.example

import com.example.ui.screens.reader.CursorSelectionState
import com.example.ui.screens.reader.WordBoundaries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Selection is expressed as character offsets into the original paragraph, so the
 * property that matters most is that the selected text is always a literal substring of
 * the source -- never a reconstruction.
 */
class WordBoundariesTest {

    private val paragraph =
        "Reading is a multi-dimensional activity.  It demands attention; it rewards patience."

    @Test
    fun `tapping inside a word selects that whole word`() {
        val range = WordBoundaries.wordRangeAt(paragraph, offset = 3) // inside "Reading"
        assertEquals("Reading", paragraph.substring(range.first, range.last + 1))
    }

    @Test
    fun `trailing punctuation is excluded from a word selection`() {
        val activityAt = paragraph.indexOf("activity")
        val range = WordBoundaries.wordRangeAt(paragraph, activityAt + 2)
        assertEquals("activity", paragraph.substring(range.first, range.last + 1))
    }

    @Test
    fun `tapping whitespace snaps forward to the next word`() {
        val doubleSpace = paragraph.indexOf("  ") // the run between the two sentences
        val range = WordBoundaries.wordRangeAt(paragraph, doubleSpace)
        assertTrue(range.isEmpty().not())
        val selected = paragraph.substring(range.first, range.last + 1)
        assertTrue("Expected a real word, got '$selected'", selected.all { it.isLetterOrDigit() })
    }

    @Test
    fun `sentence selection stops at the sentence boundary`() {
        val range = WordBoundaries.sentenceRangeAt(paragraph, offset = 3)
        val sentence = paragraph.substring(range.first, range.last + 1)
        assertEquals("Reading is a multi-dimensional activity.", sentence)
    }

    @Test
    fun `nudging forward by one word moves past exactly one word`() {
        val start = paragraph.indexOf("is")
        val moved = WordBoundaries.nudgeByWord(paragraph, start, words = 1)
        assertTrue("Expected to move forward from $start, landed on $moved", moved > start)
        assertTrue(moved <= paragraph.indexOf("multi"))
    }

    @Test
    fun `nudging backwards is clamped at the start of the text`() {
        assertEquals(0, WordBoundaries.nudgeByWord(paragraph, offset = 0, words = -5))
    }

    @Test
    fun `empty text yields an empty range instead of throwing`() {
        assertTrue(WordBoundaries.wordRangeAt("", 0).isEmpty())
        assertTrue(WordBoundaries.sentenceRangeAt("", 0).isEmpty())
    }

    @Test
    fun `offsets past the end of the text are clamped`() {
        val range = WordBoundaries.wordRangeAt(paragraph, offset = paragraph.length + 50)
        assertTrue(range.isEmpty() || range.last < paragraph.length)
    }

    @Test
    fun `selected text is always a literal substring of the paragraph`() {
        // The whitespace here is the point: the old word-index selector rebuilt the
        // selection with joinToString(" "), which silently collapsed the double space
        // and so produced text that did not appear in the book.
        val selection = CursorSelectionState.of(
            paragraphIndex = 0,
            text = paragraph,
            start = 0,
            end = paragraph.indexOf("It") + 2
        )
        val selected = checkNotNull(selection).selectedText
        assertTrue("'$selected' must occur verbatim in the paragraph", paragraph.contains(selected))
        assertTrue("Double space must survive", selected.contains("activity.  It"))
    }

    @Test
    fun `an empty range produces no selection`() {
        assertNull(CursorSelectionState.of(0, paragraph, start = 5, end = 5))
        assertNull(CursorSelectionState.of(0, paragraph, IntRange.EMPTY))
    }
}
