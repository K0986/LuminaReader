package com.example.ui.screens.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShortText
import androidx.compose.material.icons.filled.Subject
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmberGold
import com.example.ui.theme.HighlightAmber
import com.example.ui.theme.HighlightMint
import com.example.ui.theme.HighlightRose
import com.example.ui.theme.HighlightSky

/** Highlight palette, shared by the action bar and the notebook. */
val HighlightPalette: List<Pair<Color, String>> = listOf(
    HighlightAmber to "#FFE082",
    HighlightMint to "#A5D6A7",
    HighlightSky to "#90CAF9",
    HighlightRose to "#F48FB1"
)

/**
 * Floating toolbar shown while text is selected.
 *
 * It is deliberately independent of *how* the selection was made: reflowed text hands it a
 * character range, the PDF page hands it a platform selection, and both get the same actions.
 */
@Composable
fun SelectionActionBar(
    selectedText: String,
    wordCount: Int,
    canExtendStart: Boolean,
    canShrinkStart: Boolean,
    canShrinkEnd: Boolean,
    canExtendEnd: Boolean,
    onNudgeStart: (Int) -> Unit,
    onNudgeEnd: (Int) -> Unit,
    onSelectSentence: (() -> Unit)?,
    onSelectAll: (() -> Unit)?,
    onSpeak: (String) -> Unit,
    onDefine: (String) -> Unit,
    onHighlight: (String) -> Unit,
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .testTag("selection_action_bar")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Selected: $wordCount word${if (wordCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = AmberGold
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Cursor: ", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    NudgeButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        description = "Extend selection to the previous word",
                        enabled = canExtendStart
                    ) { onNudgeStart(-1) }
                    Text("S", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AmberGold)
                    NudgeButton(
                        icon = Icons.AutoMirrored.Filled.ArrowForward,
                        description = "Shrink selection from the start",
                        enabled = canShrinkStart
                    ) { onNudgeStart(1) }

                    Spacer(modifier = Modifier.width(6.dp))

                    NudgeButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        description = "Shrink selection from the end",
                        enabled = canShrinkEnd
                    ) { onNudgeEnd(-1) }
                    Text("E", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AmberGold)
                    NudgeButton(
                        icon = Icons.AutoMirrored.Filled.ArrowForward,
                        description = "Extend selection to the next word",
                        enabled = canExtendEnd
                    ) { onNudgeEnd(1) }

                    IconButton(onClick = onClear, modifier = Modifier.size(28.dp)) {
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ActionChip(
                    icon = Icons.Default.Headphones,
                    label = "Speak",
                    emphasised = true,
                    onClick = { onSpeak(selectedText) }
                )
                ActionChip(
                    icon = Icons.Default.Translate,
                    label = "Define",
                    onClick = {
                        val firstWord = selectedText.split(Regex("\\s+")).firstOrNull() ?: selectedText
                        onDefine(firstWord)
                    }
                )
                if (onSelectSentence != null) {
                    ActionChip(icon = Icons.Default.ShortText, label = "Sentence", onClick = onSelectSentence)
                }
                if (onSelectAll != null) {
                    ActionChip(icon = Icons.Default.Subject, label = "Paragraph", onClick = onSelectAll)
                }
                ActionChip(
                    icon = Icons.Default.FormatColorFill,
                    label = "Highlight",
                    emphasised = showColorPicker,
                    onClick = { showColorPicker = !showColorPicker }
                )
                ActionChip(icon = Icons.AutoMirrored.Filled.NoteAdd, label = "Note", onClick = onAddNote)
                ActionChip(
                    icon = Icons.Default.ContentCopy,
                    label = "Copy",
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Lumina Selection", selectedText))
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                )
                ActionChip(icon = Icons.Default.Share, label = "Share", onClick = onShare)
            }

            AnimatedVisibility(visible = showColorPicker) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Save Color:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        HighlightPalette.forEach { (color, hex) ->
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(color)
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

@Composable
private fun NudgeButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(28.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (enabled) AmberGold else Color.Gray.copy(alpha = 0.3f),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun ActionChip(
    icon: ImageVector,
    label: String,
    emphasised: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (emphasised) AmberGold.copy(alpha = 0.20f) else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (emphasised) AmberGold else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (emphasised) FontWeight.Bold else FontWeight.Medium,
                color = if (emphasised) AmberGold else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
