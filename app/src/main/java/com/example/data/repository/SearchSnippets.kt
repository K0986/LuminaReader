package com.example.data.repository

/** Pure helpers for turning a page of raw text into readable search results. */
object SearchSnippets {

    /**
     * Builds a one-line snippet around the first occurrence of [query].
     *
     * Returns null when the query does not appear, so callers can filter pages out.
     */
    fun build(text: String, query: String, radiusBefore: Int = 40, radiusAfter: Int = 60): String? {
        if (query.isBlank()) return null
        val found = text.indexOf(query, ignoreCase = true)
        if (found < 0) return null

        val start = (found - radiusBefore).coerceAtLeast(0)
        val end = (found + query.length + radiusAfter).coerceAtMost(text.length)
        val core = text.substring(start, end).replace(Regex("\\s+"), " ").trim()

        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < text.length) "…" else ""
        return "$prefix$core$suffix"
    }

    /** Counts non-overlapping, case-insensitive occurrences of [query] in [text]. */
    fun countMatches(text: String, query: String): Int {
        if (query.isBlank()) return 0
        var count = 0
        var index = text.indexOf(query, 0, ignoreCase = true)
        while (index >= 0) {
            count++
            index = text.indexOf(query, index + query.length, ignoreCase = true)
        }
        return count
    }
}
