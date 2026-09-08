package com.example.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PageAnimation
import com.example.data.model.ReadingMode
import com.example.data.repository.SettingsRepository
import com.example.ui.theme.AmberGold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings by settingsRepository.settings.collectAsState()

    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Preferences",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Reading Experience
            item {
                Text("Reading Mode & Navigation", fontWeight = FontWeight.Bold, color = AmberGold, fontSize = 12.sp)
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Default Reading Layout", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = settings.readingMode == ReadingMode.PAGINATED,
                                onClick = { settingsRepository.updateSettings(settings.copy(readingMode = ReadingMode.PAGINATED)) },
                                label = { Text("Paginated (Flip)") }
                            )
                            FilterChip(
                                selected = settings.readingMode == ReadingMode.CONTINUOUS_SCROLL,
                                onClick = { settingsRepository.updateSettings(settings.copy(readingMode = ReadingMode.CONTINUOUS_SCROLL)) },
                                label = { Text("Continuous Scroll") }
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Text("Page Turn Animation", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PageAnimation.values().forEach { anim ->
                                FilterChip(
                                    selected = settings.pageTurnAnimation == anim,
                                    onClick = { settingsRepository.updateSettings(settings.copy(pageTurnAnimation = anim)) },
                                    label = { Text(anim.displayName) }
                                )
                            }
                        }
                    }
                }
            }

            // Display & Hardware
            item {
                Text("Display & Hardware", fontWeight = FontWeight.Bold, color = AmberGold, fontSize = 12.sp)
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Keep Screen Awake", fontWeight = FontWeight.Bold)
                                Text("Prevents device from sleeping while reading", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = settings.keepScreenOn,
                                onCheckedChange = { settingsRepository.updateSettings(settings.copy(keepScreenOn = it)) },
                                colors = SwitchDefaults.colors(checkedThumbColor = AmberGold)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Volume Keys Turn Pages", fontWeight = FontWeight.Bold)
                                Text("Use physical buttons for one-handed page turns", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = settings.volumeKeyPageTurn,
                                onCheckedChange = { settingsRepository.updateSettings(settings.copy(volumeKeyPageTurn = it)) },
                                colors = SwitchDefaults.colors(checkedThumbColor = AmberGold)
                            )
                        }
                    }
                }
            }

            // Daily Goal
            item {
                Text("Daily Habit", fontWeight = FontWeight.Bold, color = AmberGold, fontSize = 12.sp)
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Daily Reading Goal", fontWeight = FontWeight.Bold)
                        Text("Target reading time per day", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(10, 20, 30, 45, 60).forEach { mins ->
                                FilterChip(
                                    selected = settings.dailyReadingGoalMinutes == mins,
                                    onClick = { settingsRepository.updateSettings(settings.copy(dailyReadingGoalMinutes = mins)) },
                                    label = { Text("${mins}m") }
                                )
                            }
                        }
                    }
                }
            }

            // Privacy & Security
            item {
                Text("Privacy & Security", fontWeight = FontWeight.Bold, color = AmberGold, fontSize = 12.sp)
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showPrivacyDialog = true }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = AmberGold)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Offline-First Privacy Policy", fontWeight = FontWeight.Bold)
                                Text("Zero tracking, 100% on-device SQLite database", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("App PIN Lock", fontWeight = FontWeight.Bold)
                                Text(if (settings.appLockEnabled) "PIN protection enabled" else "Require 4-digit PIN to open library", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = settings.appLockEnabled,
                                onCheckedChange = { enabled ->
                                    if (enabled) {
                                        showPinDialog = true
                                    } else {
                                        settingsRepository.updateSettings(settings.copy(appLockEnabled = false, appLockPin = ""))
                                    }
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = AmberGold)
                            )
                        }
                    }
                }
            }

            // About & Battery Info
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.BatteryChargingFull, contentDescription = null, tint = Color(0xFF10B981))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("OLED True Black Optimization", fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Lumina Reader's OLED theme uses 100% black (#000000) pixels, allowing AMOLED displays to completely switch off individual subpixels for extended battery life during long reading sessions.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // Privacy Policy Dialog
    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("Privacy & Data Safety") },
            text = {
                Text(
                    "Lumina Reader is built on an offline-first architecture:\n\n" +
                            "• No personal data, reading habits, or library contents are ever transmitted to external servers.\n" +
                            "• All book files, covers, bookmarks, notes, and reading sessions are stored strictly on your local device via encrypted Room SQLite.\n" +
                            "• No DRM cracking or unauthorized copying is performed.\n" +
                            "• Device storage access is used solely to discover and open user-selected e-books."
                )
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) { Text("Got it") }
            }
        )
    }

    // PIN Setup Dialog
    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text("Set 4-Digit Security PIN") },
            text = {
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) pinInput = it },
                    placeholder = { Text("Enter 4 digits (e.g. 1234)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (pinInput.length == 4) {
                            settingsRepository.updateSettings(settings.copy(appLockEnabled = true, appLockPin = pinInput))
                            Toast.makeText(context, "PIN lock enabled", Toast.LENGTH_SHORT).show()
                            showPinDialog = false
                            pinInput = ""
                        } else {
                            Toast.makeText(context, "PIN must be 4 digits", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Save PIN")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) { Text("Cancel") }
            }
        )
    }
}
