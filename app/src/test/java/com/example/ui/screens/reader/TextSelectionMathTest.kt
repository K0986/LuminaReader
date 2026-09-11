package com.example.ui.screens.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextSelectionMathTest {

    private val sentence = "The quick brown fox jumps over the lazy dog."

    @Test
    fun `word range covers the whole word from any offset inside it`() {
        val range = TextSelectionMath.wordRangeAt(sentence, offset = 6)
        assertEquals("quick", TextSelectionMath.substring(sentence, range!!))
    }

    @Test
    fun `tapping the right edge of a word still selects that word`() {
        // Offset 9 is the space after "quick"; a reader aiming at the end of the word lands here.
        val range = TextSelectionMath.wordRangeAt(sentence, offset = 9)
        assertEquals("quick", TextSelectionMath.substring(sentence, range!!))
    }

    @Test
    fun `tapping empty space selects nothing rather than an arbitrary word`() {
        assertNull(TextSelectionMath.wordRangeAt("   ", offset = 1))
    }

    @Test
    fun `hyphenated and apostrophed words stay whole`() {
        val text = "It's a well-known result."
        assertEquals("It's", TextSelectionMath.substring(text, TextSelectionMath.wordRangeAt(text, 1)!!))
        assertEquals(
            "well-known",
            TextSelectionMath.substring(text, TextSelectionMath.wordRangeAt(text, 8)!!)
        )
    }

    @Test
    fun `nudging the end grows the selection one word at a time`() {
        val start = TextSelectionMath.wordRangeAt(sentence, 4)!! // "quick"
        val grown = TextSelectionMath.nudgeEnd(sentence, start, 1)
        assertEquals("quick brown", TextSelectionMath.substring(sentence, grown))

        val grownTwice = TextSelectionMath.nudgeEnd(sentence, grown, 1)
        assertEquals("quick brown fox", TextSelectionMath.substring(sentence, grownTwice))
    }

    @Test
    fun `nudging the start backwards grows to the left`() {
        val start = TextSelectionMath.wordRangeAt(sentence, 10)!! // "brown"
        val grown = TextSelectionMath.nudgeStart(sentence, start, -1)
        assertEquals("quick brown", TextSelectionMath.substring(sentence, grown))
    }

    @Test
    fun `the start never crosses the end`() {
        val range = TextSelectionMath.wordRangeAt(sentence, 4)!!
        val shrunk = TextSelectionMath.nudgeStart(sentence, range, 5)
        assertEquals(range.last, shrunk.last)
        assertEquals(true, shrunk.first <= shrunk.last)
    }

    @Test
    fun `sentence range stops at punctuation`() {
        val text = "First one. Second one! Third one?"
        val range = TextSelectionMath.sentenceRangeAt(text, 13)
        assertEquals("Second one!", TextSelectionMath.substring(text, range!!))
    }

    @Test
    fun `word count matches the selected substring`() {
        val range = 4..18
        assertEquals(3, TextSelectionMath.wordCount(sentence, range))
    }

    @Test
    fun `paragraph splitting joins the soft line breaks PDFs are full of`() {
        val pageText = "A sentence broken\nacross lines.\n\nAnd a second paragraph."
        val paragraphs = TextSelectionMath.toParagraphs(pageText)

        assertEquals(2, paragraphs.size)
        assertEquals("A sentence broken across lines.", paragraphs[0])
        assertEquals("And a second paragraph.", paragraphs[1])
    }

    @Test
    fun `blank page text produces no paragraphs`() {
        assertEquals(emptyList<String>(), TextSelectionMath.toParagraphs("   \n\n  "))
    }
}
