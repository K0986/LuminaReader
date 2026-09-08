package com.example.domain.dictionary

import android.content.Context
import android.content.Intent
import android.net.Uri

data class WordDefinition(
    val word: String,
    val partOfSpeech: String,
    val definition: String,
    val example: String? = null
)

object DictionaryLookup {

    private val offlineDictionary = mapOf(
        "abhorrent" to WordDefinition("abhorrent", "adjective", "Inspiring disgust and loathing; repugnant.", "Racism is abhorrent to a civilized society."),
        "predominates" to WordDefinition("predominates", "verb", "Be the strongest or main element; be greater in number or amount.", "Small shops still predominate in the town."),
        "eclipses" to WordDefinition("eclipses", "verb", "Obscure the light from; deprive of significance, power, or prominence.", "Her work eclipsed that of her peers."),
        "gibe" to WordDefinition("gibe", "noun / verb", "An insulting or mocking remark; a taunt.", "He endured their cruel gibes with quiet dignity."),
        "bizarre" to WordDefinition("bizarre", "adjective", "Very strange or unusual, especially so as to cause interest or amusement.", "He told a bizarre tale of encounters in the forest."),
        "forebodings" to WordDefinition("forebodings", "noun", "Fearful apprehension; a feeling that something bad will happen.", "With a strange foreboding of disaster, she opened the letter."),
        "inspirited" to WordDefinition("inspirited", "verb", "Encourage and enliven; fill with spirit or courage.", "The captain's speech inspirited the crew."),
        "magnificence" to WordDefinition("magnificence", "noun", "Greatness or lavishness of appearance; splendor.", "The palace was renowned for its antique magnificence."),
        "curiouser" to WordDefinition("curiouser", "adjective (colloquial)", "More curious; famously coined by Lewis Carroll in Alice in Wonderland.", "Things grew curiouser and curiouser as she wandered.")
    )

    fun getDefinition(word: String): WordDefinition? {
        val clean = word.lowercase().trim().replace("[^a-zA-Z]".toRegex(), "")
        return offlineDictionary[clean] ?: if (clean.length > 2) {
            WordDefinition(
                word = clean,
                partOfSpeech = "vocabulary",
                definition = "Look up '$clean' in your preferred offline or web dictionary.",
                example = null
            )
        } else null
    }

    fun openWebSearch(context: Context, query: String) {
        try {
            val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                putExtra(android.app.SearchManager.QUERY, query)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query)))
            browserIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(browserIntent)
        }
    }

    fun openWikipedia(context: Context, query: String) {
        val clean = query.trim()
        val url = "https://en.wikipedia.org/wiki/" + Uri.encode(clean.replace(" ", "_"))
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun shareTextOrQuote(context: Context, quote: String, bookTitle: String, author: String) {
        val shareBody = "“$quote”\n\n— $author, $bookTitle\n(via Lumina Reader)"
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareBody)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Share Quote")
        shareIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(shareIntent)
    }
}
