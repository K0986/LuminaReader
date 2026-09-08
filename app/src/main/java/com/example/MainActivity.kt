package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.data.repository.BookRepository
import com.example.data.repository.ReadingSessionRepository
import com.example.data.repository.SettingsRepository
import com.example.ui.LuminaApp
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var bookRepository: BookRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var sessionRepository: ReadingSessionRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        bookRepository = BookRepository(this)
        settingsRepository = SettingsRepository(this)
        sessionRepository = ReadingSessionRepository(this)

        val intentUri = intent?.data

        setContent {
            MyApplicationTheme {
                var initialBookId by remember { mutableStateOf<Long?>(null) }
                val scope = rememberCoroutineScope()

                LaunchedEffect(intentUri) {
                    if (intentUri != null) {
                        scope.launch {
                            val res = bookRepository.importBookFromUri(intentUri)
                            res.onSuccess { book ->
                                initialBookId = book.id
                            }
                        }
                    }
                }

                LuminaApp(
                    bookRepository = bookRepository,
                    settingsRepository = settingsRepository,
                    sessionRepository = sessionRepository,
                    initialOpenBookId = initialBookId,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

