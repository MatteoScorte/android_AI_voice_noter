package com.transcriber.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.saveable.rememberSaveable
import android.net.Uri
import com.transcriber.app.data.InboxItem
import com.transcriber.app.data.Meeting
import com.transcriber.app.data.MeetingStatus
import com.transcriber.app.ui.theme.*
import com.transcriber.app.viewmodel.HomeUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onStartRecording: (String, String) -> Unit,
    onPauseRecording: () -> Unit,
    onResumeRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onMeetingClick: (String) -> Unit,
    onDeleteMeeting: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onImportFile: (Uri) -> Unit,
    onProcessInboxItem: (InboxItem, String, String) -> Unit,
    onDeleteInboxItem: (String) -> Unit
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    var currentTab by rememberSaveable { mutableIntStateOf(0) }
    var meetingToDelete by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Audio Transcriber", fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = TextWhite) },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, "Impostazioni", tint = TextWhite) 
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = DarkSurface,
                contentColor = TextWhite,
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Mic, contentDescription = "Record") },
                    label = { Text("REC", letterSpacing = 1.sp) },
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = DarkBackground,
                        selectedTextColor = AccentGreen,
                        indicatorColor = AccentGreen,
                        unselectedIconColor = TextGray,
                        unselectedTextColor = TextGray
                    )
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.History, contentDescription = "History") },
                    label = { Text("HISTORY", letterSpacing = 1.sp) },
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = DarkBackground,
                        selectedTextColor = AccentGreen,
                        indicatorColor = AccentGreen,
                        unselectedIconColor = TextGray,
                        unselectedTextColor = TextGray
                    )
                )
                NavigationBarItem(
                    icon = {
                        BadgedBox(
                            badge = {
                                if (uiState.inboxItems.isNotEmpty()) {
                                    Badge(containerColor = AccentGreen, contentColor = DarkBackground) {
                                        Text("${uiState.inboxItems.size}")
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.MoveToInbox, contentDescription = "Inbox")
                        }
                    },
                    label = { Text("INBOX", letterSpacing = 1.sp) },
                    selected = currentTab == 2,
                    onClick = { currentTab = 2 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = DarkBackground,
                        selectedTextColor = AccentGreen,
                        indicatorColor = AccentGreen,
                        unselectedIconColor = TextGray,
                        unselectedTextColor = TextGray
                    )
                )
            }
        },
        containerColor = DarkBackground
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (currentTab) {
                0 -> RecordTab(
                    uiState = uiState,
                    onStartClick = { showDialog = true },
                    onPause = onPauseRecording,
                    onResume = onResumeRecording,
                    onStop = onStopRecording
                )
                1 -> HistoryTab(
                    uiState = uiState,
                    onMeetingClick = onMeetingClick,
                    onDeleteMeeting = { meetingId -> meetingToDelete = meetingId }
                )
                2 -> InboxTab(
                    items = uiState.inboxItems,
                    onImportFile = onImportFile,
                    onProcess = onProcessInboxItem,
                    onDelete = onDeleteInboxItem
                )
            }
        }
    }

    if (meetingToDelete != null) {
        Dialog(onDismissRequest = { meetingToDelete = null }) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkBackground),
                border = BorderStroke(1.dp, DarkSurfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.DeleteOutline, null, tint = ErrorRed, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("ELIMINA", letterSpacing = 1.sp, style = MaterialTheme.typography.labelLarge, color = ErrorRed, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    Text("Sei sicuro di voler eliminare questo audio e il verbale? L'azione è irreversibile.", color = TextGray, style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(Modifier.height(32.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedButton(
                            onClick = { meetingToDelete = null },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite),
                            border = BorderStroke(1.dp, DarkSurfaceVariant),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Text("ANNULLA", letterSpacing = 1.sp, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedButton(
                            onClick = {
                                onDeleteMeeting(meetingToDelete!!)
                                meetingToDelete = null
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                            border = BorderStroke(1.dp, ErrorRed.copy(alpha=0.5f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Text("ELIMINA", letterSpacing = 1.sp, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        NewRecordingDialog(
            onDismiss = { showDialog = false },
            onConfirm = { title, language ->
                showDialog = false
                currentTab = 0 // Ensure we stay on the record tab
                onStartRecording(title, language)
            }
        )
    }
}

@Composable
fun RecordTab(
    uiState: HomeUiState,
    onStartClick: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!uiState.isRecording) {
            // Unpulsing big start button
            RecordingButtonCircle(
                isRecording = false,
                isPaused = false,
                onClick = onStartClick
            )
            Spacer(Modifier.height(32.dp))
            Text(
                "TAP TO START RECORDING", 
                color = AccentGreen, 
                letterSpacing = 2.sp, 
                fontSize = 14.sp, 
                fontWeight = FontWeight.SemiBold
            )
        } else {
            // Recording active
            Text(
                text = formatDuration(uiState.recordingDurationMs),
                color = if (uiState.isPaused) TextGray else ErrorRed,
                fontSize = 48.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = if (uiState.isPaused) "PAUSED" else "RECORDING...",
                color = if (uiState.isPaused) TextGray else ErrorRed.copy(alpha=0.8f),
                letterSpacing = 4.sp,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(64.dp))
            
            // Pulse circle indicating recording
            RecordingButtonCircle(
                isRecording = true,
                isPaused = uiState.isPaused,
                onClick = {} // Interaction now handled by buttons below
            )
            
            Spacer(Modifier.height(64.dp))
            
            // Action buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pause / Resume Button
                OutlinedIconButton(
                    onClick = { if (uiState.isPaused) onResume() else onPause() },
                    icon = if (uiState.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    text = if (uiState.isPaused) "RESUME" else "PAUSE",
                    color = AccentGreen
                )
                
                // Stop Button
                OutlinedIconButton(
                    onClick = onStop,
                    icon = Icons.Default.Stop,
                    text = "STOP",
                    color = ErrorRed
                )
            }
        }
    }
}

@Composable
fun OutlinedIconButton(onClick: () -> Unit, icon: ImageVector, text: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .border(1.dp, color.copy(alpha=0.5f), CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = text, tint = color, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(text, color = color, fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun RecordingButtonCircle(isRecording: Boolean, isPaused: Boolean, onClick: () -> Unit) {
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1000, easing = EaseInOutSine), RepeatMode.Reverse), label = "scale"
    )
    val scale = if (isRecording && !isPaused) pulse else 1f
    
    val buttonColor = if (isRecording) (if (isPaused) TextGray else ErrorRed) else AccentGreen
    val icon = if (isRecording) Icons.Default.Mic else Icons.Default.MicNone

    Box(
        modifier = Modifier
            .size(120.dp)
            .scale(scale)
            .clip(CircleShape)
            .border(2.dp, buttonColor, CircleShape)
            .clickable(enabled = !isRecording, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isRecording && !isPaused) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
                    .clip(CircleShape)
                    .background(buttonColor.copy(alpha = 0.1f))
            )
        }
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = buttonColor,
            modifier = Modifier.size(48.dp)
        )
    }
}

@Composable
fun HistoryTab(
    uiState: HomeUiState,
    onMeetingClick: (String) -> Unit,
    onDeleteMeeting: (String) -> Unit
) {
    if (uiState.meetings.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.History, null, tint = TextGray.copy(alpha = 0.3f), modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(16.dp))
                Text("No recordings yet", color = TextGray.copy(alpha = 0.6f), fontWeight = FontWeight.Medium)
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("RECORDING HISTORY", style = MaterialTheme.typography.labelMedium, letterSpacing = 2.sp, color = TextGray)
                Text("${uiState.meetings.size} items", style = MaterialTheme.typography.labelMedium, color = TextGray)
            }
            Spacer(Modifier.height(16.dp))
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(uiState.meetings) { meeting ->
                    MeetingCard(meeting, { onMeetingClick(meeting.id) }, { onDeleteMeeting(meeting.id) })
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewRecordingDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, language: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var language by remember { mutableStateOf("it") }
    var languageExpanded by remember { mutableStateOf(false) }
    
    val availableLanguages = listOf(
        "it" to "🇮🇹 Italiano", 
        "en" to "🇬🇧 English", 
        "auto" to "🌍 Auto-Detect"
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.dp, DarkSurfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("NEW RECORDING", letterSpacing = 1.sp, style = MaterialTheme.typography.labelLarge, color = AccentGreen, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(24.dp))
                
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Meeting Title (Optional)", style = MaterialTheme.typography.bodySmall) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite, unfocusedTextColor = TextWhite,
                        focusedBorderColor = AccentGreen, unfocusedBorderColor = DarkSurfaceVariant,
                        cursorColor = AccentGreen, focusedLabelColor = AccentGreen, unfocusedLabelColor = TextGray
                    ),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    shape = RoundedCornerShape(8.dp)
                )
                Spacer(Modifier.height(16.dp))
                
                ExposedDropdownMenuBox(expanded = languageExpanded, onExpandedChange = { languageExpanded = it }) {
                    val selectedLangDisplay = availableLanguages.find { it.first == language }?.second ?: language
                    OutlinedTextField(
                        value = selectedLangDisplay, onValueChange = {},
                        readOnly = true, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(languageExpanded) },
                        label = { Text("Language", style = MaterialTheme.typography.bodySmall, color = AccentGreen) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextWhite, unfocusedTextColor = TextWhite,
                            focusedBorderColor = AccentGreen, unfocusedBorderColor = DarkSurfaceVariant,
                            cursorColor = AccentGreen
                        ),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        shape = RoundedCornerShape(8.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = languageExpanded, 
                        onDismissRequest = { languageExpanded = false },
                        modifier = Modifier.background(DarkCard)
                    ) {
                        availableLanguages.forEach { (langCode, langName) ->
                            DropdownMenuItem(
                                text = { Text(langName, color = TextWhite, style = MaterialTheme.typography.bodyMedium) },
                                onClick = { language = langCode; languageExpanded = false },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                            )
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))
                
                OutlinedButton(
                    onClick = { onConfirm(title, language) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentGreen),
                    border = BorderStroke(1.dp, AccentGreen.copy(alpha=0.5f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("START RECORDING", letterSpacing = 2.sp, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun MeetingCard(meeting: Meeting, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, DarkSurfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            val sColor = statusColor(meeting.status)
            Icon(
                imageVector = statusIcon(meeting.status),
                contentDescription = null,
                tint = sColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(meeting.title, color = TextWhite, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(statusLabel(meeting.status).uppercase(), color = sColor, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
                    if (meeting.durationMs > 0) {
                        Text("  •  ${formatDuration(meeting.durationMs)}", color = TextGray, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (meeting.isShared) {
                Icon(
                    imageVector = Icons.Default.CloudDone,
                    contentDescription = "Condiviso",
                    tint = AccentTeal,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.DeleteOutline, "Delete", tint = TextGray, modifier = Modifier.size(20.dp))
            }
        }
    }
}

private fun statusColor(s: MeetingStatus) = when (s) {
    MeetingStatus.RECORDING -> ErrorRed
    MeetingStatus.RECORDED -> TextWhite
    MeetingStatus.TRANSCRIBING, MeetingStatus.PROCESSING -> TextGray
    MeetingStatus.COMPLETED -> AccentGreen
    MeetingStatus.ERROR -> ErrorRed
}

private fun statusIcon(s: MeetingStatus): ImageVector = when (s) {
    MeetingStatus.RECORDING -> Icons.Default.RadioButtonChecked
    MeetingStatus.RECORDED -> Icons.Default.Mic
    MeetingStatus.TRANSCRIBING -> Icons.Default.QueryBuilder
    MeetingStatus.PROCESSING -> Icons.Default.Autorenew
    MeetingStatus.COMPLETED -> Icons.Default.Mic
    MeetingStatus.ERROR -> Icons.Default.ErrorOutline
}

private fun statusLabel(s: MeetingStatus) = when (s) {
    MeetingStatus.RECORDING -> "Recording"
    MeetingStatus.RECORDED -> "Ready to Process"
    MeetingStatus.TRANSCRIBING -> "Transcribing"
    MeetingStatus.PROCESSING -> "Processing AI"
    MeetingStatus.COMPLETED -> "Completed"
    MeetingStatus.ERROR -> "Error"
}

private fun formatDuration(ms: Long): String {
    val s = ms / 1000; val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) String.format("%02d:%02d:%02d", h, m, sec) else String.format("%02d:%02d", m, sec)
}
