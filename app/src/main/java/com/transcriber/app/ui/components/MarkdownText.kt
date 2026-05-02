package com.transcriber.app.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Composable
fun MarkdownText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier
) {
    Text(text = parseMarkdown(text), style = style, modifier = modifier)
}

private fun parseMarkdown(raw: String): AnnotatedString = buildAnnotatedString {
    val lines = raw.split("\n")
    lines.forEachIndexed { index, line ->
        if (index > 0) append("\n")

        // Headers: #, ##, ###
        val headerMatch = Regex("^(#{1,3}) (.+)$").find(line.trim())
        if (headerMatch != null) {
            val level = headerMatch.groupValues[1].length
            val fs = when (level) { 1 -> 19; 2 -> 17; else -> 15 }
            pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = fs.sp))
            appendInline(headerMatch.groupValues[2])
            pop()
            return@forEachIndexed
        }

        // Horizontal rule
        if (line.trim().matches(Regex("^(-{3,}|\\*{3,}|_{3,})$"))) {
            append("─────────────────────")
            return@forEachIndexed
        }

        // Bullet list: - item or * item
        val bulletMatch = Regex("^[\\-\\*] (.+)$").find(line)
        if (bulletMatch != null) {
            append("• ")
            appendInline(bulletMatch.groupValues[1])
            return@forEachIndexed
        }

        // Numbered list: 1. item
        val numberedMatch = Regex("^(\\d+\\.) (.+)$").find(line)
        if (numberedMatch != null) {
            append(numberedMatch.groupValues[1] + " ")
            appendInline(numberedMatch.groupValues[2])
            return@forEachIndexed
        }

        // Plain line
        appendInline(line)
    }
}

private fun AnnotatedString.Builder.appendInline(text: String) {
    var i = 0
    while (i < text.length) {
        when {
            // **bold**
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end != -1) {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(text.substring(i + 2, end))
                    pop()
                    i = end + 2
                } else {
                    append("**")
                    i += 2
                }
            }
            // *italic* (single asterisk, not double)
            text[i] == '*' -> {
                val end = text.indexOf('*', i + 1)
                if (end != -1) {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(text.substring(i + 1, end))
                    pop()
                    i = end + 1
                } else {
                    append(text[i])
                    i++
                }
            }
            // `inline code`
            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end != -1) {
                    pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0x22FFFFFF)))
                    append(text.substring(i + 1, end))
                    pop()
                    i = end + 1
                } else {
                    append(text[i])
                    i++
                }
            }
            else -> {
                append(text[i])
                i++
            }
        }
    }
}
