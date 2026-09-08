package com.example.data.parser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.StringReader
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object EpubParser {

    /**
     * Parses an EPUB file into structured metadata, cover image, TOC, and chapters.
     */
    fun parse(context: Context, uri: Uri): ParsedBook {
        val zipEntries = mutableMapOf<String, ByteArray>()
        val inputStream: InputStream? = if (uri.scheme == "file") {
            File(uri.path ?: "").inputStream()
        } else {
            context.contentResolver.openInputStream(uri)
        }

        inputStream?.use { stream ->
            ZipInputStream(stream).use { zip ->
                var entry: ZipEntry? = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        zipEntries[entry.name] = zip.readBytes()
                    }
                    entry = zip.nextEntry
                }
            }
        }

        if (zipEntries.isEmpty()) {
            return ParsedBook(
                title = "Unknown Book",
                author = "Unknown Author",
                format = "EPUB"
            )
        }

        // 1. Locate OPF file from META-INF/container.xml
        val opfPath = findOpfPath(zipEntries) ?: "content.opf"
        val opfDir = if (opfPath.contains("/")) opfPath.substringBeforeLast("/") + "/" else ""

        val opfBytes = zipEntries[opfPath] ?: zipEntries.entries.firstOrNull { it.key.endsWith(".opf") }?.value

        var title = "Untitled Book"
        var author = "Unknown Author"
        val manifest = mutableMapOf<String, ManifestItem>() // id -> ManifestItem
        val spine = mutableListOf<String>() // idrefs
        var coverId: String? = null

        if (opfBytes != null) {
            val opfXml = String(opfBytes, Charsets.UTF_8)
            val parsedOpf = parseOpfXml(opfXml)
            if (parsedOpf.title.isNotBlank()) title = parsedOpf.title
            if (parsedOpf.author.isNotBlank()) author = parsedOpf.author
            manifest.putAll(parsedOpf.manifest)
            spine.addAll(parsedOpf.spine)
            coverId = parsedOpf.coverId
        }

        // 2. Extract Cover Image
        var coverBitmap: Bitmap? = null
        var coverBytes: ByteArray? = null

        val coverItem = if (coverId != null) {
            manifest[coverId]
        } else {
            manifest.values.firstOrNull { it.properties.contains("cover-image") || it.id.contains("cover", ignoreCase = true) }
                ?: manifest.values.firstOrNull { it.mediaType.startsWith("image/") && it.href.contains("cover", ignoreCase = true) }
        }

        if (coverItem != null) {
            val fullCoverPath = resolvePath(opfDir, coverItem.href)
            val imgData = zipEntries[fullCoverPath] ?: zipEntries[coverItem.href]
            if (imgData != null) {
                coverBytes = imgData
                coverBitmap = try {
                    BitmapFactory.decodeByteArray(imgData, 0, imgData.size)
                } catch (e: Exception) {
                    null
                }
            }
        }

        // 3. Extract Chapters from Spine
        val chapters = mutableListOf<SpineChapter>()
        val tocItems = mutableListOf<TocItem>()

        var chapterIdx = 0
        for (idref in spine) {
            val item = manifest[idref] ?: continue
            val fullChapterPath = resolvePath(opfDir, item.href)
            val chapterBytes = zipEntries[fullChapterPath] ?: zipEntries[item.href] ?: continue
            val rawHtml = String(chapterBytes, Charsets.UTF_8)

            val (chapterTitle, paragraphs) = extractHtmlTextAndTitle(rawHtml, "Chapter ${chapterIdx + 1}")
            val fullText = paragraphs.joinToString("\n\n")

            if (fullText.isNotBlank()) {
                val spineChapter = SpineChapter(
                    id = idref,
                    title = chapterTitle,
                    plainText = fullText,
                    formattedParagraphs = paragraphs,
                    wordCount = fullText.split("\\s+".toRegex()).count()
                )
                chapters.add(spineChapter)
                tocItems.add(
                    TocItem(
                        id = idref,
                        title = chapterTitle,
                        targetIndex = chapterIdx,
                        level = 0
                    )
                )
                chapterIdx++
            }
        }

        // Fallback if spine was empty
        if (chapters.isEmpty()) {
            chapters.add(
                SpineChapter(
                    id = "single_ch",
                    title = title,
                    plainText = "Could not parse individual chapters from this EPUB.",
                    formattedParagraphs = listOf("Could not parse individual chapters from this EPUB."),
                    wordCount = 8
                )
            )
        }

        val totalEstPages = (chapters.sumOf { it.plainText.length } / 1500).coerceAtLeast(chapters.size)

        return ParsedBook(
            title = title,
            author = author,
            format = "EPUB",
            coverBitmap = coverBitmap,
            coverBytes = coverBytes,
            tableOfContents = tocItems,
            chapters = chapters,
            totalPagesEstimate = totalEstPages
        )
    }

    private fun findOpfPath(zipEntries: Map<String, ByteArray>): String? {
        val containerBytes = zipEntries["META-INF/container.xml"] ?: return null
        return try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(ByteArrayInputStream(containerBytes), "UTF-8")
            var eventType = parser.eventType
            var opfPath: String? = null
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "rootfile") {
                    opfPath = parser.getAttributeValue(null, "full-path")
                    if (opfPath != null) break
                }
                eventType = parser.next()
            }
            opfPath
        } catch (e: Exception) {
            null
        }
    }

    private data class ManifestItem(val id: String, val href: String, val mediaType: String, val properties: String = "")
    private data class OpfResult(
        val title: String,
        val author: String,
        val coverId: String?,
        val manifest: Map<String, ManifestItem>,
        val spine: List<String>
    )

    private fun parseOpfXml(xml: String): OpfResult {
        var title = ""
        var author = ""
        var coverId: String? = null
        val manifest = mutableMapOf<String, ManifestItem>()
        val spine = mutableListOf<String>()

        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            var eventType = parser.eventType
            var currentTag = ""

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        currentTag = parser.name.lowercase()
                        when (currentTag) {
                            "item" -> {
                                val id = parser.getAttributeValue(null, "id") ?: ""
                                val href = parser.getAttributeValue(null, "href") ?: ""
                                val mediaType = parser.getAttributeValue(null, "media-type") ?: ""
                                val properties = parser.getAttributeValue(null, "properties") ?: ""
                                if (id.isNotBlank()) {
                                    manifest[id] = ManifestItem(id, href, mediaType, properties)
                                    if (properties.contains("cover-image")) {
                                        coverId = id
                                    }
                                }
                            }
                            "itemref" -> {
                                val idref = parser.getAttributeValue(null, "idref") ?: ""
                                if (idref.isNotBlank()) {
                                    spine.add(idref)
                                }
                            }
                            "meta" -> {
                                val name = parser.getAttributeValue(null, "name")
                                val content = parser.getAttributeValue(null, "content")
                                if (name.equals("cover", ignoreCase = true) && content != null) {
                                    coverId = content
                                }
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        val text = parser.text.trim()
                        if (text.isNotBlank()) {
                            if (currentTag == "title" && title.isBlank()) {
                                title = text
                            } else if (currentTag == "creator" && author.isBlank()) {
                                author = text
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        currentTag = ""
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            // Handled gracefully
        }

        return OpfResult(title, author, coverId, manifest, spine)
    }

    private fun extractHtmlTextAndTitle(html: String, defaultTitle: String): Pair<String, List<String>> {
        var chapterTitle = defaultTitle
        val paragraphs = mutableListOf<String>()

        // Quick title detection from <h1>, <h2>, <h3>, or <title>
        val titleRegex = "<(?:h1|h2|title)[^>]*>(.*?)</(?:h1|h2|title)>".toRegex(RegexOption.IGNORE_CASE)
        val titleMatch = titleRegex.find(html)
        if (titleMatch != null) {
            val extracted = cleanTags(titleMatch.groupValues[1]).trim()
            if (extracted.isNotBlank() && extracted.length < 120) {
                chapterTitle = extracted
            }
        }

        // Extract paragraphs
        val pRegex = "<(?:p|div|blockquote|li)[^>]*>(.*?)</(?:p|div|blockquote|li)>".toRegex(RegexOption.IGNORE_CASE)
        val matches = pRegex.findAll(html)

        for (match in matches) {
            val cleaned = cleanTags(match.groupValues[1]).trim()
            if (cleaned.isNotBlank()) {
                paragraphs.add(cleaned)
            }
        }

        // Fallback if no <p> tags were present
        if (paragraphs.isEmpty()) {
            val allClean = cleanTags(html).trim()
            if (allClean.isNotBlank()) {
                allClean.split("\n\n").forEach { p ->
                    val trimmed = p.trim()
                    if (trimmed.isNotBlank()) paragraphs.add(trimmed)
                }
            }
        }

        return Pair(chapterTitle, paragraphs)
    }

    private fun cleanTags(html: String): String {
        return html
            .replace("<br\\s*/?>".toRegex(RegexOption.IGNORE_CASE), "\n")
            .replace("<[^>]*>".toRegex(), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")
            .replace("\\s+".toRegex(), " ")
    }

    private fun resolvePath(baseDir: String, relative: String): String {
        if (relative.startsWith("/")) return relative.removePrefix("/")
        val combined = baseDir + relative
        val segments = combined.split("/")
        val normalized = mutableListOf<String>()
        for (seg in segments) {
            when (seg) {
                "", "." -> {}
                ".." -> if (normalized.isNotEmpty()) normalized.removeAt(normalized.size - 1)
                else -> normalized.add(seg)
            }
        }
        return normalized.joinToString("/")
    }
}
