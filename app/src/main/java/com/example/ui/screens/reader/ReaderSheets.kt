package com.example.ui.screens.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppSettings
import com.example.data.model.Bookmark
import com.example.data.model.Highlight
import com.example.data.model.ReaderFont
import com.example.data.model.ReaderTheme
import com.example.data.parser.ParsedBook
import com.example.ui.theme.AmberGold
import kotlinx.coroutines.delay

/**
 * Font & Appearance Settings Sheet (Kindle 'Aa' Menu)
 */
@Composable
fun AppearanceSettingsContent(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Text("Display & Reading Style", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        Text("Theme", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ReaderTheme.entries.forEach { theme ->
                val isSelected = settings.theme == theme
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(theme.bgHex))
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) AmberGold else Color.Gray.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { onSettingsChange(settings.copy(theme = theme)) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = theme.title,
                        color = Color(theme.textHex),
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text("Font Family", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ReaderFont.entries.forEach { font ->
                FilterChip(
                    selected = settings.fontFamily == font,
                    onClick = { onSettingsChange(settings.copy(fontFamily = font)) },
                    label = { Text(font.displayName, fontSize = 11.sp) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Font Size", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        if (settings.fontSizeSp > 12f) {
                            onSettingsChange(settings.copy(fontSizeSp = settings.fontSizeSp - 2f))
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Text("A-", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Text(
                    text = "${settings.fontSizeSp.toInt()} sp",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                IconButton(
                    onClick = {
                        if (settings.fontSizeSp < 32f) {
                            onSettingsChange(settings.copy(fontSizeSp = settings.fontSizeSp + 2f))
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Text("A+", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Line Spacing", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Row {
                listOf(1.2f, 1.4f, 1.8f).forEach { spacing ->
                    FilterChip(
                        selected = kotlin.math.abs(settings.lineSpacingMultiplier - spacing) < 0.05f,
                        onClick = { onSettingsChange(settings.copy(lineSpacingMultiplier = spacing)) },
                        label = { Text("${spacing}x", fontSize = 11.sp) },
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Margins", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Row {
                listOf(12 to "Narrow", 20 to "Normal", 32 to "Wide").forEach { (margin, name) ->
                    FilterChip(
                        selected = settings.marginPaddingDp == margin,
                        onClick = { onSettingsChange(settings.copy(marginPaddingDp = margin)) },
                        label = { Text(name, fontSize = 11.sp) },
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text("Screen Brightness", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = if (settings.brightness < 0f) 1f else settings.brightness,
                onValueChange = { onSettingsChange(settings.copy(brightness = it)) },
                valueRange = 0.05f..1.0f,
                colors = SliderDefaults.colors(thumbColor = AmberGold, activeTrackColor = AmberGold),
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { onSettingsChange(settings.copy(brightness = -1f)) }) {
                Text("Auto", fontSize = 11.sp)
            }
        }

        Text("Warm Night Light", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Slider(
            value = settings.nightLightWarmth,
            onValueChange = { onSettingsChange(settings.copy(nightLightWarmth = it)) },
            valueRange = 0.0f..0.6f,
            colors = SliderDefaults.colors(thumbColor = AmberGold, activeTrackColor = AmberGold)
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * Table of Contents & Bookmarks Drawer
 */
@Composable
fun TableOfContentsContent(
    parsedBook: ParsedBook?,
    currentPage: Int,
    bookmarks: List<Bookmark>,
    highlights: List<Highlight>,
    onSelectChapter: (Int) -> Unit,
    onDeleteBookmark: (Long) -> Unit,
    onDeleteHighlight: (Long) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.75f)
            .padding(horizontal = 16.dp)
    ) {
        TabRow(selectedTabIndex = selectedTab, contentColor = AmberGold) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Chapters") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Bookmarks (${bookmarks.size})") })
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Notes (${highlights.size})") })
        }

        Spacer(modifier = Modifier.height(8.dp))

        when (selectedTab) {
            0 -> {
                val toc = parsedBook?.tableOfContents ?: emptyList()
                if (toc.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No chapters detected")
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(toc) { idx, item ->
                            val isCurrent = idx == currentPage
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectChapter(item.targetIndex) }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = item.title,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isCurrent) AmberGold else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isCurrent) {
                                    Text("Reading", fontSize = 11.sp, color = AmberGold, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
            1 -> {
                if (bookmarks.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No bookmarks added yet")
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(bookmarks) { _, bm ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectChapter(bm.pageIndex) }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Bookmark, contentDescription = null, tint = AmberGold)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = bm.chapterTitle.ifEmpty { "Page ${bm.pageIndex + 1}" },
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (bm.snippetText.isNotBlank()) {
                                        Text(text = bm.snippetText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                IconButton(onClick = { onDeleteBookmark(bm.id) }) {
                                    Icon(Icons.Default.Close, contentDescription = "Delete", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
            2 -> {
                if (highlights.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No highlights or notes yet")
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(highlights) { _, hl ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable { onSelectChapter(hl.pageIndex) },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(parseHighlightColor(hl.colorHex))
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(text = hl.chapterTitle, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AmberGold)
                                        Spacer(modifier = Modifier.weight(1f))
                                        IconButton(onClick = { onDeleteHighlight(hl.id) }, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Default.Close, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    Text(
                                        text = "“${hl.selectedText.take(100)}”",
                                        fontSize = 12.sp,
                                        fontStyle = FontStyle.Italic
                                    )
                                    if (!hl.note.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(text = "Note: ${hl.note}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One row of in-book search results. */
data class InBookSearchResult(
    val targetIndex: Int,
    val label: String,
    val snippet: String
)

/**
 * Full-text in-book search.
 *
 * Searching is delegated to the caller because the two book kinds answer very differently: a
 * reflowable book searches its parsed chapters in memory, while a PDF searches the page-text index
 * in the database — which is the only reason searching a 500 page PDF is possible at all.
 */
@Composable
fun InBookSearchContent(
    search: suspend (String) -> List<InBookSearchResult>,
    onSelectMatch: (Int) -> Unit,
    emptyHint: String? = null
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<InBookSearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        if (query.length < 2) {
            results = emptyList()
            isSearching = false
            return@LaunchedEffect
        }
        isSearching = true
        delay(250) // debounce typing
        results = search(query)
        isSearching = false
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.75f)
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search word or passage in book...") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("in_book_search_field"),
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear")
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isSearching) {
                CircularProgressIndicator(color = AmberGold, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = when {
                    query.length < 2 -> emptyHint ?: "Type at least two characters"
                    isSearching -> "Searching…"
                    else -> "${results.size} page${if (results.size == 1) "" else "s"} matched"
                },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(results) { _, match ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onSelectMatch(match.targetIndex) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(match.label, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = AmberGold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(match.snippet, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

/**
 * Text-to-Speech Audio Controls Sheet
 */
@Composable
fun TtsAudioControlsContent(
    pageTitle: String,
    isPlaying: Boolean,
    currentSentence: Int,
    sentences: List<String>,
    sleepTimerMins: Int?,
    speed: Float,
    onPlayPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onSetSleepTimer: (Int) -> Unit,
    onCancelSleepTimer: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Headphones, contentDescription = null, tint = AmberGold)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Voice Narration (TTS)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(pageTitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (isPlaying) {
                Surface(color = AmberGold.copy(alpha = 0.18f), shape = RoundedCornerShape(12.dp)) {
                    Text(
                        "Reading...",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AmberGold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val activeText = sentences.getOrNull(currentSentence)
            ?: sentences.firstOrNull()
            ?: "No selectable text was found on this page."
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = if (sentences.isNotEmpty()) {
                        "Sentence ${currentSentence + 1} of ${sentences.size}"
                    } else {
                        "Text Preview"
                    },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AmberGold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "“$activeText”",
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onPrev, enabled = currentSentence > 0, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Previous sentence", modifier = Modifier.size(32.dp))
            }

            Spacer(modifier = Modifier.width(20.dp))

            Surface(
                shape = CircleShape,
                color = AmberGold,
                modifier = Modifier
                    .size(64.dp)
                    .clickable { onPlayPause() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color(0xFF0F172A),
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(20.dp))

            IconButton(
                onClick = onNext,
                enabled = sentences.isNotEmpty() && currentSentence < sentences.size - 1,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Default.SkipNext, contentDescription = "Next sentence", modifier = Modifier.size(32.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Speed: ", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(4.dp))
            listOf(0.75f, 1.0f, 1.25f, 1.5f).forEach { candidate ->
                FilterChip(
                    selected = kotlin.math.abs(speed - candidate) < 0.05f,
                    onClick = { onSpeedChange(candidate) },
                    label = { Text("${candidate}x", fontSize = 11.sp) },
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Timer, contentDescription = null, tint = AmberGold, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (sleepTimerMins != null) "Sleep Timer: $sleepTimerMins min left" else "Sleep Timer: Off",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.width(8.dp))
            if (sleepTimerMins != null) {
                TextButton(onClick = onCancelSleepTimer) { Text("Cancel", fontSize = 11.sp) }
            } else {
                listOf(15, 30, 45).forEach { minutes ->
                    TextButton(onClick = { onSetSleepTimer(minutes) }) { Text("${minutes}m", fontSize = 11.sp) }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}

/** Highlight colours are stored as hex strings; fall back to the accent when one is malformed. */
fun parseHighlightColor(colorHex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(colorHex)) }.getOrDefault(AmberGold)
