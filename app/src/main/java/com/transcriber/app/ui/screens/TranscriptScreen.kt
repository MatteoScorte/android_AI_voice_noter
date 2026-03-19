package com.transcriber.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.transcriber.app.data.ActionItem
import com.transcriber.app.data.MeetingStatus
import com.transcriber.app.data.OutlineItem
import com.transcriber.app.ui.theme.*
import com.transcriber.app.viewmodel.TranscriptUiState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TranscriptScreen(
    uiState: TranscriptUiState,
    onBack: () -> Unit,
    onStartProcessing: () -> Unit,
    onRenameTitle: (String) -> Unit,
    onSetEditingTitle: (Boolean) -> Unit,
    onRenameSpeaker: (original: String, newName: String) -> Unit
) {
    val context = LocalContext.current
    var editableTitleText by remember(uiState.title) { mutableStateOf(uiState.title) }
    val focusRequester = remember { FocusRequester() }
    var selectedKeyword by remember { mutableStateOf<String?>(null) }
    var showFullTranscript by rememberSaveable { mutableStateOf(false) }
    var showRawTranscript by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(uiState.isEditingTitle) {
        if (uiState.isEditingTitle) {
            // Wait for the TextField to be composed before requesting focus
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                StatusBanner(uiState.status, uiState.processingStep, uiState.errorMessage)
                Spacer(Modifier.height(24.dp))

                if (uiState.status == MeetingStatus.RECORDED || uiState.status == MeetingStatus.ERROR) {
                    OutlinedButton(
                        onClick = onStartProcessing,
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

                if (uiState.isProcessing) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = AccentGreen, trackColor = DarkSurfaceVariant
                    )
                }
            }
        } else {
            val hasStructuredData = uiState.keywords.isNotEmpty() || uiState.overview.isNotBlank() ||
                    uiState.outline.isNotEmpty() || uiState.bulletNotes.isNotEmpty() || uiState.actionItems.isNotEmpty()

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp)
            ) {
                item { Spacer(Modifier.height(8.dp)) }
                item { StatusBanner(uiState.status, uiState.processingStep, uiState.errorMessage) }

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
                        SectionHeader("PANORAMICA", Icons.Default.Info)
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

                // ── OUTLINE ──
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
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
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
                            showToggle = hasStructuredData
                        ) {
                            Column {
                                uiState.finalTranscript.split("\n").forEach { paragraph ->
                                    if (paragraph.isNotBlank()) {
                                        Text(
                                            paragraph,
                                            color = TextWhite,
                                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 24.sp)
                                        )
                                    }
                                }
                            }
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
}

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

@Composable
private fun ActionItemCard(action: ActionItem) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, DarkSurfaceVariant),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Default.CheckBoxOutlineBlank,
                null,
                tint = AccentGreen,
                modifier = Modifier
                    .size(20.dp)
                    .padding(top = 2.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    action.task,
                    color = TextWhite,
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.bodyMedium
                )
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
                        "#$cleanKeyword",
                        color = AccentGreen,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        style = MaterialTheme.typography.titleSmall
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
                    color = TextGray,
                    style = MaterialTheme.typography.labelSmall
                )
                if (snippets.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        snippets.forEach { snippet ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = AccentGreen.copy(alpha = 0.05f)),
                                border = BorderStroke(0.5.dp, AccentGreen.copy(alpha = 0.2f)),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    snippet.trim(),
                                    color = TextWhite.copy(alpha = 0.9f),
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
                            value = editText,
                            onValueChange = { editText = it },
                            singleLine = true,
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
                                onDone = {
                                    if (editText.isNotBlank() && editText != displayedName) {
                                        onRename(originalLabel, editText)
                                    }
                                }
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

@Composable
private fun StatusBanner(status: MeetingStatus, step: String, error: String) {
    val (color, text, icon) = when (status) {
        MeetingStatus.RECORDED -> Triple(TextWhite, "READY FOR TRANSCRIPTION", Icons.Default.Mic)
        MeetingStatus.TRANSCRIBING -> Triple(AccentGreen, step.ifBlank { "TRANSCRIBING..." }, Icons.Default.QueryBuilder)
        MeetingStatus.PROCESSING -> Triple(AccentGreen, step.ifBlank { "PROCESSING..." }, Icons.Default.Autorenew)
        MeetingStatus.COMPLETED -> Triple(AccentGreen, "COMPLETED", Icons.Default.CheckCircleOutline)
        MeetingStatus.ERROR -> Triple(ErrorRed, error.ifBlank { "ERROR OCCURRED" }, Icons.Default.ErrorOutline)
        else -> Triple(TextGray, "", Icons.Default.Info)
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
