package com.transcriber.app

import com.transcriber.app.data.WordTimestamp
import com.transcriber.app.util.findActiveWordIndex
import com.transcriber.app.util.parseTimestampToMs
import org.junit.Assert.*
import org.junit.Test

// ─────────────────────────────────────────────────────────────────────────────
//  parseTimestampToMs — Feature 3: Scaletta interattiva
// ─────────────────────────────────────────────────────────────────────────────

class ParseTimestampToMsTest {

    @Test
    fun `MM_SS converts correctly`() {
        assertEquals(930_000L, parseTimestampToMs("15:30"))
    }

    @Test
    fun `MM_SS zero seconds`() {
        assertEquals(60_000L, parseTimestampToMs("01:00"))
    }

    @Test
    fun `MM_SS zero all`() {
        assertEquals(0L, parseTimestampToMs("00:00"))
    }

    @Test
    fun `HH_MM_SS converts correctly`() {
        assertEquals(3_900_000L, parseTimestampToMs("01:05:00"))
    }

    @Test
    fun `HH_MM_SS one hour`() {
        assertEquals(3_600_000L, parseTimestampToMs("01:00:00"))
    }

    @Test
    fun `HH_MM_SS full`() {
        assertEquals(7_322_000L, parseTimestampToMs("02:02:02"))
    }

    @Test
    fun `single segment returns 0`() {
        assertEquals(0L, parseTimestampToMs("30"))
    }

    @Test
    fun `non-numeric string returns 0`() {
        assertEquals(0L, parseTimestampToMs("bad"))
    }

    @Test
    fun `empty string returns 0`() {
        assertEquals(0L, parseTimestampToMs(""))
    }

    @Test
    fun `leading and trailing whitespace is trimmed`() {
        assertEquals(930_000L, parseTimestampToMs("  15:30  "))
    }

    @Test
    fun `small value 00_05 returns 5 seconds`() {
        assertEquals(5_000L, parseTimestampToMs("00:05"))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  findActiveWordIndex — Feature 4: Karaoke / Trascrizione Sincronizzata
// ─────────────────────────────────────────────────────────────────────────────

class FindActiveWordIndexTest {

    // Helper to build compact WordTimestamp entries
    private fun word(text: String, start: Double, end: Double) =
        WordTimestamp(word = text, start = start, end = end)

    private val sampleWords = listOf(
        word("Ciao",       start = 0.0,  end = 0.5),
        word("mondo",      start = 0.6,  end = 1.1),
        word("come",       start = 1.2,  end = 1.5),
        word("stai",       start = 1.6,  end = 2.0),
        word("oggi",       start = 2.1,  end = 2.6),
    )

    // ── currentMs = 0 ────────────────────────────────────────────────────────

    @Test
    fun `returns -1 when currentMs is 0`() {
        assertEquals(-1, findActiveWordIndex(sampleWords, 0L))
    }

    // ── playback inside a word window ─────────────────────────────────────────

    @Test
    fun `identifies first word at 200ms`() {
        // 200ms = 0.2s — inside "Ciao" [0.0, 0.5)
        assertEquals(0, findActiveWordIndex(sampleWords, 200L))
    }

    @Test
    fun `identifies second word at 800ms`() {
        // 800ms = 0.8s — inside "mondo" [0.6, 1.1)
        assertEquals(1, findActiveWordIndex(sampleWords, 800L))
    }

    @Test
    fun `identifies middle word at 1300ms`() {
        // 1300ms = 1.3s — inside "come" [1.2, 1.5)
        assertEquals(2, findActiveWordIndex(sampleWords, 1_300L))
    }

    @Test
    fun `identifies last word at 2500ms`() {
        // 2500ms = 2.5s — inside "oggi" [2.1, 2.6)
        assertEquals(4, findActiveWordIndex(sampleWords, 2_500L))
    }

    // ── inter-word gap: cursor between two words ──────────────────────────────

    @Test
    fun `gap between word 0 and word 1 returns last completed word`() {
        // 550ms = 0.55s — gap between "Ciao"[0.0,0.5) and "mondo"[0.6,1.1)
        // Expected: index 0 (last word whose end <= 0.55)
        assertEquals(0, findActiveWordIndex(sampleWords, 550L))
    }

    @Test
    fun `gap between word 3 and word 4 returns index 3`() {
        // 2050ms = 2.05s — gap between "stai"[1.6,2.0) and "oggi"[2.1,2.6)
        assertEquals(3, findActiveWordIndex(sampleWords, 2_050L))
    }

    // ── past end of audio ─────────────────────────────────────────────────────

    @Test
    fun `past end of last word returns last word index`() {
        // 3000ms = 3.0s — past "oggi" which ends at 2.6s
        assertEquals(4, findActiveWordIndex(sampleWords, 3_000L))
    }

    // ── boundary conditions ───────────────────────────────────────────────────

    @Test
    fun `exactly at word start is active`() {
        // Exactly at 600ms = 0.6s — start of "mondo"
        assertEquals(1, findActiveWordIndex(sampleWords, 600L))
    }

    @Test
    fun `exactly at word end is NOT active (exclusive upper bound)`() {
        // 1100ms = 1.1s = end of "mondo" → not inside "mondo", should return last completed
        // 1.1 is not < 1.1, so indexOfFirst fails → falls to indexOfLast { end <= 1.1 } = index 1
        assertEquals(1, findActiveWordIndex(sampleWords, 1_100L))
    }

    @Test
    fun `empty list returns -1`() {
        assertEquals(-1, findActiveWordIndex(emptyList(), 500L))
    }

    @Test
    fun `single word list active at midpoint`() {
        val single = listOf(word("unico", 1.0, 2.0))
        assertEquals(0, findActiveWordIndex(single, 1_500L))
    }

    @Test
    fun `single word list before start returns -1`() {
        val single = listOf(word("unico", 1.0, 2.0))
        // currentMs = 0 → always -1
        assertEquals(-1, findActiveWordIndex(single, 0L))
    }

    @Test
    fun `negative currentMs treated as not started`() {
        assertEquals(-1, findActiveWordIndex(sampleWords, -100L))
    }
}
