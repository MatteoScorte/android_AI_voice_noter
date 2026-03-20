package com.transcriber.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.transcriber.app.ui.theme.*
import com.transcriber.app.viewmodel.SettingsUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onBack: () -> Unit,
    onUpdateOpenRouterKey: (String) -> Unit,
    onUpdateDeepgramKey: (String) -> Unit,
    onUpdateModel: (String) -> Unit,
    onUpdateLanguage: (String) -> Unit,
    onUpdateSupabaseUrl: (String) -> Unit,
    onUpdateSupabaseKey: (String) -> Unit,
    onUpdateSyncEnabled: (Boolean) -> Unit,
    onSave: () -> Unit,
    onSyncNow: () -> Unit
) {
    var modelExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SETTINGS", letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, color = TextWhite, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextWhite)
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

            // ── DEEPGRAM API ──
            SectionTitle("DEEPGRAM (AUDIO)", Icons.Default.Hearing)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = uiState.deepgramApiKey,
                onValueChange = onUpdateDeepgramKey,
                label = { Text("API Key") },
                placeholder = { Text("dg_...", color = TextGray.copy(alpha = 0.5f)) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                colors = textFieldColors(), shape = RoundedCornerShape(8.dp), singleLine = true
            )
            Spacer(Modifier.height(32.dp))

            // ── OPENROUTER API ──
            SectionTitle("OPENROUTER (LLM)", Icons.Default.SmartToy)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = uiState.openRouterApiKey,
                onValueChange = onUpdateOpenRouterKey,
                label = { Text("API Key") },
                placeholder = { Text("sk-or-...", color = TextGray.copy(alpha=0.5f)) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                colors = textFieldColors(), shape = RoundedCornerShape(8.dp), singleLine = true
            )
            Spacer(Modifier.height(16.dp))
            Text("Model Strategy", color = TextGray, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth()) {
                val selectedDisplay = uiState.availableModels.find { it.id == uiState.selectedModel }?.displayName ?: uiState.selectedModel
                OutlinedTextField(
                    value = selectedDisplay, onValueChange = {},
                    readOnly = true, trailingIcon = { Icon(Icons.Default.ArrowDropDown, null, tint = TextGray) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = textFieldColors(), shape = RoundedCornerShape(8.dp)
                )

                // Overlay invisibile per catturare i tap senza triggerare input testo
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Transparent)
                        .clickable { modelExpanded = true }
                )

                DropdownMenu(
                    expanded = modelExpanded,
                    onDismissRequest = { modelExpanded = false }
                ) {
                    uiState.availableModels.forEach { model ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(model.displayName, color = DarkBackground, fontWeight = FontWeight.Medium)
                                    Text(model.description, color = DarkBackground.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                                }
                            },
                            onClick = { onUpdateModel(model.id); modelExpanded = false }
                        )
                    }
                }
            }
            Spacer(Modifier.height(32.dp))

            // ── CLOUD SYNC ──
            SectionTitle("CLOUD SYNC", Icons.Default.CloudSync)
            Spacer(Modifier.height(16.dp))

            // Enable toggle
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                border = BorderStroke(1.dp, DarkSurfaceVariant),
                shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Enable Sync", color = TextWhite, fontWeight = FontWeight.Medium)
                        Text("Backup to Supabase", color = TextGray, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = uiState.supabaseSyncEnabled,
                        onCheckedChange = onUpdateSyncEnabled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = DarkBackground,
                            checkedTrackColor = AccentGreen,
                            uncheckedThumbColor = TextGray,
                            uncheckedTrackColor = DarkSurfaceVariant
                        )
                    )
                }
            }

            if (uiState.supabaseSyncEnabled) {
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = uiState.supabaseUrl,
                    onValueChange = onUpdateSupabaseUrl,
                    label = { Text("Supabase URL") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = textFieldColors(), shape = RoundedCornerShape(8.dp), singleLine = true
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = uiState.supabaseAnonKey,
                    onValueChange = onUpdateSupabaseKey,
                    label = { Text("Supabase Anon Key") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = textFieldColors(), shape = RoundedCornerShape(8.dp), singleLine = true
                )
                Spacer(Modifier.height(16.dp))

                // Manual sync button
                OutlinedButton(
                    onClick = onSyncNow,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite),
                    border = BorderStroke(1.dp, TextWhite.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("MANUAL SYNC", letterSpacing = 1.sp, fontSize = 12.sp)
                }

                if (uiState.syncStatus.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(uiState.syncStatus, color = AccentGreen, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(48.dp))

            // ── SAVE BUTTON ──
            Button(
                onClick = onSave,
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = DarkBackground),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(Icons.Default.Check, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("SAVE SETTINGS", fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            }

            if (uiState.isSaved) {
                Spacer(Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = AccentGreen.copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, AccentGreen.copy(alpha = 0.5f))
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = AccentGreen, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Settings saved successfully", color = AccentGreen, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("AUDIO TRANSCRIBER v1.0", letterSpacing = 2.sp, color = TextGray.copy(alpha = 0.3f), style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = AccentGreen, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = AccentGreen, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextWhite, unfocusedTextColor = TextWhite,
    focusedBorderColor = AccentGreen, unfocusedBorderColor = DarkSurfaceVariant,
    cursorColor = AccentGreen, focusedLabelColor = AccentGreen, unfocusedLabelColor = TextGray,
    focusedPlaceholderColor = TextGray.copy(alpha = 0.5f), unfocusedPlaceholderColor = TextGray.copy(alpha = 0.3f)
)
