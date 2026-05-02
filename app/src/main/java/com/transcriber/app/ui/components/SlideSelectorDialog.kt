package com.transcriber.app.ui.components

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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.transcriber.app.api.AVAILABLE_MODELS
import com.transcriber.app.data.CanvaSkillEntity
import com.transcriber.app.ui.theme.*

data class SlideStyle(val id: String, val name: String, val description: String, val emoji: String)

val SLIDE_STYLES = listOf(
    SlideStyle("Steo",      "Steo",      "Tema colorato con grafica moderna",   "🎨"),
    SlideStyle("dark grey", "Dark Grey", "Tema scuro elegante e professionale", "🖤"),
    SlideStyle("blank",     "Blank",     "Slide bianche pulite",                "⬜")
)

@Composable
fun SlideSelectorDialog(
    skills: List<CanvaSkillEntity>,
    currentModelId: String,
    defaultFileName: String,
    onGenerate: (CanvaSkillEntity, String, String, Int, String) -> Unit,
    onDismiss: () -> Unit
) {
    var step by remember { mutableStateOf(1) }
    var selectedFileName by remember { mutableStateOf(defaultFileName) }
    var selectedSkill by remember { mutableStateOf<CanvaSkillEntity?>(null) }
    var selectedStyleId by remember { mutableStateOf("blank") }
    var selectedModelId by remember { mutableStateOf(currentModelId) }
    var selectedSlideCount by remember { mutableStateOf(10) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = DarkSurface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Step indicator + back button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (step > 1) {
                        IconButton(onClick = { step-- }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextGray, modifier = Modifier.size(20.dp))
                        }
                    } else {
                        Spacer(Modifier.size(48.dp))
                    }
                    Text(
                        "${step}/5",
                        color = TextGray.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.End
                    )
                }
                Spacer(Modifier.height(4.dp))

                when (step) {
                    // ── Step 1: nome file ──────────────────────────────────────
                    1 -> {
                        Text("NOME FILE", letterSpacing = 1.sp, style = MaterialTheme.typography.labelLarge,
                            color = AccentGreen, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("Come vuoi chiamare il file su Google Drive?",
                            color = TextGray.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center, lineHeight = 17.sp)
                        Spacer(Modifier.height(20.dp))
                        OutlinedTextField(
                            value = selectedFileName,
                            onValueChange = { selectedFileName = it },
                            placeholder = { Text("Es. Presentazione Q1 2025", color = TextGray.copy(alpha = 0.4f), style = MaterialTheme.typography.bodySmall) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextWhite, unfocusedTextColor = TextWhite,
                                focusedBorderColor = AccentGreen, unfocusedBorderColor = DarkSurfaceVariant,
                                cursorColor = AccentGreen,
                                focusedContainerColor = DarkBackground, unfocusedContainerColor = DarkBackground
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                        Spacer(Modifier.height(20.dp))
                        Button(
                            onClick = { step = 2 },
                            enabled = selectedFileName.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentGreen, contentColor = DarkBackground,
                                disabledContainerColor = DarkSurfaceVariant, disabledContentColor = TextGray
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) { Text("AVANTI", fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp) }
                    }

                    // ── Step 2: tipo di presentazione ──────────────────────────
                    2 -> {
                        Text("TIPO DI PRESENTAZIONE", letterSpacing = 1.sp, style = MaterialTheme.typography.labelLarge,
                            color = AccentGreen, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("Seleziona il template da usare per generare la presentazione",
                            color = TextGray.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center, lineHeight = 17.sp)
                        Spacer(Modifier.height(20.dp))
                        val supported = skills.filter { it.outputType == "Slide" }
                        supported.forEach { skill ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                    .clickable { selectedSkill = skill; step = 3 }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(skill.emoji, fontSize = 24.sp)
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(skill.name, color = TextWhite, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                    Text(skill.outputType, color = TextGray, fontSize = 12.sp)
                                }
                                Icon(Icons.Default.ChevronRight, null, tint = TextGray.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
                            }
                            HorizontalDivider(color = DarkSurfaceVariant.copy(alpha = 0.5f), thickness = 0.5.dp)
                        }
                        if (supported.isEmpty()) {
                            Text(
                                "Nessun template compatibile.\nCrea un template di tipo Slide.",
                                color = TextGray.copy(alpha = 0.5f), fontSize = 13.sp,
                                textAlign = TextAlign.Center, lineHeight = 18.sp
                            )
                        }
                    }

                    // ── Step 3: stile grafico ──────────────────────────────────
                    3 -> {
                        Text("STILE GRAFICO", letterSpacing = 1.sp, style = MaterialTheme.typography.labelLarge,
                            color = AccentGreen, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("${selectedSkill?.emoji ?: ""} ${selectedSkill?.name ?: ""}",
                            color = TextGray.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center)
                        Spacer(Modifier.height(20.dp))
                        SLIDE_STYLES.forEach { style ->
                            val isSelected = style.id == selectedStyleId
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) AccentGreen.copy(alpha = 0.08f) else Color.Transparent)
                                    .clickable { selectedStyleId = style.id; step = 4 }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(style.emoji, fontSize = 22.sp)
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(style.name, color = if (isSelected) AccentGreen else TextWhite,
                                        fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                    Text(style.description, color = TextGray, fontSize = 12.sp)
                                }
                                if (isSelected) Icon(Icons.Default.Check, null, tint = AccentGreen, modifier = Modifier.size(16.dp))
                                else Icon(Icons.Default.ChevronRight, null, tint = TextGray.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
                            }
                            HorizontalDivider(color = DarkSurfaceVariant.copy(alpha = 0.5f), thickness = 0.5.dp)
                        }
                    }

                    // ── Step 4: modello AI ─────────────────────────────────────
                    4 -> {
                        Text("MODELLO AI", letterSpacing = 1.sp, style = MaterialTheme.typography.labelLarge,
                            color = AccentGreen, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("Scegli il modello da usare per generare i contenuti",
                            color = TextGray.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center, lineHeight = 17.sp)
                        Spacer(Modifier.height(20.dp))
                        AVAILABLE_MODELS.forEach { model ->
                            val isSelected = model.id == selectedModelId
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) AccentGreen.copy(alpha = 0.08f) else Color.Transparent)
                                    .clickable { selectedModelId = model.id; step = 5 }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(model.displayName, color = if (isSelected) AccentGreen else TextWhite,
                                        fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                    Text(model.description, color = TextGray, fontSize = 12.sp)
                                }
                                if (isSelected) Icon(Icons.Default.Check, null, tint = AccentGreen, modifier = Modifier.size(16.dp))
                            }
                            HorizontalDivider(color = DarkSurfaceVariant.copy(alpha = 0.5f), thickness = 0.5.dp)
                        }
                    }

                    // ── Step 5: numero di slide + Genera ──────────────────────
                    5 -> {
                        Text("NUMERO DI SLIDE", letterSpacing = 1.sp, style = MaterialTheme.typography.labelLarge,
                            color = AccentGreen, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("Quante slide vuoi generare?",
                            color = TextGray.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center)
                        Spacer(Modifier.height(28.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(48.dp).clip(CircleShape)
                                    .background(DarkBackground).border(1.dp, DarkSurfaceVariant, CircleShape)
                                    .clickable { if (selectedSlideCount > 3) selectedSlideCount-- },
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Default.Remove, null, tint = TextWhite, modifier = Modifier.size(20.dp)) }
                            Spacer(Modifier.width(32.dp))
                            Text("$selectedSlideCount", color = TextWhite, fontSize = 40.sp,
                                fontWeight = FontWeight.Bold, modifier = Modifier.widthIn(min = 64.dp),
                                textAlign = TextAlign.Center)
                            Spacer(Modifier.width(32.dp))
                            Box(
                                modifier = Modifier.size(48.dp).clip(CircleShape)
                                    .background(DarkBackground).border(1.dp, DarkSurfaceVariant, CircleShape)
                                    .clickable { if (selectedSlideCount < 25) selectedSlideCount++ },
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Default.Add, null, tint = TextWhite, modifier = Modifier.size(20.dp)) }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("min 3 · max 25", color = TextGray.copy(alpha = 0.4f), fontSize = 11.sp)
                        Spacer(Modifier.height(28.dp))
                        Button(
                            onClick = {
                                onGenerate(selectedSkill!!, selectedStyleId, selectedModelId, selectedSlideCount, selectedFileName)
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Slideshow, null, modifier = Modifier.size(18.dp), tint = DarkBackground)
                            Spacer(Modifier.width(8.dp))
                            Text("Genera presentazione", color = DarkBackground, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}
