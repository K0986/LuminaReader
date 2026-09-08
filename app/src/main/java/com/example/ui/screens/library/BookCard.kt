package com.example.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.Book
import com.example.ui.theme.AmberGold
import java.io.File

@Composable
fun BookGridCard(
    book: Book,
    onClick: () -> Unit,
    onToggleFavorite: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onStatusChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("book_card_${book.id}")
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            // Book Cover Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.68f)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .background(Color(0xFF1E293B))
            ) {
                if (book.coverPath != null && File(book.coverPath).exists()) {
                    AsyncImage(
                        model = File(book.coverPath),
                        contentDescription = "Cover for ${book.title}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Fallback placeholder cover with title and author
                    FallbackCover(book.title, book.author, book.format)
                }

                // Format Pill Badge (e.g. EPUB, PDF, TXT)
                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Text(
                        text = book.format,
                        color = AmberGold,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Favorite Icon Button
                IconButton(
                    onClick = { onToggleFavorite(!book.isFavorite) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(36.dp)
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = if (book.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (book.isFavorite) "Favorited" else "Add to favorites",
                        tint = if (book.isFavorite) Color(0xFFEF4444) else Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Progress overlay bar at bottom of cover
                if (book.progressPercent > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(Color.Black.copy(alpha = 0.75f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${book.progressPercent.toInt()}%",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                if (book.currentChapterTitle.isNotBlank()) {
                                    Text(
                                        text = book.currentChapterTitle,
                                        color = Color.LightGray,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false).padding(start = 4.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            LinearProgressIndicator(
                                progress = { book.progressPercent / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = AmberGold,
                                trackColor = Color.Gray.copy(alpha = 0.5f),
                            )
                        }
                    }
                }
            }

            // Book Details
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (book.readingStatus == "FINISHED") "Mark as Unread" else "Mark as Finished") },
                                onClick = {
                                    showMenu = false
                                    onStatusChange(if (book.readingStatus == "FINISHED") "UNREAD" else "FINISHED")
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (book.isFavorite) "Remove from Favorites" else "Add to Favorites") },
                                onClick = {
                                    showMenu = false
                                    onToggleFavorite(!book.isFavorite)
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Favorite, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Book", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMenu = false
                                    onDelete()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = book.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun BookListRow(
    book: Book,
    onClick: () -> Unit,
    onToggleFavorite: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onStatusChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("book_row_${book.id}")
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .height(80.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF1E293B))
            ) {
                if (book.coverPath != null && File(book.coverPath).exists()) {
                    AsyncImage(
                        model = File(book.coverPath),
                        contentDescription = "Cover for ${book.title}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    FallbackCover(book.title, book.author, book.format, isCompact = true)
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = book.format,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                    if (book.readingStatus == "READING") {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = AmberGold.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "Reading",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AmberGold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    } else if (book.readingStatus == "FINISHED") {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = Color(0xFF10B981).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "Finished",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF10B981),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = book.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (book.progressPercent > 0f) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        LinearProgressIndicator(
                            progress = { book.progressPercent / 100f },
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = AmberGold,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${book.progressPercent.toInt()}%",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Actions
            IconButton(
                onClick = { onToggleFavorite(!book.isFavorite) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (book.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (book.isFavorite) "Favorited" else "Favorite",
                    tint = if (book.isFavorite) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Menu",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(if (book.readingStatus == "FINISHED") "Mark as Unread" else "Mark as Finished") },
                        onClick = {
                            showMenu = false
                            onStatusChange(if (book.readingStatus == "FINISHED") "UNREAD" else "FINISHED")
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete Book", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun FallbackCover(title: String, author: String, format: String, isCompact: Boolean = false) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E293B))
            .padding(if (isCompact) 4.dp else 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Book,
                contentDescription = null,
                tint = AmberGold,
                modifier = Modifier.size(if (isCompact) 20.dp else 36.dp)
            )
            if (!isCompact) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = author,
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
