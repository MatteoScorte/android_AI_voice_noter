package com.transcriber.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.transcriber.app.api.AVAILABLE_MODELS
import com.transcriber.app.api.LlmModel
import com.transcriber.app.data.CanvaSkillEntity
import com.transcriber.app.data.ChatMessageEntity
import com.transcriber.app.ui.components.MarkdownText
import com.transcriber.app.ui.theme.*
import com.transcriber.app.viewmodel.ChatConversationUiState
import com.transcriber.app.viewmodel.ChatExportStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    uiState: ChatConversationUiState,
    onBack: () -> Unit,
    onSendMessage: (String) -> Unit,
    onClearError: () -> Unit,
    onExportSkill: (CanvaSkillEntity, String, String, Int, String) -> Unit,
    onResetExport: () -> Unit,
    onUpdateModel: (String) -> Unit,
    onRenameChat: (String) -> Unit,
    onUpdateAgentPrompt: (String) -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    var showSkillSheet by remember { mutableStateOf(false) }
    var showModelSheet by remember { mutableStateOf(false) }
    var showBotMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showAgentPromptDialog by remember { mutableStateOf(false) }
    var lastSelectedSkill by remember { mutableStateOf<CanvaSkillEntity?>(null) }
    var lastSelectedStyle by remember { mutableStateOf("blank") }
    var lastSelectedModelId by remember { mutableStateOf("") }
    var lastSelectedSlideCount by remember { mutableStateOf(10) }
    var lastSelectedFileName by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Close skill sheet automatically when generation succeeds (status returns to Idle)
    var prevExportStatus by remember { mutableStateOf<ChatExportStatus>(ChatExportStatus.Idle) }
    LaunchedEffect(uiState.exportStatus) {
        if (prevExportStatus is ChatExportStatus.Generating && uiState.exportStatus is ChatExportStatus.Idle) {
            showSkillSheet = false
        }
        prevExportStatus = uiState.exportStatus
    }

    // Re-scroll whenever messages change, typing state changes, or viewport shrinks (keyboard opens)
    val viewportHeight = listState.layoutInfo.viewportSize.height
    LaunchedEffect(uiState.messages.size, uiState.isTyping, viewportHeight) {
        val total = uiState.messages.size + if (uiState.isTyping) 1 else 0
        if (total > 0) listState.animateScrollToItem(total - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(modifier = Modifier.clickable { showRenameDialog = true }) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                uiState.conversation?.title ?: "Chat",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 17.sp,
                                color = TextWhite
                            )
                            Icon(Icons.Default.Edit, null, tint = TextGray.copy(alpha = 0.35f), modifier = Modifier.size(13.dp))
                        }
                        uiState.conversation?.meetingTitle?.let { mtitle ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.AttachFile, null,
                                    tint = AccentGreen.copy(alpha = 0.8f),
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    mtitle,
                                    fontSize = 11.sp,
                                    color = AccentGreen.copy(alpha = 0.8f),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro", tint = TextWhite)
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showBotMenu = true }) {
                            Icon(Icons.Default.SmartToy, "Agente AI", tint = TextGray.copy(alpha = 0.8f))
                        }
                        DropdownMenu(
                            expanded = showBotMenu,
                            onDismissRequest = { showBotMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Prompt agente", color = TextWhite, fontSize = 14.sp) },
                                leadingIcon = { Icon(Icons.Default.Edit, null, tint = TextGray, modifier = Modifier.size(18.dp)) },
                                onClick = { showBotMenu = false; showAgentPromptDialog = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Modello AI", color = TextWhite, fontSize = 14.sp) },
                                leadingIcon = { Icon(Icons.Default.Tune, null, tint = TextGray, modifier = Modifier.size(18.dp)) },
                                onClick = { showBotMenu = false; showModelSheet = true }
                            )
                        }
                    }
                    if (uiState.conversation?.meetingId != null) {
                        IconButton(onClick = { showSkillSheet = true }) {
                            Icon(
                                Icons.Default.Slideshow,
                                "Crea presentazione",
                                tint = AccentGreen.copy(alpha = 0.85f)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Messages ──────────────────────────────────────────────────────
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                if (uiState.messages.isEmpty() && !uiState.isTyping) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillParentMaxWidth()
                                .fillParentMaxHeight(0.6f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Scrivi un messaggio per iniziare",
                                color = TextGray.copy(alpha = 0.35f),
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    items(uiState.messages, key = { it.id }) { msg ->
                        MessageBubble(msg)
                    }
                    if (uiState.isTyping) {
                        item { TypingIndicator() }
                    }
                }
            }

            // ── Error banner ──────────────────────────────────────────────────
            if (uiState.error.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ErrorRed.copy(alpha = 0.1f))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.ErrorOutline, null,
                        tint = ErrorRed,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        uiState.error,
                        color = ErrorRed,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onClearError) {
                        Text("Ok", color = ErrorRed, fontSize = 12.sp)
                    }
                }
            }

            // ── Input area ────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            "Scrivi un messaggio...",
                            color = TextGray.copy(alpha = 0.35f),
                            fontSize = 14.sp
                        )
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedBorderColor = AccentGreen.copy(alpha = 0.5f),
                        unfocusedBorderColor = DarkSurfaceVariant,
                        cursorColor = AccentGreen,
                        focusedContainerColor = DarkBackground,
                        unfocusedContainerColor = DarkBackground
                    ),
                    maxLines = 5,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp)
                )
                Spacer(Modifier.width(8.dp))
                val canSend = inputText.isNotBlank() && !uiState.isTyping
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (canSend) AccentGreen else DarkSurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = {
                            if (canSend) {
                                onSendMessage(inputText)
                                inputText = ""
                            }
                        },
                        enabled = canSend
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            "Invia",
                            tint = if (canSend) DarkBackground else TextGray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }

    // ── Model selector bottom sheet ───────────────────────────────────────────
    if (showModelSheet) {
        ModelSelectorSheet(
            models = AVAILABLE_MODELS,
            currentModelId = uiState.currentModel,
            onSelectModel = { onUpdateModel(it) },
            onDismiss = { showModelSheet = false }
        )
    }

    // ── Rename chat dialog ────────────────────────────────────────────────────
    if (showRenameDialog) {
        var renameText by remember { mutableStateOf(uiState.conversation?.title ?: "") }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            containerColor = DarkSurface,
            title = {
                Text("RINOMINA CHAT", letterSpacing = 1.sp, color = AccentGreen,
                    fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite, unfocusedTextColor = TextWhite,
                        focusedBorderColor = AccentGreen, unfocusedBorderColor = DarkSurfaceVariant,
                        cursorColor = AccentGreen,
                        focusedContainerColor = DarkBackground, unfocusedContainerColor = DarkBackground
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { if (renameText.isNotBlank()) { onRenameChat(renameText); showRenameDialog = false } },
                    enabled = renameText.isNotBlank()
                ) { Text("SALVA", color = AccentGreen, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("ANNULLA", color = TextGray) }
            }
        )
    }

    // ── Agent prompt dialog ───────────────────────────────────────────────────
    if (showAgentPromptDialog) {
        var promptText by remember { mutableStateOf(uiState.conversation?.agentPrompt ?: "") }
        Dialog(onDismissRequest = { showAgentPromptDialog = false }) {
            Surface(shape = RoundedCornerShape(16.dp), color = DarkSurface, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("ISTRUZIONI AGGIUNTIVE", letterSpacing = 1.sp, color = AccentGreen,
                        fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Aggiungi istruzioni specifiche per questa chat. Si sommano al comportamento base di Voxlog, che rimane sempre attivo.",
                        color = TextGray.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall, lineHeight = 17.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = promptText,
                        onValueChange = { promptText = it },
                        placeholder = {
                            Text("Es. Sei un esperto di marketing. Rispondi sempre con esempi pratici e un tono diretto...",
                                color = TextGray.copy(alpha = 0.4f), style = MaterialTheme.typography.bodySmall)
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                        minLines = 6,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextWhite, unfocusedTextColor = TextWhite,
                            focusedBorderColor = AccentGreen, unfocusedBorderColor = DarkSurfaceVariant,
                            cursorColor = AccentGreen,
                            focusedContainerColor = DarkBackground, unfocusedContainerColor = DarkBackground
                        ),
                        shape = RoundedCornerShape(8.dp),
                        textStyle = MaterialTheme.typography.bodySmall.copy(color = TextWhite, lineHeight = 20.sp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { showAgentPromptDialog = false },
                            modifier = Modifier.weight(1f).height(44.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceVariant),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextGray),
                            shape = RoundedCornerShape(8.dp)
                        ) { Text("ANNULLA", fontSize = 12.sp) }
                        Button(
                            onClick = { onUpdateAgentPrompt(promptText); showAgentPromptDialog = false },
                            modifier = Modifier.weight(1f).height(44.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = DarkBackground),
                            shape = RoundedCornerShape(8.dp)
                        ) { Text("SALVA", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }

    // ── Slide creation wizard ─────────────────────────────────────────────────
    if (showSkillSheet) {
        com.transcriber.app.ui.components.SlideSelectorDialog(
            skills = uiState.skills,
            currentModelId = uiState.currentModel,
            defaultFileName = uiState.conversation?.meetingTitle ?: uiState.conversation?.title ?: "",
            onGenerate = { skill, style, modelId, slideCount, fileName ->
                onExportSkill(skill, style, modelId, slideCount, fileName)
            },
            onDismiss = {
                showSkillSheet = false
                onResetExport()
            }
        )
    }
}

// ── Model selector bottom sheet ───────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelSelectorSheet(
    models: List<LlmModel>,
    currentModelId: String,
    onSelectModel: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = DarkSurfaceVariant) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                "MODELLO AI",
                letterSpacing = 1.sp,
                style = MaterialTheme.typography.labelLarge,
                color = AccentGreen,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Il modello selezionato verrà usato per le prossime risposte",
                color = TextGray.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 17.sp
            )
            Spacer(Modifier.height(20.dp))
            models.forEach { model ->
                val isSelected = model.id == currentModelId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) AccentGreen.copy(alpha = 0.1f) else Color.Transparent)
                        .clickable {
                            onSelectModel(model.id)
                            onDismiss()
                        }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            model.displayName,
                            color = if (isSelected) AccentGreen else TextWhite,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                        Text(model.description, color = TextGray, fontSize = 12.sp)
                    }
                    if (isSelected) {
                        Icon(Icons.Default.Check, null, tint = AccentGreen, modifier = Modifier.size(18.dp))
                    }
                }
                HorizontalDivider(color = DarkSurfaceVariant.copy(alpha = 0.5f), thickness = 0.5.dp)
            }
        }
    }
}

