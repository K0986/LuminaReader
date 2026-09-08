package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.repository.BookRepository
import com.example.data.repository.ReadingSessionRepository
import com.example.data.repository.SettingsRepository
import com.example.ui.navigation.Screen
import com.example.ui.screens.library.LibraryScreen
import com.example.ui.screens.notes.NotesScreen
import com.example.ui.screens.reader.ReaderScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.stats.StatsScreen
import com.example.ui.theme.AmberGold
import kotlinx.coroutines.launch

@Composable
fun LuminaApp(
    bookRepository: BookRepository,
    settingsRepository: SettingsRepository,
    sessionRepository: ReadingSessionRepository,
    initialOpenBookId: Long? = null,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val settings by settingsRepository.settings.collectAsState()
    var isUnlocked by remember { mutableStateOf(!settings.appLockEnabled) }
    var enteredPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }

    // Seed database with classics on first launch if empty
    LaunchedEffect(Unit) {
        bookRepository.initializeLibraryIfEmpty()
    }

    // Direct open intent if launched with a book
    LaunchedEffect(initialOpenBookId) {
        if (initialOpenBookId != null && initialOpenBookId > 0L) {
            navController.navigate(Screen.Reader.createRoute(initialOpenBookId))
        }
    }

    if (settings.appLockEnabled && !isUnlocked) {
        // App Lock PIN Screen
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = AmberGold, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Lumina Reader Locked", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Enter your 4-digit PIN to access library", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = enteredPin,
                        onValueChange = {
                            if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                                enteredPin = it
                                pinError = false
                                if (it.length == 4) {
                                    if (it == settings.appLockPin) {
                                        isUnlocked = true
                                    } else {
                                        pinError = true
                                    }
                                }
                            }
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        isError = pinError,
                        placeholder = { Text("4-digit PIN") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (pinError) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Incorrect PIN", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            if (enteredPin == settings.appLockPin) {
                                isUnlocked = true
                            } else {
                                pinError = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AmberGold),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Unlock Library", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        return
    }

    // Hide bottom navigation bar when inside the immersive Reader screen
    val isReadingScreen = currentRoute?.startsWith("reader/") == true

    Scaffold(
        bottomBar = {
            if (!isReadingScreen) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.testTag("main_bottom_nav")
                ) {
                    NavigationBarItem(
                        selected = currentRoute == Screen.Library.route,
                        onClick = {
                            if (currentRoute != Screen.Library.route) {
                                navController.navigate(Screen.Library.route) {
                                    popUpTo(Screen.Library.route) { inclusive = true }
                                }
                            }
                        },
                        icon = { Icon(Icons.Default.MenuBook, contentDescription = "Library") },
                        label = { Text("Library") },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = AmberGold, indicatorColor = AmberGold.copy(alpha = 0.2f))
                    )
                    NavigationBarItem(
                        selected = currentRoute == Screen.Notes.route,
                        onClick = {
                            if (currentRoute != Screen.Notes.route) {
                                navController.navigate(Screen.Notes.route) {
                                    popUpTo(Screen.Library.route)
                                }
                            }
                        },
                        icon = { Icon(Icons.Default.FormatQuote, contentDescription = "Notebook") },
                        label = { Text("Notebook") },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = AmberGold, indicatorColor = AmberGold.copy(alpha = 0.2f))
                    )
                    NavigationBarItem(
                        selected = currentRoute == Screen.Stats.route,
                        onClick = {
                            if (currentRoute != Screen.Stats.route) {
                                navController.navigate(Screen.Stats.route) {
                                    popUpTo(Screen.Library.route)
                                }
                            }
                        },
                        icon = { Icon(Icons.Default.Insights, contentDescription = "Insights") },
                        label = { Text("Insights") },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = AmberGold, indicatorColor = AmberGold.copy(alpha = 0.2f))
                    )
                    NavigationBarItem(
                        selected = currentRoute == Screen.Settings.route,
                        onClick = {
                            if (currentRoute != Screen.Settings.route) {
                                navController.navigate(Screen.Settings.route) {
                                    popUpTo(Screen.Library.route)
                                }
                            }
                        },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = AmberGold, indicatorColor = AmberGold.copy(alpha = 0.2f))
                    )
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Library.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Library.route) {
                LibraryScreen(
                    bookRepository = bookRepository,
                    sessionRepository = sessionRepository,
                    onBookClick = { bookId ->
                        navController.navigate(Screen.Reader.createRoute(bookId))
                    }
                )
            }

            composable(
                route = Screen.Reader.route,
                arguments = listOf(navArgument("bookId") { type = NavType.LongType })
            ) { backStackEntry ->
                val bookId = backStackEntry.arguments?.getLong("bookId") ?: 0L
                ReaderScreen(
                    bookId = bookId,
                    bookRepository = bookRepository,
                    settingsRepository = settingsRepository,
                    sessionRepository = sessionRepository,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Notes.route) {
                NotesScreen(
                    bookRepository = bookRepository,
                    onNavigateToBook = { bookId ->
                        navController.navigate(Screen.Reader.createRoute(bookId))
                    }
                )
            }

            composable(Screen.Stats.route) {
                StatsScreen(
                    sessionRepository = sessionRepository,
                    settingsRepository = settingsRepository
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(settingsRepository = settingsRepository)
            }
        }
    }
}
