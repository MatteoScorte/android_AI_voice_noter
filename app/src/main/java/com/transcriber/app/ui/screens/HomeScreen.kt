package com.transcriber.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import android.net.Uri
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import kotlinx.coroutines.launch
import com.transcriber.app.data.FOLDER_ID_NONE
import com.transcriber.app.data.FOLDER_PRESET_COLORS
import com.transcriber.app.data.InboxItem
import com.transcriber.app.data.Meeting
import com.transcriber.app.data.MeetingFolder
import com.transcriber.app.data.MeetingStatus
import com.transcriber.app.ui.theme.*
import com.transcriber.app.viewmodel.HomeUiState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onStartRecording: (title: String, language: String, folderId: String?) -> Unit,
    onPauseRecording: () -> Unit,
    onResumeRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onMeetingClick: (String) -> Unit,
    onDeleteMeeting: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onCategoriesClick: () -> Unit,
    onImportFile: (Uri) -> Unit,
    onProcessInboxItem: (InboxItem, String, String) -> Unit,
    onDeleteInboxItem: (String) -> Unit,
    onSelectFolder: (String?) -> Unit,
    onCreateFolder: (name: String, colorHex: String) -> Unit,
    onUpdateFolder: (id: String, newName: String, colorHex: String) -> Unit,
    onDeleteFolder: (id: String) -> Unit,
    onAssignFolder: (meetingId: String, folderId: String?) -> Unit
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    var showInbox by rememberSaveable { mutableStateOf(false) }
    var meetingToDelete by rememberSaveable { mutableStateOf<String?>(null) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = 1, pageCount = { 3 })

    // Right-side drawer via RTL layout trick
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    ModalDrawerSheet(
                        modifier = Modifier.width(260.dp),
                        drawerContainerColor = DarkSurface,
                        drawerContentColor = TextWhite
                    ) {
                        Spacer(Modifier.height(32.dp))
                        Text(
                            "VOXLOG",
                            modifier = Modifier.padding(horizontal = 24.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = AccentGreen,
                            letterSpacing = 3.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(color = DarkSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                        Spacer(Modifier.height(8.dp))
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.Settings, null) },
                            label = { Text("Impostazioni", fontWeight = FontWeight.Medium) },
                            selected = false,
                            onClick = {
                                scope.launch { drawerState.close() }
                                onSettingsClick()
                            },
                            colors = NavigationDrawerItemDefaults.colors(
                                unselectedContainerColor = Color.Transparent,
                                unselectedTextColor = TextWhite,
                                unselectedIconColor = TextGray
                            )
                        )
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.AutoAwesome, null) },
                            label = { Text("Categorie Prompt AI", fontWeight = FontWeight.Medium) },
                            selected = false,
                            onClick = {
                                scope.launch { drawerState.close() }
                                onCategoriesClick()
                            },
                            colors = NavigationDrawerItemDefaults.colors(
                                unselectedContainerColor = Color.Transparent,
                                unselectedTextColor = TextWhite,
                                unselectedIconColor = TextGray
                            )
                        )
                        NavigationDrawerItem(
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
                                    Icon(Icons.Default.MoveToInbox, null)
                                }
                            },
                            label = { Text("Inbox", fontWeight = FontWeight.Medium) },
                            selected = showInbox,
                            onClick = {
                                scope.launch { drawerState.close() }
                                showInbox = true
                            },
                            colors = NavigationDrawerItemDefaults.colors(
                                unselectedContainerColor = Color.Transparent,
                                unselectedTextColor = TextWhite,
                                unselectedIconColor = TextGray,
                                selectedContainerColor = AccentGreen.copy(alpha = 0.1f),
                                selectedTextColor = AccentGreen,
                                selectedIconColor = AccentGreen
                            )
                        )
                    }
                }
            }
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (showInbox) "INBOX" else "Voxlog",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 20.sp,
                        color = TextWhite,
                        letterSpacing = if (showInbox) 2.sp else 0.sp
                    )
                },
                navigationIcon = {
                    if (showInbox) {
                        IconButton(onClick = { showInbox = false }) {
                            Icon(Icons.Default.ArrowBack, "Indietro", tint = TextWhite)
                        }
                    }
                },
                actions = {
                    if (!showInbox) {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, "Menu", tint = TextWhite)
                        }
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
                    icon = { Icon(Icons.Default.Chat, contentDescription = "Chat") },
                    label = { Text("CHAT", letterSpacing = 1.sp) },
                    selected = !showInbox && pagerState.currentPage == 0,
                    onClick = {
                        showInbox = false
                        scope.launch { pagerState.animateScrollToPage(0) }
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = DarkBackground,
                        selectedTextColor = AccentGreen,
                        indicatorColor = AccentGreen,
                        unselectedIconColor = TextGray,
                        unselectedTextColor = TextGray
                    )
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Mic, contentDescription = "Record") },
                    label = { Text("REC", letterSpacing = 1.sp) },
                    selected = !showInbox && pagerState.currentPage == 1,
                    onClick = {
                        showInbox = false
                        scope.launch { pagerState.animateScrollToPage(1) }
                    },
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
                    selected = !showInbox && pagerState.currentPage == 2,
                    onClick = {
                        showInbox = false
                        scope.launch { pagerState.animateScrollToPage(2) }
                    },
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
            if (showInbox) {
                InboxTab(
                    items = uiState.inboxItems,
                    onImportFile = onImportFile,
                    onProcess = onProcessInboxItem,
                    onDelete = onDeleteInboxItem
                )
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    when (page) {
                        0 -> ChatTab()
                        1 -> RecordTab(
                            uiState = uiState,
                            onStartClick = { showDialog = true },
                            onPause = onPauseRecording,
                            onResume = onResumeRecording,
                            onStop = onStopRecording
                        )
                        2 -> HistoryTab(
                            uiState = uiState,
                            onMeetingClick = onMeetingClick,
                            onDeleteMeeting = { meetingId -> meetingToDelete = meetingId },
                            onSelectFolder = onSelectFolder,
                            onCreateFolder = onCreateFolder,
                            onUpdateFolder = onUpdateFolder,
                            onDeleteFolder = onDeleteFolder,
                            onAssignFolder = onAssignFolder
                        )
                    }
                }
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
            folders = uiState.folders,
            onDismiss = { showDialog = false },
            onConfirm = { title, language, folderId ->
                showDialog = false
                scope.launch { pagerState.animateScrollToPage(1) }
                onStartRecording(title, language, folderId)
            }
        )
    }

            } // end CompositionLocalProvider LTR (main content)
        }     // end ModalNavigationDrawer
    }         // end CompositionLocalProvider RTL (drawer)
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
fun ChatTab() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Chat, null, tint = TextGray.copy(alpha = 0.3f), modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(16.dp))
            Text("Chat coming soon", color = TextGray.copy(alpha = 0.6f), fontWeight = FontWeight.Medium)
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryTab(
    uiState: HomeUiState,
    onMeetingClick: (String) -> Unit,
    onDeleteMeeting: (String) -> Unit,
    onSelectFolder: (String?) -> Unit,
    onCreateFolder: (name: String, colorHex: String) -> Unit,
    onUpdateFolder: (id: String, newName: String, colorHex: String) -> Unit,
    onDeleteFolder: (id: String) -> Unit,
    onAssignFolder: (meetingId: String, folderId: String?) -> Unit
) {
    var showCreateFolder by remember { mutableStateOf(false) }
    var folderToManage by remember { mutableStateOf<MeetingFolder?>(null) }
    var meetingToAssign by remember { mutableStateOf<String?>(null) }

    // Filter meetings by selected folder
    val filteredMeetings = remember(uiState.meetings, uiState.selectedFolderId) {
        when (uiState.selectedFolderId) {
            null         -> uiState.meetings
            FOLDER_ID_NONE -> uiState.meetings.filter { it.folderId == null }
            else         -> uiState.meetings.filter { it.folderId == uiState.selectedFolderId }
        }
    }
    val unassignedCount = remember(uiState.meetings) { uiState.meetings.count { it.folderId == null } }

    if (uiState.meetings.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.History, null, tint = TextGray.copy(alpha = 0.3f), modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(16.dp))
                Text("No recordings yet", color = TextGray.copy(alpha = 0.6f), fontWeight = FontWeight.Medium)
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(Modifier.height(16.dp))

            // ── Folder filter chips ──
            FolderFilterChips(
                folders = uiState.folders,
                selectedFolderId = uiState.selectedFolderId,
                totalCount = uiState.meetings.size,
                unassignedCount = unassignedCount,
                onSelectFolder = onSelectFolder,
                onCreateFolder = { showCreateFolder = true },
                onLongPressFolder = { folderToManage = it }
            )

            Spacer(Modifier.height(16.dp))

            // ── Header row ──
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("RECORDING HISTORY", style = MaterialTheme.typography.labelMedium, letterSpacing = 2.sp, color = TextGray)
                Text("${filteredMeetings.size} items", style = MaterialTheme.typography.labelMedium, color = TextGray)
            }
            Spacer(Modifier.height(12.dp))

            // ── Meeting list ──
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredMeetings) { meeting ->
                    val folder = uiState.folders.find { it.id == meeting.folderId }
                    MeetingCard(
                        meeting = meeting,
                        folder = folder,
                        onClick = { onMeetingClick(meeting.id) },
                        onDelete = { onDeleteMeeting(meeting.id) },
                        onChangeFolder = { meetingToAssign = meeting.id }
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    // ── Create folder dialog ──
    if (showCreateFolder) {
        CreateFolderDialog(
            onDismiss = { showCreateFolder = false },
            onCreate = { name, color ->
                onCreateFolder(name, color)
                showCreateFolder = false
            }
        )
    }

    // ── Manage folder dialog (rename / delete) ──
    folderToManage?.let { folder ->
        FolderManageDialog(
            folder = folder,
            onDismiss = { folderToManage = null },
            onSave = { newName, colorHex ->
                onUpdateFolder(folder.id, newName, colorHex)
                folderToManage = null
            },
            onDelete = {
                onDeleteFolder(folder.id)
                folderToManage = null
            }
        )
    }

    // ── Assign folder to meeting ──
    meetingToAssign?.let { meetingId ->
        FolderPickerDialog(
            folders = uiState.folders,
            currentFolderId = uiState.meetings.find { it.id == meetingId }?.folderId,
            onDismiss = { meetingToAssign = null },
            onPick = { folderId ->
                onAssignFolder(meetingId, folderId)
                meetingToAssign = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewRecordingDialog(
    folders: List<MeetingFolder>,
    onDismiss: () -> Unit,
    onConfirm: (title: String, language: String, folderId: String?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var language by remember { mutableStateOf("it") }
    var languageExpanded by remember { mutableStateOf(false) }
    var folderExpanded by remember { mutableStateOf(false) }
    var selectedFolderId by remember { mutableStateOf<String?>(null) }

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

                // ── Folder picker (optional, only shown if folders exist) ──
                if (folders.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    ExposedDropdownMenuBox(expanded = folderExpanded, onExpandedChange = { folderExpanded = it }) {
                        val selectedFolderDisplay = folders.find { it.id == selectedFolderId }?.name ?: "Nessuna cartella"
                        OutlinedTextField(
                            value = selectedFolderDisplay, onValueChange = {},
                            readOnly = true, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(folderExpanded) },
                            label = { Text("Cartella (opzionale)", style = MaterialTheme.typography.bodySmall, color = AccentGreen) },
                            leadingIcon = {
                                val color = folders.find { it.id == selectedFolderId }?.colorHex
                                if (color != null) {
                                    Box(Modifier.size(12.dp).background(Color(android.graphics.Color.parseColor(color)), CircleShape))
                                } else {
                                    Icon(Icons.Default.FolderOpen, null, tint = TextGray, modifier = Modifier.size(18.dp))
                                }
                            },
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
                            expanded = folderExpanded,
                            onDismissRequest = { folderExpanded = false },
                            modifier = Modifier.background(DarkCard)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Nessuna cartella", color = TextGray, style = MaterialTheme.typography.bodyMedium) },
                                onClick = { selectedFolderId = null; folderExpanded = false },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                            )
                            folders.forEach { folder ->
                                DropdownMenuItem(
                                    text = { Text(folder.name, color = TextWhite, style = MaterialTheme.typography.bodyMedium) },
                                    leadingIcon = {
                                        Box(Modifier.size(10.dp).background(Color(android.graphics.Color.parseColor(folder.colorHex)), CircleShape))
                                    },
                                    onClick = { selectedFolderId = folder.id; folderExpanded = false },
                                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))

                OutlinedButton(
                    onClick = { onConfirm(title, language, selectedFolderId) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentGreen),
                    border = BorderStroke(1.dp, AccentGreen.copy(alpha = 0.5f)),
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
private fun MeetingCard(
    meeting: Meeting,
    folder: MeetingFolder?,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onChangeFolder: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, DarkSurfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            val sColor = if (meeting.status == MeetingStatus.COMPLETED && folder != null)
                Color(android.graphics.Color.parseColor(folder.colorHex))
            else
                statusColor(meeting.status)
            Icon(
                imageVector = statusIcon(meeting.status),
                contentDescription = null,
                tint = sColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(meeting.title, color = TextWhite, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp)
                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(statusLabel(meeting.status).uppercase(), color = sColor, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
                    if (meeting.durationMs > 0) {
                        Text("•  ${formatDuration(meeting.durationMs)}", color = TextGray, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(8.dp))
                // ── Folder pill — always visible, always tappable ──
                if (folder != null) {
                    val folderColor = Color(android.graphics.Color.parseColor(folder.colorHex))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(folderColor.copy(alpha = 0.15f))
                            .border(1.dp, folderColor.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                            .clickable(onClick = onChangeFolder)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Box(Modifier.size(7.dp).background(folderColor, CircleShape))
                        Spacer(Modifier.width(6.dp))
                        Text(folder.name, color = folderColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowDropDown, null, tint = folderColor, modifier = Modifier.size(14.dp))
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .border(1.dp, TextGray.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                            .clickable(onClick = onChangeFolder)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Add, null, tint = TextGray.copy(alpha = 0.6f), modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Cartella", color = TextGray.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (meeting.isShared) {
                Icon(Icons.Default.CloudDone, "Condiviso", tint = AccentTeal, modifier = Modifier.size(18.dp))
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

// ── Folder UI components ──────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderFilterChips(
    folders: List<MeetingFolder>,
    selectedFolderId: String?,
    totalCount: Int,
    unassignedCount: Int,
    onSelectFolder: (String?) -> Unit,
    onCreateFolder: () -> Unit,
    onLongPressFolder: (MeetingFolder) -> Unit
) {
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = AccentGreen.copy(alpha = 0.2f),
        selectedLabelColor = AccentGreen,
        selectedLeadingIconColor = AccentGreen,
        labelColor = TextGray,
        containerColor = Color.Transparent
    )
    val chipBorder = FilterChipDefaults.filterChipBorder(
        enabled = true, selected = false,
        borderColor = DarkSurfaceVariant, selectedBorderColor = AccentGreen.copy(alpha = 0.5f)
    )

    LazyRow(
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // "Tutte"
        item {
            FilterChip(
                selected = selectedFolderId == null,
                onClick = { onSelectFolder(null) },
                label = { Text("Tutte ($totalCount)", fontSize = 12.sp) },
                colors = chipColors, border = chipBorder
            )
        }
        // One chip per folder
        items(folders) { folder ->
            val folderColor = Color(android.graphics.Color.parseColor(folder.colorHex))
            FilterChip(
                selected = selectedFolderId == folder.id,
                onClick = { onSelectFolder(folder.id) },
                label = {
                    Box(
                        Modifier.combinedClickable(
                            onClick = { onSelectFolder(folder.id) },
                            onLongClick = { onLongPressFolder(folder) }
                        )
                    ) {
                        Text(folder.name, fontSize = 12.sp)
                    }
                },
                leadingIcon = {
                    Box(Modifier.size(8.dp).background(folderColor, CircleShape))
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = folderColor.copy(alpha = 0.2f),
                    selectedLabelColor = folderColor,
                    selectedLeadingIconColor = folderColor,
                    labelColor = TextGray,
                    containerColor = Color.Transparent
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true, selected = selectedFolderId == folder.id,
                    borderColor = DarkSurfaceVariant, selectedBorderColor = folderColor.copy(alpha = 0.5f)
                )
            )
        }
        // "Nessuna" (only if there are unassigned meetings)
        if (unassignedCount > 0 || selectedFolderId == FOLDER_ID_NONE) {
            item {
                FilterChip(
                    selected = selectedFolderId == FOLDER_ID_NONE,
                    onClick = { onSelectFolder(FOLDER_ID_NONE) },
                    label = { Text("Nessuna ($unassignedCount)", fontSize = 12.sp) },
                    colors = chipColors, border = chipBorder
                )
            }
        }
        // "+" create folder
        item {
            AssistChip(
                onClick = onCreateFolder,
                label = { Text("Nuova", fontSize = 12.sp, color = TextGray) },
                leadingIcon = { Icon(Icons.Default.Add, null, tint = TextGray, modifier = Modifier.size(14.dp)) },
                colors = AssistChipDefaults.assistChipColors(containerColor = Color.Transparent),
                border = AssistChipDefaults.assistChipBorder(enabled = true, borderColor = DarkSurfaceVariant)
            )
        }
    }
}

@Composable
private fun CreateFolderDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, colorHex: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(FOLDER_PRESET_COLORS.first()) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.dp, DarkSurfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(24.dp)) {
                Text("NUOVA CARTELLA", letterSpacing = 1.sp, style = MaterialTheme.typography.labelLarge, color = AccentGreen, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(20.dp))
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Nome cartella") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite, unfocusedTextColor = TextWhite,
                        focusedBorderColor = AccentGreen, unfocusedBorderColor = DarkSurfaceVariant,
                        cursorColor = AccentGreen, focusedLabelColor = AccentGreen, unfocusedLabelColor = TextGray
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text("Colore", style = MaterialTheme.typography.labelSmall, color = TextGray, letterSpacing = 1.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FOLDER_PRESET_COLORS.forEach { hex ->
                        val c = Color(android.graphics.Color.parseColor(hex))
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(c)
                                .border(2.dp, if (selectedColor == hex) TextWhite else Color.Transparent, CircleShape)
                                .clickable { selectedColor = hex }
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, DarkSurfaceVariant),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextGray)
                    ) { Text("ANNULLA", fontSize = 12.sp, letterSpacing = 1.sp) }
                    OutlinedButton(
                        onClick = { if (name.isNotBlank()) onCreate(name, selectedColor) },
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, AccentGreen.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentGreen)
                    ) { Text("CREA", fontSize = 12.sp, letterSpacing = 1.sp) }
                }
            }
        }
    }
}

@Composable
private fun FolderManageDialog(
    folder: MeetingFolder,
    onDismiss: () -> Unit,
    onSave: (name: String, colorHex: String) -> Unit,
    onDelete: () -> Unit
) {
    var newName by remember { mutableStateOf(folder.name) }
    var selectedColor by remember { mutableStateOf(folder.colorHex) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.dp, DarkSurfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).background(Color(android.graphics.Color.parseColor(selectedColor)), CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text("MODIFICA CARTELLA", letterSpacing = 1.sp, style = MaterialTheme.typography.labelLarge, color = TextWhite, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(20.dp))
                if (!showDeleteConfirm) {
                    OutlinedTextField(
                        value = newName, onValueChange = { newName = it },
                        label = { Text("Nome") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextWhite, unfocusedTextColor = TextWhite,
                            focusedBorderColor = AccentGreen, unfocusedBorderColor = DarkSurfaceVariant,
                            cursorColor = AccentGreen, focusedLabelColor = AccentGreen, unfocusedLabelColor = TextGray
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Colore", style = MaterialTheme.typography.labelSmall, color = TextGray, letterSpacing = 1.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FOLDER_PRESET_COLORS.forEach { hex ->
                            val c = Color(android.graphics.Color.parseColor(hex))
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(c)
                                    .border(2.dp, if (selectedColor == hex) TextWhite else Color.Transparent, CircleShape)
                                    .clickable { selectedColor = hex }
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed)
                        ) { Text("ELIMINA", fontSize = 11.sp, letterSpacing = 1.sp) }
                        OutlinedButton(
                            onClick = { if (newName.isNotBlank()) onSave(newName, selectedColor) },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, AccentGreen.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentGreen)
                        ) { Text("SALVA", fontSize = 11.sp, letterSpacing = 1.sp) }
                    }
                } else {
                    Text(
                        "Eliminare \"${folder.name}\"? I meeting in questa cartella non verranno eliminati, ma rimarranno senza cartella.",
                        color = TextGray, style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showDeleteConfirm = false },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, DarkSurfaceVariant),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextGray)
                        ) { Text("ANNULLA", fontSize = 11.sp, letterSpacing = 1.sp) }
                        OutlinedButton(
                            onClick = onDelete,
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed)
                        ) { Text("ELIMINA", fontSize = 11.sp, letterSpacing = 1.sp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderPickerDialog(
    folders: List<MeetingFolder>,
    currentFolderId: String?,
    onDismiss: () -> Unit,
    onPick: (String?) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.dp, DarkSurfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(24.dp)) {
                Text("SPOSTA IN CARTELLA", letterSpacing = 1.sp, style = MaterialTheme.typography.labelLarge, color = TextWhite, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                // "Nessuna cartella" option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (currentFolderId == null) DarkSurfaceVariant else Color.Transparent)
                        .clickable { onPick(null) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.FolderOpen, null, tint = TextGray, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Nessuna cartella", color = if (currentFolderId == null) TextWhite else TextGray, style = MaterialTheme.typography.bodyMedium)
                    if (currentFolderId == null) {
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Default.Check, null, tint = AccentGreen, modifier = Modifier.size(16.dp))
                    }
                }
                folders.forEach { folder ->
                    val folderColor = Color(android.graphics.Color.parseColor(folder.colorHex))
                    val isSelected = folder.id == currentFolderId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) folderColor.copy(alpha = 0.15f) else Color.Transparent)
                            .clickable { onPick(folder.id) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(12.dp).background(folderColor, CircleShape))
                        Spacer(Modifier.width(12.dp))
                        Text(folder.name, color = if (isSelected) folderColor else TextGray, style = MaterialTheme.typography.bodyMedium)
                        if (isSelected) {
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.Default.Check, null, tint = folderColor, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}
