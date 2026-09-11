package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ReaderTheme
import com.example.ui.screens.reader.DocumentPreparingView
import com.example.ui.screens.reader.SelectableParagraph
import com.example.ui.screens.reader.SelectionActionBar
import com.example.ui.screens.reader.TextSelection
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots of the new reading surfaces. They double as review artefacts: the PR and the
 * explainer doc show these images instead of asking a reviewer to imagine the change.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class ReaderScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun document_preparing_gate() {
        composeTestRule.setContent {
            MyApplicationTheme {
                DocumentPreparingView(
                    title = "Thinking in Java, 4th Edition",
                    done = 148,
                    total = 486,
                    errorMessage = null,
                    backgroundColor = Color(ReaderTheme.SEPIA.bgHex),
                    textColor = Color(ReaderTheme.SEPIA.textHex),
                    onReadNow = {},
                    onBack = {}
                )
            }
        }

        composeTestRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/document_preparing.png"
        )
    }

    @Test
    fun selection_action_bar() {
        composeTestRule.setContent {
            MyApplicationTheme {
                Box(
                    modifier = Modifier
                        .background(Color(ReaderTheme.SEPIA.bgHex))
                        .padding(vertical = 24.dp)
                ) {
                    SelectionActionBar(
                        selectedText = "trace trees as a unit of compilation",
                        wordCount = 7,
                        canExtendStart = true,
                        canShrinkStart = true,
                        canShrinkEnd = true,
                        canExtendEnd = true,
                        onNudgeStart = {},
                        onNudgeEnd = {},
                        onSelectSentence = {},
                        onSelectAll = {},
                        onSpeak = {},
                        onDefine = {},
                        onHighlight = {},
                        onAddNote = {},
                        onShare = {},
                        onClear = {}
                    )
                }
            }
        }

        composeTestRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/selection_action_bar.png"
        )
    }

    @Test
    fun paragraph_with_word_selection() {
        val paragraph = "Trace trees are a unit of compilation: the tracing just-in-time compiler " +
            "records the operations executed by a hot loop and compiles that trace to native code."

        composeTestRule.setContent {
            MyApplicationTheme {
                Box(
                    modifier = Modifier
                        .background(Color(ReaderTheme.SEPIA.bgHex))
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    SelectableParagraph(
                        paragraphIndex = 0,
                        text = paragraph,
                        fontSize = 18.sp,
                        lineHeight = 26.sp,
                        textColor = Color(ReaderTheme.SEPIA.textHex),
                        fontFamily = FontFamily.Serif,
                        textAlign = TextAlign.Justify,
                        activeSelection = TextSelection(
                            paragraphIndex = 0,
                            range = 0..21,
                            text = paragraph.substring(0, 22)
                        ),
                        onSelectionChange = {},
                        highlightColor = null,
                        isTtsSentenceActive = false
                    )
                }
            }
        }

        composeTestRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/paragraph_word_selection.png"
        )
    }
}
