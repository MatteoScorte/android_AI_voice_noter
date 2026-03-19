package com.transcriber.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.transcriber.app.data.InboxItem
import com.transcriber.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun InboxTab(
    items: List<InboxItem>,
    onImportFile: (Uri) -> Unit,
    onProcess: (InboxItem, String, String) -> Unit,
    onDelete: (String) -> Unit
) {
    var itemToProcess by remember { mutableStateOf<InboxItem?>(null) }
    var itemToDeleteId by remember { mutableStateOf<String?>(null) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(onImportFile)
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(16.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "AUDIO INBOX",
                style = MaterialTheme.typography.labelMedium,
                letterSpacing = 2.sp,
                color = TextGray
            )
            Text(
                "${items.size} ${if (items.size == 1) "file" else "file"}",
                style = MaterialTheme.typography.labelMedium,
                color = TextGray
            )
        }
        Spacer(Modifier.height(16.dp))

        OutlinedButton(
            onClick = { filePicker.launch("audio/*") },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentGreen),
            border = BorderStroke(1.dp, AccentGreen.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Icon(Icons.Default.Add, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("CARICA FILE AUDIO", letterSpacing = 1.sp, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(16.dp))

        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.MoveToInbox,
                        null,
                        tint = TextGray.copy(alpha = 0.3f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Nessun file in attesa",
                        color = TextGray.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Carica un file audio o condividilo da un'altra app",
                        color = TextGray.copy(alpha = 0.4f),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(items, key = { it.id }) { item ->
                    InboxItemCard(
                        item = item,
                        onProcess = { itemToProcess = item },
                        onDelete = { itemToDeleteId = item.id }
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    // Delete confirmation dialog
    if (itemToDeleteId != null) {
        Dialog(onDismissRequest = { itemToDeleteId = null }) {
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
                    Text(
                        "RIMUOVI",
                        letterSpacing = 1.sp,
                        style = MaterialTheme.typography.labelLarge,
                        color = ErrorRed,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Rimuovere questo file dall'inbox? Il file verrà eliminato definitivamente.",
                        color = TextGray,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(32.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedButton(
                            onClick = { itemToDeleteId = null },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite),
                            border = BorderStroke(1.dp, DarkSurfaceVariant),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Text("ANNULLA", letterSpacing = 1.sp, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedButton(
                            onClick = { onDelete(itemToDeleteId!!); itemToDeleteId = null },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                            border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Text("RIMUOVI", letterSpacing = 1.sp, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }

    // Process dialog
    itemToProcess?.let { item ->
        ProcessInboxItemDialog(
            item = item,
            onDismiss = { itemToProcess = null },
            onConfirm = { title, language ->
                onProcess(item, title, language)
                itemToProcess = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProcessInboxItemDialog(
    item: InboxItem,
    onDismiss: () -> Unit,
    onConfirm: (title: String, language: String) -> Unit
) {
    var title by remember { mutableStateOf(item.displayName.substringBeforeLast('.')) }
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
                Text(
                    "ELABORA FILE",
                    letterSpacing = 1.sp,
                    style = MaterialTheme.typography.labelLarge,
                    color = AccentGreen,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    item.displayName,
                    color = TextGray,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(24.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Titolo (Opzionale)", style = MaterialTheme.typography.bodySmall) },
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
                        label = { Text("Lingua", style = MaterialTheme.typography.bodySmall, color = AccentGreen) },
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
                    border = BorderStroke(1.dp, AccentGreen.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Icon(Icons.Default.Send, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("AVVIA ELABORAZIONE", letterSpacing = 2.sp, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun InboxItemCard(item: InboxItem, onProcess: () -> Unit, onDelete: () -> Unit) {
    val dateStr = remember(item.addedAt) {
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALIAN).format(Date(item.addedAt))
    }
    val sizeStr = remember(item.sizeBytes) { formatFileSize(item.sizeBytes) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, DarkSurfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Audiotrack, null, tint = AccentGreen, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        item.displayName,
                        color = TextWhite,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 15.sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Text("$sizeStr  •  $dateStr", color = TextGray, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.DeleteOutline, "Rimuovi", tint = TextGray, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onProcess,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentGreen),
                border = BorderStroke(1.dp, AccentGreen.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().height(40.dp)
            ) {
                Text("ELABORA", letterSpacing = 1.sp, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Default.ChevronRight, null, Modifier.size(16.dp))
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
}
