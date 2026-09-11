package com.example.util

import android.content.Intent
import android.net.Uri
import android.os.Build

/**
 * Works out which files another app is handing us.
 *
 * Android has three different ways to pass a document to an app and they all matter here:
 *
 * - `ACTION_VIEW` with `intent.data` — "Open with" from a file manager, browser download, or mail
 *   attachment.
 * - `ACTION_SEND` with `EXTRA_STREAM` — the Share sheet, e.g. sharing a PDF from Drive or WhatsApp.
 * - `ACTION_SEND_MULTIPLE` with a list in `EXTRA_STREAM` — sharing several books at once.
 *
 * Some senders additionally (or only) populate `ClipData`, so that is folded in as well. The result
 * is de-duplicated and order-preserving: the first URI is the one the reader opens.
 */
object IncomingBookIntent {

    /** File extensions Lumina can read. Used when a sender gives us no usable MIME type. */
    val SUPPORTED_EXTENSIONS = setOf("epub", "pdf", "txt", "cbz")

    fun bookUris(intent: Intent?): List<Uri> {
        if (intent == null) return emptyList()

        val uris = LinkedHashSet<Uri>()

        when (intent.action) {
            Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> {
                uris += streamExtras(intent)
                uris += clipDataUris(intent)
            }
            else -> {
                intent.data?.let { uris += it }
                uris += clipDataUris(intent)
                uris += streamExtras(intent)
            }
        }

        return uris.filter { it.scheme == "content" || it.scheme == "file" }
    }

    /** True when the extension of [name] is a format the app can parse. */
    fun hasSupportedExtension(name: String?): Boolean {
        val extension = name?.substringAfterLast('.', "")?.lowercase() ?: return false
        return extension in SUPPORTED_EXTENSIONS
    }

    private fun clipDataUris(intent: Intent): List<Uri> {
        val clip = intent.clipData ?: return emptyList()
        return (0 until clip.itemCount).mapNotNull { clip.getItemAt(it)?.uri }
    }

    @Suppress("DEPRECATION")
    private fun streamExtras(intent: Intent): List<Uri> {
        if (!intent.hasExtra(Intent.EXTRA_STREAM)) return emptyList()
        // Senders are inconsistent: the extra may be a single Uri or an ArrayList of them, and the
        // typed getters throw when the actual type does not match, so both shapes are attempted.
        val many = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            }
        }.getOrNull()
        if (!many.isNullOrEmpty()) return many.filterNotNull()

        val single = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
            }
        }.getOrNull()
        return listOfNotNull(single)
    }
}
