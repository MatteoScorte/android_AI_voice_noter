package com.transcriber.app.util

import com.transcriber.app.data.WordTimestamp

// ── Phrase model ──────────────────────────────────────────────────────────────

/**
 * A logical speech unit derived at runtime from [WordTimestamp] entries.
 * Not serialized — computed from [groupIntoPhrases] and held only in UI state.
 */
data class Phrase(
    val text: String,
    val startTime: Double,  // seconds from audio start (first word)
    val endTime: Double     // seconds from audio start (last word)
)

/**
 * Groups a flat [WordTimestamp] list into [Phrase] objects suitable for
 * Spotify-style lyrics display.
 *
 * A new phrase boundary is created when ANY of the following is true for
 * the word just added to the current bucket:
 *  1. It ends with sentence-closing punctuation (. ! ?)
 *  2. The gap to the next word exceeds [pauseThresholdSec] — natural speaker pause
 *  3. The bucket has reached [maxWordsPerPhrase] words
 *  4. It ends with a clause marker (, ; :) AND the bucket already has ≥ [minWordsForClause]
 */
fun groupIntoPhrases(
    words: List<WordTimestamp>,
    maxWordsPerPhrase: Int = 10,
    pauseThresholdSec: Double = 0.6,
    minWordsForClause: Int = 5
): List<Phrase> {
    if (words.isEmpty()) return emptyList()

    val result  = mutableListOf<Phrase>()
    val bucket  = mutableListOf<WordTimestamp>()

    fun flush() {
        if (bucket.isEmpty()) return
        result += Phrase(
            text      = bucket.joinToString(" ") { it.word },
            startTime = bucket.first().start,
            endTime   = bucket.last().end
        )
        bucket.clear()
    }

    words.forEachIndexed { i, word ->
        bucket += word
        val isLast       = i == words.lastIndex
        val w            = word.word
        val sentenceEnd  = w.endsWith(".") || w.endsWith("!") || w.endsWith("?")
        val clauseEnd    = (w.endsWith(",") || w.endsWith(";") || w.endsWith(":"))
                           && bucket.size >= minWordsForClause
        val tooLong      = bucket.size >= maxWordsPerPhrase
        val longPause    = !isLast && (words[i + 1].start - word.end) >= pauseThresholdSec

        if (isLast || sentenceEnd || clauseEnd || tooLong || longPause) flush()
    }

    flush() // safety-net for any leftover words
    return result
}

// ── Timestamp utilities ───────────────────────────────────────────────────────

/**
 * Converts a "MM:SS" or "HH:MM:SS" timestamp string to milliseconds.
 *
 * Examples:
 *   "00:30" -> 30_000
 *   "15:30" -> 930_000
 *   "01:05:00" -> 3_900_000
 *   "bad"   -> 0  (graceful fallback)
 */
fun parseTimestampToMs(timestamp: String): Long {
    val parts = timestamp.trim().split(":").mapNotNull { it.trim().toLongOrNull() }
    return when (parts.size) {
        2    -> (parts[0] * 60L + parts[1]) * 1_000L
        3    -> (parts[0] * 3_600L + parts[1] * 60L + parts[2]) * 1_000L
        else -> 0L
    }
}

/**
 * Returns the index of the word currently being spoken at [currentMs] playback position.
 *
 * - Returns -1 if [words] is empty or [currentMs] is 0 (playback hasn't started).
 * - If the cursor falls inside a word's [start..end) window, that word is active.
 * - If the cursor is between two words (inter-word gap), returns the last word whose
 *   end <= currentSec, so the highlight doesn't disappear mid-sentence.
 */
fun findActiveWordIndex(words: List<WordTimestamp>, currentMs: Long): Int {
    if (words.isEmpty() || currentMs <= 0L) return -1
    val currentSec = currentMs / 1000.0
    val inProgress = words.indexOfFirst { it.start <= currentSec && currentSec < it.end }
    return if (inProgress >= 0) inProgress
    else words.indexOfLast { it.end <= currentSec }
}
