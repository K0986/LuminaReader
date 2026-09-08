package com.example.ui.screens.notes

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.BookRepository
import com.example.ui.theme.AmberGold
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    bookRepository: BookRepository,
    onNavigateToBook: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val bookmarks by bookRepository.allBookmarks.collectAsState(initial = emptyList())
    val highlights by bookRepository.allHighlights.collectAsState(initial = emptyList())
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Highlights & Notes, 1: Bookmarks

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Notebook & Quotes",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = {
                        val exportText = buildString {
                            appendLine("# Lumina Reader Notes & Highlights\n")
                            highlights.forEach { hl ->
                                appendLine("### ${hl.chapterTitle}")
                                appendLine("> ${hl.selectedText}\n")
                                if (!hl.note.isNullOrBlank()) {
                                    appendLine("**Note:** ${hl.note}\n")
                                }
                            }
                        }
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, exportText)
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Export Notes as Markdown"))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Export Notes")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(selectedTabIndex = selectedTab, contentColor = AmberGold) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Highlights & Notes (${highlights.size})") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Bookmarks (${bookmarks.size})") }
                )
            }

            if (selectedTab == 0) {
                // Highlights
                if (highlights.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.FormatQuote, contentDescription = null, tint = AmberGold, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("No highlights or notes saved yet", fontWeight = FontWeight.SemiBold)
                            Text("Select any sentence while reading to highlight or attach a note.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(highlights, key = { it.id }) { hl ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateToBook(hl.bookId) },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    try { Color(android.graphics.Color.parseColor(hl.colorHex)) }
                                                    catch (e: Exception) { AmberGold }
                                                )
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = hl.chapterTitle.ifEmpty { "Excerpt" },
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AmberGold,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = { scope.launch { bookRepository.removeHighlight(hl.id) } },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "“${hl.selectedText}”",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                    )
                                    if (!hl.note.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = "Note: ${hl.note}",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.padding(8.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Bookmarks
                if (bookmarks.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No bookmarks saved yet")
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(bookmarks, key = { it.id }) { bm ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateToBook(bm.bookId) },
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Bookmark, contentDescription = null, tint = AmberGold)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = bm.chapterTitle.ifEmpty { "Page ${bm.pageIndex + 1}" }, fontWeight = FontWeight.Bold)
                                        if (bm.snippetText.isNotBlank()) {
                                            Text(text = bm.snippetText, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    IconButton(onClick = { scope.launch { bookRepository.removeBookmark(bm.id) } }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp))
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
