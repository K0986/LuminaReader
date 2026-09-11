package com.example.ui.screens.library

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Book
import com.example.data.repository.BookRepository
import com.example.data.repository.ImportOutcome
import com.example.data.repository.ReadingSessionRepository
import com.example.data.sample.SampleBooks
import com.example.ui.theme.AmberGold
import kotlinx.coroutines.launch

enum class SortOption(val title: String) {
    RECENT("Recently Opened"),
    TITLE("Title (A-Z)"),
    AUTHOR("Author (A-Z)"),
    PROGRESS("Reading Progress"),
    DATE_ADDED("Date Added")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    bookRepository: BookRepository,
    sessionRepository: ReadingSessionRepository,
    onBookClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val books by bookRepository.allBooks.collectAsState(initial = emptyList())
    val stats by sessionRepository.stats.collectAsState(initial = null)

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var isGridView by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableStateOf(0) } // 0: All, 1: Reading, 2: To Read, 3: Finished, 4: Favorites
    var currentSort by remember { mutableStateOf(SortOption.RECENT) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showScanRationale by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }

    val tabs = listOf("All Books", "Reading", "To Read", "Finished", "Favorites")

    // SAF Document Picker for Books
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            isImporting = true
            scope.launch {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {}

                when (val outcome = bookRepository.importBook(uri)) {
                    is ImportOutcome.Added ->
                        Toast.makeText(context, "Added '${outcome.book.title}' to library!", Toast.LENGTH_SHORT).show()
                    is ImportOutcome.AlreadyInLibrary ->
                        Toast.makeText(context, "'${outcome.book.title}' is already in your library", Toast.LENGTH_SHORT).show()
                    is ImportOutcome.Failed ->
                        Toast.makeText(context, outcome.message, Toast.LENGTH_LONG).show()
                }
                isImporting = false
            }
        }
    }

    // Filter books
    val filteredBooks = books.filter { book ->
        val matchesQuery = if (searchQuery.isBlank()) true else {
            book.title.contains(searchQuery, ignoreCase = true) ||
                    book.author.contains(searchQuery, ignoreCase = true) ||
                    book.format.contains(searchQuery, ignoreCase = true)
        }

        val matchesTab = when (selectedTab) {
            1 -> book.readingStatus == "READING"
            2 -> book.readingStatus == "UNREAD"
            3 -> book.readingStatus == "FINISHED"
            4 -> book.isFavorite
            else -> true
        }

        matchesQuery && matchesTab
    }.sortedWith { a, b ->
        when (currentSort) {
            SortOption.RECENT -> b.lastOpened.compareTo(a.lastOpened)
            SortOption.TITLE -> a.title.compareTo(b.title, ignoreCase = true)
            SortOption.AUTHOR -> a.author.compareTo(b.author, ignoreCase = true)
            SortOption.PROGRESS -> b.progressPercent.compareTo(a.progressPercent)
            SortOption.DATE_ADDED -> b.dateAdded.compareTo(a.dateAdded)
        }
    }

    // Currently reading book for hero resume card
    val currentlyReadingBook = books.firstOrNull { it.readingStatus == "READING" || it.progressPercent > 0f }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search title, author, or format...") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("library_search_input"),
                            trailingIcon = {
                                IconButton(onClick = {
                                    if (searchQuery.isNotEmpty()) searchQuery = ""
                                    else isSearchActive = false
                                }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                }
                            }
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.MenuBook,
                                contentDescription = null,
                                tint = AmberGold,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Lumina Reader",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${books.size} books in library",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                actions = {
                    if (!isSearchActive) {
                        IconButton(
                            onClick = { isSearchActive = true },
                            modifier = Modifier.testTag("search_icon_button")
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Search library")
                        }
                    }

                    // Toggle Grid / List
                    IconButton(
                        onClick = { isGridView = !isGridView },
                        modifier = Modifier.testTag("view_mode_toggle")
                    ) {
                        Icon(
                            imageVector = if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                            contentDescription = if (isGridView) "Switch to List" else "Switch to Grid"
                        )
                    }

                    // Sort Menu
                    Box {
                        IconButton(
                            onClick = { showSortMenu = true },
                            modifier = Modifier.testTag("sort_menu_button")
                        ) {
                            Icon(Icons.Default.Sort, contentDescription = "Sort books")
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            SortOption.values().forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = option.title,
                                            fontWeight = if (currentSort == option) FontWeight.Bold else FontWeight.Normal,
                                            color = if (currentSort == option) AmberGold else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    onClick = {
                                        currentSort = option
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = AmberGold,
                contentColor = Color(0xFF0F172A),
                modifier = Modifier.testTag("add_book_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Book")
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Stats & Streak Banner
            stats?.let { s ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocalFireDepartment,
                                contentDescription = null,
                                tint = Color(0xFFF97316),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${s.currentStreakDays} day streak",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        Text(
                            text = "${s.totalTimeMinutes}m total read • ${s.totalPagesRead} pages",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Quick Resume Hero Card (Kindle style)
            if (currentlyReadingBook != null && searchQuery.isBlank() && selectedTab == 0) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("continue_reading_card")
                        .clickable { onBookClick(currentlyReadingBook.id) },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = AmberGold,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MenuBook,
                                contentDescription = null,
                                tint = Color(0xFF0F172A),
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "CONTINUE READING",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = AmberGold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = currentlyReadingBook.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (currentlyReadingBook.currentChapterTitle.isNotBlank()) {
                                Text(
                                    text = "${currentlyReadingBook.currentChapterTitle} • ${currentlyReadingBook.progressPercent.toInt()}%",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { currentlyReadingBook.progressPercent / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = AmberGold,
                                trackColor = Color.Gray.copy(alpha = 0.3f)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Resume reading",
                            tint = AmberGold,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Shelf / Category Tabs
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = AmberGold
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == index) AmberGold else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }
            }

            // Loading Indicator
            if (isImporting) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = AmberGold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Importing and extracting book metadata...", fontSize = 13.sp)
                }
            }

            // Empty State
            if (filteredBooks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Book,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "No books match '$searchQuery'" else "No books in this shelf",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Import your EPUB, PDF, or TXT ebooks to begin reading.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = {
                                filePickerLauncher.launch(
                                    arrayOf(
                                        "application/epub+zip",
                                        "application/pdf",
                                        "text/plain",
                                        "*/*"
                                    )
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AmberGold)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF0F172A))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Import eBook File", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                // Book Content (Grid or List)
                if (isGridView) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 150.dp),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredBooks, key = { it.id }) { book ->
                            BookGridCard(
                                book = book,
                                onClick = { onBookClick(book.id) },
                                onToggleFavorite = { isFav ->
                                    scope.launch { bookRepository.toggleFavorite(book.id, isFav) }
                                },
                                onDelete = {
                                    scope.launch { bookRepository.deleteBook(book) }
                                },
                                onStatusChange = { newStatus ->
                                    scope.launch {
                                        bookRepository.updateProgress(
                                            book.id,
                                            book.progressPercent,
                                            book.progressLocation,
                                            book.currentChapterTitle,
                                            newStatus
                                        )
                                    }
                                }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredBooks, key = { it.id }) { book ->
                            BookListRow(
                                book = book,
                                onClick = { onBookClick(book.id) },
                                onToggleFavorite = { isFav ->
                                    scope.launch { bookRepository.toggleFavorite(book.id, isFav) }
                                },
                                onDelete = {
                                    scope.launch { bookRepository.deleteBook(book) }
                                },
                                onStatusChange = { newStatus ->
                                    scope.launch {
                                        bookRepository.updateProgress(
                                            book.id,
                                            book.progressPercent,
                                            book.progressLocation,
                                            book.currentChapterTitle,
                                            newStatus
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Add Book Options Dialog
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = AmberGold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Books to Library")
                }
            },
            text = {
                Column {
                    Text(
                        text = "Lumina Reader supports EPUB 2 & 3, PDF, and TXT files with full reflowable text, offline reading, and themes.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Option 1: File Picker
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showAddDialog = false
                                filePickerLauncher.launch(
                                    arrayOf(
                                        "application/epub+zip",
                                        "application/pdf",
                                        "text/plain",
                                        "*/*"
                                    )
                                )
                            },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, tint = AmberGold)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Choose File from Storage", fontWeight = FontWeight.Bold)
                                Text("Select any EPUB, PDF, or TXT file on device", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Option 2: Scan Storage Rationale
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showAddDialog = false
                                showScanRationale = true
                            },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null, tint = AmberGold)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Scan Device Folders", fontWeight = FontWeight.Bold)
                                Text("Auto-discover ebooks in Downloads & Documents", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Option 3: Seed Sample Classics
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showAddDialog = false
                                scope.launch {
                                    bookRepository.initializeLibraryIfEmpty()
                                    Toast.makeText(context, "Classics library verified!", Toast.LENGTH_SHORT).show()
                                }
                            },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = AmberGold)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Sample Classic Books", fontWeight = FontWeight.Bold)
                                Text("Sherlock Holmes, Alice in Wonderland, Frankenstein", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Option 4: Download Online Sample PDF
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showAddDialog = false
                                isImporting = true
                                scope.launch {
                                    val result = bookRepository.downloadAndImportPdf(
                                        urlString = "https://raw.githubusercontent.com/mozilla/pdf.js/master/test/pdfs/tracemonkey.pdf",
                                        customTitle = "TraceMonkey: JIT Compiler"
                                    )
                                    isImporting = false
                                    when (result) {
                                        is ImportOutcome.Added ->
                                            Toast.makeText(context, "Downloaded '${result.book.title}' (${result.book.totalPages} pages)!", Toast.LENGTH_LONG).show()
                                        is ImportOutcome.AlreadyInLibrary ->
                                            Toast.makeText(context, "'${result.book.title}' is already in your library", Toast.LENGTH_SHORT).show()
                                        is ImportOutcome.Failed ->
                                            Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, tint = AmberGold)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Download Online PDF (Test)", fontWeight = FontWeight.Bold)
                                Text("Download Mozilla's TraceMonkey paper (14 pages)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Storage Scanner Rationale Dialog (Play Store Compliance)
    if (showScanRationale) {
        AlertDialog(
            onDismissRequest = { showScanRationale = false },
            title = { Text("Storage Scanner") },
            text = {
                Text(
                    "Lumina Reader scans your Documents and Downloads folders strictly to identify DRM-free e-book files (EPUB, PDF, TXT). Your personal documents and files remain completely private on your device and are never uploaded or shared."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showScanRationale = false
                        isImporting = true
                        scope.launch {
                            val discovered = bookRepository.scanAndImportDeviceDownloads()
                            isImporting = false
                            if (discovered.isNotEmpty()) {
                                Toast.makeText(context, "Imported ${discovered.size} book(s) from Downloads!", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "No new books found in Downloads. Select file manually.", Toast.LENGTH_SHORT).show()
                                filePickerLauncher.launch(
                                    arrayOf(
                                        "application/epub+zip",
                                        "application/pdf",
                                        "text/plain",
                                        "*/*"
                                    )
                                )
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AmberGold)
                ) {
                    Text("Scan Downloads", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showScanRationale = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
