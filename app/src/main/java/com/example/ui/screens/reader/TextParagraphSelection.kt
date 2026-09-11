package com.example.ui.screens.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmberGold
import com.example.ui.theme.HighlightAmber
import com.example.ui.theme.HighlightMint
import com.example.ui.theme.HighlightRose
import com.example.ui.theme.HighlightSky

data class CursorSelectionState(
    val paragraphIndex: Int,
    val startWordIndex: Int,
    val endWordIndex: Int,
    val selectedText: String
)

/**
 * Interactive text block with word-level cursor handles.
 * Users can tap or long-press any word to drop cursor handles,
 * tap other words or use precision arrows to stretch the selection range,
 * and perform actions (Speak, Define, Highlight, Note, Copy).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SelectableParagraphWithCursors(
    paragraphIndex: Int,
    text: String,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    textColor: Color,
    fontFamily: FontFamily,
    textAlign: TextAlign,
    activeSelection: CursorSelectionState?,
    onSelectionChange: (CursorSelectionState?) -> Unit,
    highlightColor: Color?,
    isTtsSentenceActive: Boolean,
    modifier: Modifier = Modifier
) {
    val words = remember(text) {
        text.split("\\s+".toRegex()).filter { it.isNotBlank() }
    }

    val isThisParagraphSelected = activeSelection?.paragraphIndex == paragraphIndex
    val selStart = if (isThisParagraphSelected) activeSelection!!.startWordIndex else -1
    val selEnd = if (isThisParagraphSelected) activeSelection!!.endWordIndex else -1

    val paragraphBg = when {
        isTtsSentenceActive -> AmberGold.copy(alpha = 0.3f)
        highlightColor != null -> highlightColor.copy(alpha = 0.35f)
        else -> Color.Transparent
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(paragraphBg)
            .padding(horizontal = 4.dp, vertical = 3.dp)
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalArrangement = Arrangement.Center
        ) {
            words.forEachIndexed { wordIdx, word ->
                val isSelected = isThisParagraphSelected && wordIdx in selStart..selEnd
                val isStartCursor = isThisParagraphSelected && wordIdx == selStart
                val isEndCursor = isThisParagraphSelected && wordIdx == selEnd

                Box(
                    modifier = Modifier
                        .padding(end = 4.dp, bottom = 4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (isSelected) AmberGold.copy(alpha = 0.45f) else Color.Transparent
                        )
                        .then(
                            if (isSelected) {
                                Modifier.border(
                                    width = 1.dp,
                                    color = AmberGold.copy(alpha = 0.8f),
                                    shape = RoundedCornerShape(4.dp)
                                )
                            } else Modifier
                        )
                        .pointerInput(wordIdx, isThisParagraphSelected, selStart, selEnd) {
                            detectTapGestures(
                                onLongPress = {
                                    // Start cursor selection on this exact word
                                    onSelectionChange(
                                        CursorSelectionState(
                                            paragraphIndex = paragraphIndex,
                                            startWordIndex = wordIdx,
                                            endWordIndex = wordIdx,
                                            selectedText = word
                                        )
                                    )
                                },
                                onTap = {
                                    if (isThisParagraphSelected) {
                                        // Extend selection with cursor
                                        val newStart = minOf(selStart, wordIdx)
                                        val newEnd = maxOf(selEnd, wordIdx)
                                        val newText = words.subList(newStart, newEnd + 1).joinToString(" ")
                                        onSelectionChange(
                                            CursorSelectionState(
                                                paragraphIndex = paragraphIndex,
                                                startWordIndex = newStart,
                                                endWordIndex = newEnd,
                                                selectedText = newText
                                            )
                                        )
                                    } else {
                                        // Drop cursor on this word
                                        onSelectionChange(
                                            CursorSelectionState(
                                                paragraphIndex = paragraphIndex,
                                                startWordIndex = wordIdx,
                                                endWordIndex = wordIdx,
                                                selectedText = word
                                            )
                                        )
                                    }
                                }
                            )
                        }
                        .padding(horizontal = 2.dp, vertical = 1.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Start cursor pin icon
                        if (isStartCursor) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp, 16.dp)
                                    .background(AmberGold, RoundedCornerShape(2.dp))
                                    .padding(end = 2.dp)
                            )
                        }

                        Text(
                            text = word,
                            fontSize = fontSize,
                            lineHeight = lineHeight,
                            fontFamily = fontFamily,
                            color = textColor,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )

                        // End cursor pin icon
                        if (isEndCursor) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp, 16.dp)
                                    .background(AmberGold, RoundedCornerShape(2.dp))
                                    .padding(start = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Floating Cursor Selection Action Toolbar.
 * Provides precision cursor adjustment arrows, Text-to-Speech narration of selection,
 * dictionary lookup, note taking, highlight color palette, and copy.
 */
