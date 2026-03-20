package com.transcriber.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.transcriber.app.ui.theme.*
import com.transcriber.app.viewmodel.CategoryViewModel
import java.text.BreakIterator

/** Palette of preset colors the user can pick from. */
private val PRESET_COLORS = listOf(
    "#4A90D9",  // blue
    "#49DD7F",  // green
    "#FF8A65",  // orange
    "#B39DDB",  // purple
    "#FFD54F",  // amber
    "#F06292",  // pink
    "#4DD0E1",  // cyan
    "#FF5252",  // red
    "#80CBC4",  // teal
    "#CE93D8",  // light purple
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryEditorScreen(
    categoryId: Int,
    viewModel: CategoryViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.editor.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(categoryId) {
        viewModel.initEditor(categoryId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.isNew) "NUOVA CATEGORIA" else "MODIFICA CATEGORIA",
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextWhite,
                        fontSize = 16.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro", tint = TextWhite)
                    }
                },
                actions = {
                    if (!state.isNew && !state.isDefault) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.DeleteOutline, "Elimina", tint = ErrorRed)
                        }
                    } else if (state.isDefault) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Default — non eliminabile",
                            tint = TextGray.copy(alpha = 0.5f),
                            modifier = Modifier.padding(end = 12.dp).size(20.dp)
                        )
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
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(24.dp))

            // ── EMOJI ──────────────────────────────────────────────────────────
            FieldLabel("EMOJI")
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Live preview bubble
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(parseHexColor(state.colorHex).copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(state.emoji.ifBlank { "📝" }, fontSize = 24.sp)
                }
                Spacer(Modifier.width(16.dp))
                OutlinedTextField(
                    value = state.emoji,
                    onValueChange = { newVal ->
                        // Keep only the first grapheme cluster (handles emoji surrogate pairs)
                        viewModel.updateEmoji(firstGraphemeCluster(newVal))
                    },
                    placeholder = { Text("📝", color = TextGray.copy(alpha = 0.5f)) },
                    modifier = Modifier.width(100.dp),
                    singleLine = true,
                    colors = editorFieldColors(),
                    shape = RoundedCornerShape(8.dp)
                )
            }

            Spacer(Modifier.height(24.dp))

            // ── NOME ───────────────────────────────────────────────────────────
            FieldLabel("NOME CATEGORIA")
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::updateName,
                placeholder = { Text("Es. Lezione, Colloquio...", color = TextGray.copy(alpha = 0.5f)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = editorFieldColors(),
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(Modifier.height(24.dp))

            // ── COLORE ─────────────────────────────────────────────────────────
            FieldLabel("COLORE")
            Spacer(Modifier.height(12.dp))
            ColorPicker(
                selectedHex = state.colorHex,
                onSelect = viewModel::updateColor
            )

            Spacer(Modifier.height(28.dp))

            // ── SYSTEM PROMPT ──────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FieldLabel("SYSTEM PROMPT")
                Text(
                    "${state.systemPrompt.length} car.",
                    color = TextGray.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.systemPrompt,
                onValueChange = viewModel::updatePrompt,
                placeholder = {
                    Text(
                        "Scrivi le istruzioni per il modello LLM. Definisci tono, struttura e obiettivo dell'analisi.",
                        color = TextGray.copy(alpha = 0.4f),
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 220.dp),
                minLines = 10,
                colors = editorFieldColors(),
                shape = RoundedCornerShape(8.dp),
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    color = TextWhite,
                    lineHeight = 20.sp
                )
            )

            if (state.isDefault) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Questa è una categoria di sistema. Puoi modificare il prompt, ma non eliminarla.",
                    color = TextGray.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelSmall,
                    lineHeight = 16.sp
                )
            }

            Spacer(Modifier.height(36.dp))

            // ── SALVA ──────────────────────────────────────────────────────────
            val canSave = state.name.isNotBlank()
            Button(
                onClick = { viewModel.save(onBack) },
                enabled = canSave,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentGreen,
                    contentColor = DarkBackground,
                    disabledContainerColor = DarkSurfaceVariant,
                    disabledContentColor = TextGray
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(Icons.Default.Check, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("SALVA", fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            }

            Spacer(Modifier.height(40.dp))
        }
    }

    // ── Delete confirmation dialog ─────────────────────────────────────────
    if (showDeleteDialog) {
        Dialog(onDismissRequest = { showDeleteDialog = false }) {
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
                    Text("ELIMINA CATEGORIA", letterSpacing = 1.sp, style = MaterialTheme.typography.labelLarge, color = ErrorRed, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Vuoi eliminare \"${state.name}\"? L'azione è irreversibile.",
                        color = TextGray,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(28.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showDeleteDialog = false },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite),
                            border = BorderStroke(1.dp, DarkSurfaceVariant),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Text("ANNULLA", letterSpacing = 1.sp, fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                showDeleteDialog = false
                                viewModel.delete(onBack)
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                            border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.5f)),
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
}

// ── Color picker ───────────────────────────────────────────────────────────────

@Composable
private fun ColorPicker(selectedHex: String, onSelect: (String) -> Unit) {
    val selectedColor = parseHexColor(selectedHex)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PRESET_COLORS.forEach { hex ->
            val color = parseHexColor(hex)
            val isSelected = hex.equals(selectedHex, ignoreCase = true)
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(color)
                    .then(
                        if (isSelected) Modifier.border(3.dp, Color.White, CircleShape)
                        else Modifier.border(2.dp, color.copy(alpha = 0.0f), CircleShape)
                    )
                    .clickable { onSelect(hex) }
            )
        }
    }
    // Show selected hex value below the picker
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(selectedColor)
        )
        Spacer(Modifier.width(6.dp))
        Text(selectedHex.uppercase(), color = TextGray, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = AccentGreen,
        letterSpacing = 1.5.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun editorFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextWhite,
    unfocusedTextColor = TextWhite,
    focusedBorderColor = AccentGreen,
    unfocusedBorderColor = DarkSurfaceVariant,
    cursorColor = AccentGreen,
    focusedLabelColor = AccentGreen,
    unfocusedLabelColor = TextGray,
    focusedPlaceholderColor = TextGray.copy(alpha = 0.4f),
    unfocusedPlaceholderColor = TextGray.copy(alpha = 0.3f)
)

/** Returns only the first grapheme cluster (handles emoji surrogate pairs correctly). */
private fun firstGraphemeCluster(text: String): String {
    if (text.isEmpty()) return text
    val bi = BreakIterator.getCharacterInstance()
    bi.setText(text)
    val end = bi.next()
    return if (end == BreakIterator.DONE) text else text.substring(0, end)
}