// ── Message bubbles ───────────────────────────────────────────────────────────

@Composable
private fun MessageBubble(msg: ChatMessageEntity) {
    if (msg.role == "system_link") {
        LinkCard(msg.content)
        return
    }
    val isUser = msg.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 290.dp)
                .background(
                    color = if (isUser) AccentGreen else DarkSurface,
                    shape = RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (isUser) 18.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 18.dp
                    )
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            if (isUser) {
                Text(msg.content, color = DarkBackground, fontSize = 14.sp, lineHeight = 20.sp)
            } else {
                MarkdownText(
                    text = msg.content,
                    style = androidx.compose.ui.text.TextStyle(
                        color = TextWhite,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun LinkCard(content: String) {
    val parts = content.split("\n", limit = 2)
    val skillName = parts.getOrNull(0) ?: "Presentazione"
    val link = parts.getOrNull(1) ?: content
    val uriHandler = LocalUriHandler.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 310.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, AccentGreen.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                .background(AccentGreen.copy(alpha = 0.07f))
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Slideshow, null,
                    tint = AccentGreen,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    skillName,
                    color = AccentGreen,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                link,
                color = TextGray.copy(alpha = 0.7f),
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { uriHandler.openUri(link) },
                modifier = Modifier.fillMaxWidth(),
                border = androidx.compose.foundation.BorderStroke(1.dp, AccentGreen.copy(alpha = 0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentGreen),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("Apri presentazione", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ── Typing indicator ──────────────────────────────────────────────────────────

@Composable
private fun TypingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .background(
                    DarkSurface,
                    RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 4.dp)
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) { index ->
                    val delay = index * 160
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(500, delayMillis = delay),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "dot$index"
                    )
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(
                                TextGray.copy(alpha = alpha),
                                CircleShape
                            )
                    )
                }
            }
        }
    }
}
