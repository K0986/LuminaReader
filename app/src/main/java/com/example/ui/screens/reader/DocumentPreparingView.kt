package com.example.ui.screens.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmberGold

/**
 * The "process first, then open" gate for PDFs.
 *
 * A PDF is a description of marks on paper, not a stream of words, so the reader extracts the text
 * layer once before handing the book over. Doing it up front — visibly, with progress, and
 * resumably — is what makes word selection, narration and whole-book search work afterwards
 * without a stutter on every page turn.
 */
@Composable
fun DocumentPreparingView(
    title: String,
    done: Int,
    total: Int,
    errorMessage: String?,
    backgroundColor: Color,
    textColor: Color,
    onReadNow: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val safeTotal = total.coerceAtLeast(1)
    val fraction = (done.toFloat() / safeTotal.toFloat()).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(32.dp)
            .testTag("document_preparing"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoStories,
                contentDescription = null,
                tint = AmberGold,
                modifier = Modifier.size(44.dp)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Preparing “$title”",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = textColor,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (errorMessage != null) {
                    errorMessage
                } else {
                    "Reading the text layer so you can select words, listen to pages and search the whole book."
                },
                fontSize = 13.sp,
                color = textColor.copy(alpha = 0.75f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            if (errorMessage == null) {
                LinearProgressIndicator(
                    progress = { fraction },
                    color = AmberGold,
                    trackColor = textColor.copy(alpha = 0.15f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .testTag("document_preparing_progress")
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "$done of $safeTotal pages · ${(fraction * 100).toInt()}%",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = textColor.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "This happens once per book.",
                    fontSize = 11.sp,
                    color = textColor.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) {
                    Text("Back to library", color = textColor.copy(alpha = 0.8f))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onReadNow,
                    colors = ButtonDefaults.buttonColors(containerColor = AmberGold),
                    modifier = Modifier.testTag("document_preparing_read_now")
                ) {
                    Text(
                        text = if (errorMessage == null) "Read now" else "Open anyway",
                        color = Color(0xFF0F172A),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
