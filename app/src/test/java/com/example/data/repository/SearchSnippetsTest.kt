package com.example.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchSnippetsTest {

    private val page = "Trace trees are a unit of compilation. " +
        "The tracing JIT records a trace of the executed code and compiles it."

    @Test
    fun `a snippet is centred on the match`() {
        val snippet = SearchSnippets.build(page, "tracing JIT")!!
        assertTrue(snippet.contains("tracing JIT"))
    }

    @Test
    fun `snippets are elided only where text was cut`() {
        val snippet = SearchSnippets.build(page, "Trace trees")!!
        assertTrue("No leading ellipsis at the start of a page", !snippet.startsWith("…"))

        val tail = SearchSnippets.build(page, "compiles it")!!
        assertTrue("No trailing ellipsis at the end of a page", !tail.endsWith("…"))
    }

    @Test
    fun `matching is case insensitive`() {
        assertTrue(SearchSnippets.build(page, "TRACING")!!.contains("tracing"))
    }

    @Test
    fun `a miss returns null so the page can be filtered out`() {
        assertNull(SearchSnippets.build(page, "garbage collection"))
        assertNull(SearchSnippets.build(page, "  "))
    }

    @Test
    fun `whitespace inside a snippet is collapsed to one line`() {
        val messy = "A line\n\n  with   ragged\nwhitespace around match here"
        val snippet = SearchSnippets.build(messy, "match")!!
        assertTrue(!snippet.contains("\n"))
        assertTrue(!snippet.contains("  "))
    }

    @Test
    fun `occurrences are counted without overlap`() {
        assertEquals(2, SearchSnippets.countMatches(page, "trace"))
        assertEquals(0, SearchSnippets.countMatches(page, "zzz"))
        assertEquals(2, SearchSnippets.countMatches("aaaa", "aa"))
    }
}
