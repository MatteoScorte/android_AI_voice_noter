package com.transcriber.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.transcriber.app.data.ActionItem
import com.transcriber.app.data.MeetingStatus
import com.transcriber.app.data.OutlineItem
import com.transcriber.app.data.PromptCategoryEntity
import com.transcriber.app.ui.theme.*
import com.transcriber.app.util.Phrase
import com.transcriber.app.util.parseTimestampToMs
import com.transcriber.app.viewmodel.TranscriptUiState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun TranscriptScreen(
    uiState: TranscriptUiState,
    onBack: () -> Unit,
    onStartProcessing: (PromptCategoryEntity) -> Unit,
    onRenameTitle: (String) -> Unit,
    onSetEditingTitle: (Boolean) -> Unit,
    onRenameSpeaker: (original: String, newName: String) -> Unit,
    onPlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onShareToCloud: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val mainListState = rememberLazyListState()
    var editableTitleText by remember(uiState.title) { mutableStateOf(uiState.title) }
    val focusRequester = remember { FocusRequester() }
    var selectedKeyword by remember { mutableStateOf<String?>(null) }
    var showFullTranscript by rememberSaveable { mutableStateOf(false) }
    var showRawTranscript by rememberSaveable { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isEditingTitle) {
        if (uiState.isEditingTitle) {
            delay(50)
            try { focusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (uiState.isEditingTitle) {
                        OutlinedTextField(
                            value = editableTitleText,
                            onValueChange = { editableTitleText = it },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite,
                                focusedBorderColor = AccentGreen,
                                unfocusedBorderColor = DarkSurfaceVariant,
                                cursorColor = AccentGreen
                            ),
                            textStyle = MaterialTheme.typography.titleMedium.copy(color = TextWhite)
                        )
                    } else {
                        Column {
                            Text(
                                uiState.title.uppercase(), color = TextWhite, maxLines = 1,
                                fontWeight = FontWeight.SemiBold, fontSize = 16.sp, letterSpacing = 1.sp
                            )
                            if (uiState.durationMs > 0) {
                                Text(
                                    "DURATION: ${formatDuration(uiState.durationMs)}",
                                    color = AccentGreen, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextWhite)
                    }
                },
                actions = {
                    if (uiState.isEditingTitle) {
                        IconButton(onClick = { onRenameTitle(editableTitleText) }) {
                            Icon(Icons.Default.Check, "Save name", tint = AccentGreen)
                        }
                    } else {
                        IconButton(onClick = { onSetEditingTitle(true) }) {
                            Icon(Icons.Default.Edit, "Edit name", tint = TextGray)
                        }
                        if (uiState.rawTranscript.isNotBlank() && !uiState.isProcessing) {
                            IconButton(onClick = { showCategoryPicker = true }) {
                                Icon(Icons.Default.AutoAwesome, "Rigenera analisi", tint = AccentGreen)
                            }
                        }
                        if (uiState.finalTranscript.isNotBlank()) {
                            IconButton(onClick = { copyToClipboard(context, uiState.finalTranscript, "Transcript") }) {
                                Icon(Icons.Default.ContentCopy, "Copy transcript", tint = TextWhite)
                            }
                            IconButton(onClick = { shareText(context, uiState.finalTranscript) }) {
                                Icon(Icons.Default.Share, "Share", tint = TextWhite)
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        val emptyTranscript = uiState.finalTranscript.isBlank() && uiState.rawTranscript.isBlank()

        if (emptyTranscript) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                if (uiState.isProcessing) {
                    // ── Obiettivo 2: Animated processing screen ──────────────
                    ProcessingAnimation(step = uiState.processingStep)
                } else {
                    // ── Pre-transcription view ────────────────────────────────
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        // ── Obiettivo 1: Mini-player anteprima ────────────────
                        if (uiState.audioFilePath.isNotEmpty()) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(bottom = 10.dp)
                                ) {
                                    Icon(Icons.Default.GraphicEq, null, tint = AccentGreen, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "ANTEPRIMA AUDIO",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = AccentGreen,
                                        letterSpacing = 1.5.sp
                                    )
                                }
                                AudioPlayerCard(
                                    currentMs    = uiState.playerCurrentMs,
                                    durationMs   = uiState.playerDurationMs,
                                    isPlaying    = uiState.isPlayerPlaying,
                                    isReady      = uiState.isPlayerReady,
                                    onPlayPause  = onPlayPause,
                                    onSeekTo     = onSeekTo
                                )
                            }
                        }

                        // ── Error banner ──────────────────────────────────────
                        if (uiState.status == MeetingStatus.ERROR && uiState.errorMessage.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, ErrorRed.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                    .background(ErrorRed.copy(alpha = 0.06f))
                                    .padding(12.dp)
                            ) {
                                Icon(Icons.Default.ErrorOutline, null, tint = ErrorRed, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    uiState.errorMessage,
                                    color = ErrorRed,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // ── Start processing button ───────────────────────────
                        if (uiState.status == MeetingStatus.RECORDED || uiState.status == MeetingStatus.ERROR) {
                            OutlinedButton(
                                onClick = { showCategoryPicker = true },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentGreen),
                                border = BorderStroke(1.dp, AccentGreen.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().height(52.dp)
                            ) {
                                Icon(Icons.Default.AutoAwesome, null, Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("START AI PROCESSING", letterSpacing = 1.5.sp, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        } else {
            val hasStructuredData = uiState.keywords.isNotEmpty() || uiState.overview.isNotBlank() ||
                    uiState.outline.isNotEmpty() || uiState.bulletNotes.isNotEmpty() || uiState.actionItems.isNotEmpty()

            // Recomputed on every playerCurrentMs tick (80ms). Because uiState is a plain
            // data-class parameter — NOT a Compose State<> — derivedStateOf cannot track it;
            // using remember(key) instead gives us the correct reactive behaviour.
            val activePhraseIndex = remember(uiState.playerCurrentMs, uiState.phrases) {
                val currentMs = uiState.playerCurrentMs
                val phrases   = uiState.phrases
                if (currentMs <= 0L || phrases.isEmpty()) -1
                else {
                    val sec    = currentMs / 1000.0
                    val active = phrases.indexOfFirst { sec >= it.startTime && sec < it.endTime }
                    if (active >= 0) active else phrases.indexOfLast { it.endTime <= sec }
                }
            }

            LazyColumn(
                state    = mainListState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp)
            ) {
                // ── AUDIO PLAYER (sticky) ──
                if (uiState.audioFilePath.isNotEmpty()) {
                    stickyHeader {
                        Column(modifier = Modifier.background(DarkBackground)) {
                            Spacer(Modifier.height(8.dp))
                            AudioPlayerCard(
                                currentMs = uiState.playerCurrentMs,
                                durationMs = uiState.playerDurationMs,
                                isPlaying = uiState.isPlayerPlaying,
                                isReady = uiState.isPlayerReady,
                                onPlayPause = onPlayPause,
                                onSeekTo = onSeekTo
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }

                item {
                    StatusBanner(uiState.status, uiState.processingStep, uiState.errorMessage)
                    if (uiState.categoryName.isNotBlank()) {
                        CategoryBadge(
                            emoji = uiState.categoryEmoji,
                            name = uiState.categoryName,
                            colorHex = uiState.categoryColorHex
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }

                // ── TRASCRIZIONE LIVE (Spotify-style) ──
                if (uiState.phrases.isNotEmpty()) {
                    item {
                        SectionHeader("TRASCRIZIONE LIVE", Icons.Default.GraphicEq)
                        Spacer(Modifier.height(12.dp))
                        SpotifyLyricsSection(
                            phrases           = uiState.phrases,
                            activePhraseIndex = activePhraseIndex,
                            hasStarted        = uiState.playerCurrentMs > 0L || uiState.isPlayerPlaying,
                            onSeekTo          = onSeekTo
                        )
                        Spacer(Modifier.height(28.dp))
                    }
                }

                // ── KEYWORDS ──
                if (uiState.keywords.isNotEmpty()) {
                    item {
                        SectionHeader("KEYWORDS", Icons.Default.Label)
                        Spacer(Modifier.height(12.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            uiState.keywords.forEach { keyword ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .border(1.dp, AccentGreen.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                        .background(AccentGreen.copy(alpha = 0.08f))
                                        .clickable { selectedKeyword = keyword }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        "#${keyword.replace("_", " ")}",
                                        color = AccentGreen,
                                        fontSize = 12.sp,
                                        letterSpacing = 0.5.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(28.dp))
                    }
                }

                // ── OVERVIEW ──
                if (uiState.overview.isNotBlank()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            SectionHeader("PANORAMICA", Icons.Default.Info)
                            CopyButton(onCopy = { copyToClipboard(context, uiState.overview, "Panoramica") })
                        }
                        Spacer(Modifier.height(12.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                            border = BorderStroke(1.dp, DarkSurfaceVariant),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                uiState.overview,
                                color = TextWhite,
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 24.sp)
                            )
                        }
                        Spacer(Modifier.height(28.dp))
                    }
                }

                // ── OUTLINE / SCALETTA ──
                if (uiState.outline.isNotEmpty()) {
                    item {
                        SectionHeader("SCALETTA", Icons.Default.Schedule)
                        Spacer(Modifier.height(12.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                            border = BorderStroke(1.dp, DarkSurfaceVariant),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                uiState.outline.forEachIndexed { index, item ->
                                    val isPlayerAvailable = uiState.isPlayerReady
                                    // Index of the "TRASCRIZIONE LIVE" item in the main LazyColumn:
                                    //   0 = stickyHeader (AudioPlayer)
                                    //   1 = StatusBanner
                                    //   2 = SpotifyLyricsSection  ← scroll target
                                    val liveIndex = 2
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .then(
                                                if (isPlayerAvailable) Modifier.clickable {
                                                    onSeekTo(parseTimestampToMs(item.timestamp))
                                                    coroutineScope.launch {
                                                        mainListState.animateScrollToItem(liveIndex)
                                                    }
                                                } else Modifier
                                            )
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            item.timestamp,
                                            color = AccentGreen,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            modifier = Modifier.width(52.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            item.title,
                                            color = TextWhite,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (isPlayerAvailable) {
                                            Icon(
                                                Icons.Default.PlayCircleOutline,
                                                contentDescription = "Vai al punto",
                                                tint = AccentGreen.copy(alpha = 0.5f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    if (index < uiState.outline.lastIndex) {
                                        HorizontalDivider(
                                            color = DarkSurfaceVariant,
                                            thickness = 0.5.dp,
                                            modifier = Modifier.padding(horizontal = 16.dp)
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(28.dp))
                    }
                }

                // ── BULLET NOTES ──
                if (uiState.bulletNotes.isNotEmpty()) {
                    item {
                        SectionHeader("NOTE DETTAGLIATE", Icons.Default.Notes)
                        Spacer(Modifier.height(12.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                            border = BorderStroke(1.dp, DarkSurfaceVariant),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                uiState.bulletNotes.forEach { note ->
                                    Row(verticalAlignment = Alignment.Top) {
                                        Text(
                                            "•",
                                            color = AccentGreen,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            modifier = Modifier.padding(top = 1.dp, end = 10.dp)
                                        )
                                        Text(
                                            note,
                                            color = TextWhite,
                                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(28.dp))
                    }
                }

                // ── ACTION ITEMS ──
                if (uiState.actionItems.isNotEmpty()) {
                    item {
                        SectionHeader("ACTION ITEMS", Icons.Default.CheckCircleOutline)
                        Spacer(Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            uiState.actionItems.forEach { action ->
                                ActionItemCard(action)
                            }
                        }
                        Spacer(Modifier.height(28.dp))
                    }
                }

                // ── SPEAKERS RENAME ──
                if (uiState.speakers.isNotEmpty() && uiState.finalTranscript.isNotBlank()) {
                    item {
                        SpeakerRenameCard(speakers = uiState.speakers, onRename = onRenameSpeaker)
                        Spacer(Modifier.height(28.dp))
                    }
                }

                // ── TRASCRIZIONE FORMATTATA (collapsible when structured data is present) ──
                if (uiState.finalTranscript.isNotBlank()) {
                    item {
                        CollapsibleSection(
                            title = if (hasStructuredData) "TRASCRIZIONE COMPLETA" else "FINAL SUMMARY",
                            icon = Icons.Default.Description,
                            expanded = showFullTranscript || !hasStructuredData,
                            onToggle = { showFullTranscript = !showFullTranscript },
                            showToggle = hasStructuredData,
                            headerTrailing = {
                                CopyButton(onCopy = { copyToClipboard(context, uiState.finalTranscript, "Trascrizione") })
                            }
                        ) {
                            SimpleMarkdownBlock(
                                text = uiState.finalTranscript,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Spacer(Modifier.height(28.dp))
                    }
                }

                // ── RAW TRANSCRIPT (collapsible) ──
                if (uiState.rawTranscript.isNotBlank()) {
                    item {
                        CollapsibleSection(
                            title = "RAW TRANSCRIPT",
                            icon = Icons.Default.ReceiptLong,
                            expanded = showRawTranscript,
                            onToggle = { showRawTranscript = !showRawTranscript },
                            showToggle = true,
                            headerTrailing = {
                                IconButton(
                                    onClick = { copyToClipboard(context, uiState.rawTranscript, "Transcription") },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, "Copy", tint = AccentGreen, modifier = Modifier.size(18.dp))
                                }
                            }
                        ) {
                            Column {
                                uiState.rawTranscript.split("\n\n").forEach { paragraph ->
                                    if (paragraph.isNotBlank()) {
                                        Text(
                                            paragraph,
                                            color = TextGray,
                                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp)
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(28.dp))
                    }
                }

                // ── CLOUD SYNC ──
                if (uiState.status == MeetingStatus.COMPLETED) {
                    item {
                        CloudSyncSection(
                            isShared  = uiState.isShared,
                            isSharing = uiState.isSharing,
                            onShare   = onShareToCloud
                        )
                        Spacer(Modifier.height(48.dp))
                    }
                }
            }
        }
    }

    // ── KEYWORD DETAIL DIALOG ──
    selectedKeyword?.let { keyword ->
        KeywordDialog(
            keyword = keyword,
            rawTranscript = uiState.rawTranscript,
            onDismiss = { selectedKeyword = null }
        )
    }

    // ── CATEGORY PICKER BOTTOM SHEET ──
    if (showCategoryPicker) {
        ModalBottomSheet(
            onDismissRequest = { showCategoryPicker = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = DarkSurface
        ) {
            CategoryPickerSheet(
                categories = uiState.categories,
                onSelect = { category ->
                    showCategoryPicker = false
                    onStartProcessing(category)
                }
            )
        }
    }
}

// ── Category Picker Sheet ─────────────────────────────────────────────────────

@Composable
private fun CategoryPickerSheet(
    categories: List<PromptCategoryEntity>,
    onSelect: (PromptCategoryEntity) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.AutoAwesome, null, tint = AccentGreen, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "SCEGLI CATEGORIA",
                style = MaterialTheme.typography.labelMedium,
                color = AccentGreen,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
        }
        HorizontalDivider(color = DarkSurfaceVariant)
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories, key = { it.id }) { category ->
                val accent = parseHexColor(category.colorHex)
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(category) },
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    border = BorderStroke(1.dp, DarkSurfaceVariant),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(accent.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(category.emoji.ifBlank { "📝" }, fontSize = 18.sp)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                category.name,
                                color = TextWhite,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                maxLines = 1
                            )
                            Text(
                                category.systemPrompt.replace("\n", " "),
                                color = TextGray,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Category Badge ────────────────────────────────────────────────────────────

@Composable
private fun CategoryBadge(emoji: String, name: String, colorHex: String) {
    val accent = parseHexColor(colorHex)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
            .background(accent.copy(alpha = 0.06f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji.ifBlank { "📝" }, fontSize = 14.sp)
        Spacer(Modifier.width(8.dp))
        Text(
            name.uppercase(),
            color = accent,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp
        )
    }
}

// ── Spotify-style Lyrics Section ──────────────────────────────────────────────

/**
 * Renders [phrases] as a Spotify-style lyrics panel.
 *
 * Performance contract:
 *  - [activePhraseIndex] is computed via [derivedStateOf] in the parent and changes
 *    only every few seconds — NOT every 80ms player tick.
 *  - The inner LazyColumn therefore recomposes only when the active line changes.
 *  - Each item uses `key = { phrase.startTime }` so Compose reuses existing compositions
 *    during scroll instead of recreating them.
 */
@Composable
private fun SpotifyLyricsSection(
    phrases: List<Phrase>,
    activePhraseIndex: Int,
    hasStarted: Boolean,
    onSeekTo: (Long) -> Unit
) {
    val listState = rememberLazyListState()

    // Smoothly scroll to keep the active phrase visible with one phrase of context above.
    // Scrolls to (activePhraseIndex - 1) so the preceding line stays visible.
    // Skipped when activePhraseIndex is -1 (idle) or out of the current list bounds.
    LaunchedEffect(activePhraseIndex) {
        val targetIndex = (activePhraseIndex - 1).coerceAtLeast(0)
        if (activePhraseIndex >= 0 && activePhraseIndex < phrases.size) {
            listState.animateScrollToItem(index = targetIndex)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, DarkSurfaceVariant, RoundedCornerShape(8.dp))
    ) {
        if (!hasStarted) {
            // ── Idle state ──
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Premi play per avviare la trascrizione live",
                    color = TextGray.copy(alpha = 0.55f),
                    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
        } else {
            // ── Lyrics list ──
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                // Top/bottom padding creates breathing room so the first/last phrase
                // can be scrolled to a comfortable reading position.
                contentPadding = PaddingValues(start = 20.dp, top = 72.dp, end = 20.dp, bottom = 160.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                itemsIndexed(
                    items = phrases,
                    key   = { _, phrase -> phrase.startTime }
                ) { index, phrase ->
                    PhraseRow(
                        phrase            = phrase,
                        isActive          = index == activePhraseIndex,
                        isPast            = activePhraseIndex >= 0 && index < activePhraseIndex,
                        onSeekTo          = onSeekTo
                    )
                }
            }
        }
    }
}

@Composable
private fun PhraseRow(
    phrase: Phrase,
    isActive: Boolean,
    isPast: Boolean,
    onSeekTo: (Long) -> Unit
) {
    val textColor = when {
        isActive -> Color.White                   // full brightness — unmistakable
        isPast   -> Color.White.copy(alpha = 0.30f)
        else     -> Color.White.copy(alpha = 0.45f)
    }
    val fontSize   = if (isActive) 19.sp else 16.sp
    val fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
    val lineHeight = if (isActive) 29.sp else 24.sp

    Text(
        text       = phrase.text,
        color      = textColor,
        fontSize   = fontSize,
        fontWeight = fontWeight,
        lineHeight = lineHeight,
        modifier   = Modifier
            .fillMaxWidth()
            .clickable { onSeekTo((phrase.startTime * 1_000.0).toLong()) }
            .padding(vertical = 2.dp)
    )
}

// ── Markdown renderer ─────────────────────────────────────────────────────────

/**
 * Renders a subset of Markdown as native Compose:
 *   # / ## / ###  → heading sizes
 *   - / *          → bullet list
 *   **text**       → bold   (inline)
 *   *text*         → italic (inline)
 *   blank lines    → vertical spacing
 */
@Composable
private fun SimpleMarkdownBlock(text: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        text.split("\n").forEach { line ->
            when {
                line.startsWith("# ") -> Text(
                    text     = parseInlineMarkdown(line.removePrefix("# ")),
                    color    = TextWhite,
                    style    = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)
                )
                line.startsWith("## ") -> Text(
                    text     = parseInlineMarkdown(line.removePrefix("## ")),
                    color    = TextWhite,
                    style    = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                )
                line.startsWith("### ") -> Text(
                    text     = parseInlineMarkdown(line.removePrefix("### ")),
                    color    = TextWhite,
                    style    = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                )
                line.startsWith("- ") || line.startsWith("* ") -> Row(
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    Text("•  ", color = TextWhite, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text  = parseInlineMarkdown(line.drop(2)),
                        color = TextWhite,
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp)
                    )
                }
                line.isBlank() -> Spacer(Modifier.height(6.dp))
                else -> Text(
                    text     = parseInlineMarkdown(line),
                    color    = TextWhite,
                    style    = MaterialTheme.typography.bodyMedium.copy(lineHeight = 24.sp),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }
    }
}

/** Parses **bold** and *italic* markers within a single line into an [AnnotatedString]. */
private fun parseInlineMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            // **bold**
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(i + 2, end)) }
                    i = end + 2
                } else { append(text[i]); i++ }
            }
            // *italic* (only when not part of **)
            text[i] == '*' -> {
                val end = text.indexOf('*', i + 1)
                if (end != -1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(text.substring(i + 1, end)) }
                    i = end + 1
                } else { append(text[i]); i++ }
            }
            else -> { append(text[i]); i++ }
        }
    }
}

// ── Audio Player Card ─────────────────────────────────────────────────────────

@Composable
private fun AudioPlayerCard(
    currentMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    isReady: Boolean,
    onPlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkSurfaceVariant),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPlayPause, enabled = isReady) {
                if (!isReady) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = AccentGreen,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pausa" else "Play",
                        tint = AccentGreen,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Slider(
                    value = if (durationMs > 0L) currentMs.toFloat() / durationMs.toFloat() else 0f,
                    onValueChange = { fraction -> onSeekTo((fraction * durationMs).toLong()) },
                    enabled = isReady,
                    colors = SliderDefaults.colors(
                        thumbColor = AccentGreen,
                        activeTrackColor = AccentGreen,
                        inactiveTrackColor = DarkSurfaceVariant,
                        disabledThumbColor = TextGray,
                        disabledActiveTrackColor = TextGray
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatDuration(currentMs), color = TextGray, style = MaterialTheme.typography.labelSmall)
                    Text(formatDuration(durationMs), color = TextGray, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

// ── Copy Button with "Copiato!" feedback ──────────────────────────────────────

@Composable
private fun CopyButton(onCopy: () -> Unit) {
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }
    if (copied) {
        Text(
            "Copiato!",
            color = AccentGreen,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(end = 4.dp)
        )
    } else {
        IconButton(onClick = { onCopy(); copied = true }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.ContentCopy, "Copia", tint = TextGray, modifier = Modifier.size(18.dp))
        }
    }
}

// ── Collapsible Section ───────────────────────────────────────────────────────

@Composable
private fun CollapsibleSection(
    title: String,
    icon: ImageVector,
    expanded: Boolean,
    onToggle: () -> Unit,
    showToggle: Boolean,
    headerTrailing: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SectionHeader(title, icon)
            Row(verticalAlignment = Alignment.CenterVertically) {
                headerTrailing?.invoke()
                if (showToggle) {
                    IconButton(onClick = onToggle, modifier = Modifier.size(32.dp)) {
                        Icon(
                            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            null,
                            tint = TextGray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
        if (expanded) {
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

// ── Action Item Card ──────────────────────────────────────────────────────────

@Composable
private fun ActionItemCard(action: ActionItem) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, DarkSurfaceVariant),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Default.CheckBoxOutlineBlank, null, tint = AccentGreen,
                modifier = Modifier.size(20.dp).padding(top = 2.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(action.task, color = TextWhite, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                if (action.assignee.isNotBlank() || action.deadline.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (action.assignee.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Person, null, tint = TextGray, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(action.assignee, color = TextGray, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        if (action.deadline.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Schedule, null, tint = TextGray, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(action.deadline, color = TextGray, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Keyword Detail Dialog ─────────────────────────────────────────────────────

@Composable
private fun KeywordDialog(keyword: String, rawTranscript: String, onDismiss: () -> Unit) {
    val cleanKeyword = keyword.replace("_", " ")
    val snippets = remember(keyword, rawTranscript) {
        rawTranscript.split("\n")
            .filter { it.contains(cleanKeyword, ignoreCase = true) || it.contains(keyword, ignoreCase = true) }
            .filter { it.isNotBlank() }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.dp, DarkSurfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "#$cleanKeyword", color = AccentGreen, fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp, style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, "Chiudi", tint = TextGray, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (snippets.isEmpty()) "Nessuna occorrenza nella trascrizione."
                    else "${snippets.size} occorrenza${if (snippets.size == 1) "" else "e"} trovata${if (snippets.size == 1) "" else "e"}",
                    color = TextGray, style = MaterialTheme.typography.labelSmall
                )
                if (snippets.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        snippets.forEach { snippet ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = AccentGreen.copy(alpha = 0.05f)),
                                border = BorderStroke(0.5.dp, AccentGreen.copy(alpha = 0.2f)),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    snippet.trim(), color = TextWhite.copy(alpha = 0.9f),
                                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Speaker Rename Card ───────────────────────────────────────────────────────

@Composable
private fun SpeakerRenameCard(
    speakers: LinkedHashMap<String, String>,
    onRename: (original: String, newName: String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader("SPEAKERS", Icons.Default.Group)
        Spacer(Modifier.height(12.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            border = BorderStroke(1.dp, DarkSurfaceVariant),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "Rinomina i partecipanti. I nomi vengono aggiornati ovunque nella trascrizione.",
                    color = TextGray, style = MaterialTheme.typography.bodySmall, letterSpacing = 0.5.sp
                )
                speakers.keys.forEach { originalLabel ->
                    val displayedName = speakers[originalLabel] ?: originalLabel
                    var editText by remember(displayedName) { mutableStateOf(displayedName) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(Icons.Default.PersonOutline, null, tint = AccentGreen, modifier = Modifier.size(24.dp))
                        OutlinedTextField(
                            value = editText, onValueChange = { editText = it }, singleLine = true,
                            label = { Text(originalLabel, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextWhite, unfocusedTextColor = TextWhite,
                                focusedBorderColor = AccentGreen, unfocusedBorderColor = DarkSurfaceVariant,
                                cursorColor = AccentGreen, focusedLabelColor = AccentGreen, unfocusedLabelColor = TextGray
                            ),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextWhite),
                            shape = RoundedCornerShape(8.dp),
                            keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = { if (editText.isNotBlank() && editText != displayedName) onRename(originalLabel, editText) }
                            )
                        )
                        IconButton(
                            onClick = { if (editText.isNotBlank() && editText != displayedName) onRename(originalLabel, editText) },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Default.Check, "Confirm", tint = AccentGreen, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
}

// ── Processing Animation ──────────────────────────────────────────────────────

/**
 * Full-screen centred loading view shown while Deepgram / LLM are running.
 * Uses a 6-bar equaliser animated with per-bar phase stagger via [StartOffset].
 */
@Composable
private fun ProcessingAnimation(step: String) {
    val easing = CubicBezierEasing(0.37f, 0f, 0.63f, 1f)   // sine ease-in-out
    val transition = rememberInfiniteTransition(label = "wave")
    val barCount = 6
    val bars = List(barCount) { i ->
        transition.animateFloat(
            initialValue = 0.1f,
            targetValue  = 1f,
            animationSpec = infiniteRepeatable(
                animation           = tween(durationMillis = 520, easing = easing),
                repeatMode          = RepeatMode.Reverse,
                // FastForward places each bar at a different point in the cycle,
                // creating a true staggered wave that persists across every repeat.
                initialStartOffset  = StartOffset(i * 75, StartOffsetType.FastForward)
            ),
            label = "bar$i"
        )
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        // Equaliser bars
        Row(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment     = Alignment.Bottom,
            modifier              = Modifier.height(60.dp)
        ) {
            bars.forEach { anim ->
                val f by anim
                Box(
                    modifier = Modifier
                        .width(7.dp)
                        .height((8 + f * 52).dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(AccentGreen.copy(alpha = 0.4f + f * 0.6f))
                )
            }
        }

        Text(
            text       = "Elaborazione audio in corso...",
            color      = TextWhite,
            fontWeight = FontWeight.SemiBold,
            fontSize   = 17.sp,
            letterSpacing = 0.5.sp
        )

        if (step.isNotBlank()) {
            Text(
                text     = step,
                color    = TextGray.copy(alpha = 0.75f),
                style    = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier  = Modifier.padding(horizontal = 24.dp)
            )
        }
    }
}

// ── Cloud Sync Section ────────────────────────────────────────────────────────

@Composable
private fun CloudSyncSection(
    isShared: Boolean,
    isSharing: Boolean,
    onShare: () -> Unit
) {
    when {
        isShared -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, AccentTeal.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                    .background(AccentTeal.copy(alpha = 0.06f))
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.CloudDone, null, tint = AccentTeal, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "CONDIVISO",
                    color = AccentTeal,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    letterSpacing = 1.5.sp
                )
            }
        }
        isSharing -> {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = AccentGreen, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Condivisione in corso...", color = TextGray, style = MaterialTheme.typography.bodySmall)
            }
        }
        else -> {
            OutlinedButton(
                onClick = onShare,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentTeal),
                border = BorderStroke(1.dp, AccentTeal.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.Default.CloudUpload, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("CONDIVIDI SU CLOUD", letterSpacing = 1.5.sp, fontSize = 12.sp)
            }
        }
    }
}

// ── Status Banner ─────────────────────────────────────────────────────────────

@Composable
private fun StatusBanner(status: MeetingStatus, step: String, error: String) {
    val (color, text, icon) = when (status) {
        MeetingStatus.RECORDED    -> Triple(TextWhite, "READY FOR TRANSCRIPTION", Icons.Default.Mic)
        MeetingStatus.TRANSCRIBING -> Triple(AccentGreen, step.ifBlank { "TRANSCRIBING..." }, Icons.Default.QueryBuilder)
        MeetingStatus.PROCESSING  -> Triple(AccentGreen, step.ifBlank { "PROCESSING..." }, Icons.Default.Autorenew)
        MeetingStatus.COMPLETED   -> Triple(AccentGreen, "COMPLETED", Icons.Default.CheckCircleOutline)
        MeetingStatus.ERROR       -> Triple(ErrorRed, error.ifBlank { "ERROR OCCURRED" }, Icons.Default.ErrorOutline)
        else                      -> Triple(TextGray, "", Icons.Default.Info)
    }
    if (text.isNotBlank()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(text, color = color, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, fontSize = 13.sp)
        }
    }
}

// ── Section Header ────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = AccentGreen, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = AccentGreen,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp
        )
    }
}

// ── Utilities ─────────────────────────────────────────────────────────────────

private fun copyToClipboard(context: Context, text: String, label: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "$label copiato", Toast.LENGTH_SHORT).show()
}

private fun shareText(context: Context, text: String) {
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) },
            "Condividi"
        )
    )
}

private fun formatDuration(ms: Long): String {
    val s = ms / 1000; val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) String.format("%02d:%02d:%02d", h, m, sec) else String.format("%02d:%02d", m, sec)
}