@Composable
fun CursorSelectionFloatingBar(
    selection: CursorSelectionState,
    totalWordsInParagraph: Int,
    allParagraphWords: List<String>,
    onNudgeStart: (delta: Int) -> Unit,
    onNudgeEnd: (delta: Int) -> Unit,
    onSelectSentence: () -> Unit,
    onSelectAllParagraph: () -> Unit,
    onSpeak: (String) -> Unit,
    onDefine: (String) -> Unit,
    onHighlight: (colorHex: String) -> Unit,
    onAddNote: () -> Unit,
    onShare: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showColorPicker by remember { mutableStateOf(false) }

    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // Header: Selected word count & Cursor Nudge arrows
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val wordCount = (selection.endWordIndex - selection.startWordIndex + 1).coerceAtLeast(1)
                Text(
                    text = "Selected: $wordCount word${if (wordCount > 1) "s" else ""}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = AmberGold
                )

                // Cursor adjustment controls
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Cursor: ", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    // Start cursor nudge buttons
                    IconButton(
                        onClick = { onNudgeStart(-1) },
                        enabled = selection.startWordIndex > 0,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Expand start cursor backward",
                            tint = if (selection.startWordIndex > 0) AmberGold else Color.Gray.copy(alpha = 0.3f),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Text("S", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AmberGold)

                    IconButton(
                        onClick = { onNudgeStart(1) },
                        enabled = selection.startWordIndex < selection.endWordIndex,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Shrink start cursor forward",
                            tint = if (selection.startWordIndex < selection.endWordIndex) AmberGold else Color.Gray.copy(alpha = 0.3f),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // End cursor nudge buttons
                    IconButton(
                        onClick = { onNudgeEnd(-1) },
                        enabled = selection.endWordIndex > selection.startWordIndex,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Shrink end cursor backward",
                            tint = if (selection.endWordIndex > selection.startWordIndex) AmberGold else Color.Gray.copy(alpha = 0.3f),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Text("E", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AmberGold)

                    IconButton(
                        onClick = { onNudgeEnd(1) },
                        enabled = selection.endWordIndex < totalWordsInParagraph - 1,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Expand end cursor forward",
                            tint = if (selection.endWordIndex < totalWordsInParagraph - 1) AmberGold else Color.Gray.copy(alpha = 0.3f),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = onClear,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close selection",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Action Buttons Row: Speak, Define, Highlight, Note, Copy, Share
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 🗣️ Speak selection out loud
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AmberGold.copy(alpha = 0.15f),
                    modifier = Modifier.clickable { onSpeak(selection.selectedText) }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Headphones, contentDescription = null, tint = AmberGold, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Speak", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AmberGold)
                    }
                }

                // 📖 Dictionary Define
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clickable {
                        val firstWord = selection.selectedText.split("\\s+".toRegex()).firstOrNull() ?: selection.selectedText
                        onDefine(firstWord)
                    }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Define", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }

                // 🎨 Highlight Color Picker Toggle
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (showColorPicker) AmberGold.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clickable { showColorPicker = !showColorPicker }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.FormatColorFill, contentDescription = null, tint = AmberGold, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Highlight", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }

                // 📝 Note
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clickable { onAddNote() }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.NoteAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Note", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }

                // 📋 Copy
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clickable {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Lumina Selection", selection.selectedText)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }

                // 🔗 Share
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clickable { onShare() }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // Expandable Color Chips Row
            AnimatedVisibility(visible = showColorPicker) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Save Color:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)

                        val colors = listOf(
                            Pair(HighlightAmber, "#FFE082"),
                            Pair(HighlightMint, "#A5D6A7"),
                            Pair(HighlightSky, "#90CAF9"),
                            Pair(HighlightRose, "#F48FB1")
                        )

                        colors.forEach { (c, hex) ->
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(c)
                                    .border(1.dp, Color.DarkGray.copy(alpha = 0.3f), CircleShape)
                                    .clickable {
                                        onHighlight(hex)
                                        showColorPicker = false
                                    }
                            )
                        }
                    }
                }
            }
        }
    }
}
