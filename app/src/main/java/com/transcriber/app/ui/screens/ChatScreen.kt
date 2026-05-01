package com.transcriber.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.transcriber.app.data.ChatMessageEntity
import com.transcriber.app.ui.theme.*
import com.transcriber.app.viewmodel.ChatConversationUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    uiState: ChatConversationUiState,
    onBack: () -> Unit,
    onSendMessage: (String) -> Unit,
    onClearError: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Re-scroll whenever messages change, typing state changes, or the viewport shrinks
    // (e.g. keyboard opens — viewport height decreases so we pin to the last message)
    val viewportHeight = listState.layoutInfo.viewportSize.height
    LaunchedEffect(uiState.messages.size, uiState.isTyping, viewportHeight) {
        val total = uiState.messages.size + if (uiState.isTyping) 1 else 0
        if (total > 0) listState.animateScrollToItem(total - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            uiState.conversation?.title ?: "Chat",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp,
                            color = TextWhite
                        )
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
}

@Composable
private fun MessageBubble(msg: ChatMessageEntity) {
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
            Text(
                msg.content,
                color = if (isUser) DarkBackground else TextWhite,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}

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
