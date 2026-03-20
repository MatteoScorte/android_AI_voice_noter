package com.transcriber.app

import com.transcriber.app.data.MeetingStatus
import com.transcriber.app.data.WordTimestamp
import com.transcriber.app.util.findActiveWordIndex
import com.transcriber.app.util.parseTimestampToMs
import com.transcriber.app.viewmodel.TranscriptUiState
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests the pure-Kotlin logic around:
 *  - seekTo clamping (Feature 2: Player Audio)
 *  - Scaletta timestamp-to-ms conversion for seek (Feature 3)
 *  - Clipboard copy text selection (Feature 1: Pulsanti Copia)
 *  - Karaoke state transitions during playback (Feature 4)
 *
 * These tests cover the deterministic, Android-free business logic.
 * MediaPlayer lifecycle and Android ClipboardManager are excluded here
 * because they require an emulated Android environment (Robolectric or
 * on-device instrumentation tests — see notes at the bottom of this file).
 */
class PlayerAndCopyLogicTest {

    // ─────────────────────────────────────────────────────────────────────────
    //  Feature 2 — Player: seekTo clamping logic
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Mirrors the clamping done in TranscriptViewModel.seekTo():
     *   val clamped = ms.coerceIn(0L, uiState.playerDurationMs)
     */
    private fun simulateSeekTo(requestedMs: Long, durationMs: Long): Long =
        requestedMs.coerceIn(0L, durationMs)

    @Test
    fun `seekTo within range passes through unchanged`() {
        assertEquals(5_000L, simulateSeekTo(5_000L, 10_000L))
    }

    @Test
    fun `seekTo below zero is clamped to 0`() {
        assertEquals(0L, simulateSeekTo(-500L, 10_000L))
    }

    @Test
    fun `seekTo beyond duration is clamped to duration`() {
        assertEquals(10_000L, simulateSeekTo(15_000L, 10_000L))
    }

    @Test
    fun `seekTo exactly at 0 is valid`() {
        assertEquals(0L, simulateSeekTo(0L, 10_000L))
    }

    @Test
    fun `seekTo exactly at duration is valid`() {
        assertEquals(10_000L, simulateSeekTo(10_000L, 10_000L))
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Feature 3 — Scaletta: Timestamp → seekTo integration
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `outline timestamp 15_30 seeks to 930 seconds`() {
        val ms = parseTimestampToMs("15:30")
        assertEquals(930_000L, ms)
        // Simulated seekTo with a 60-minute recording
        assertEquals(930_000L, simulateSeekTo(ms, 3_600_000L))
    }

    @Test
    fun `outline timestamp 00_00 seeks to start`() {
        val ms = parseTimestampToMs("00:00")
        assertEquals(0L, simulateSeekTo(ms, 3_600_000L))
    }

    @Test
    fun `outline timestamp beyond duration is clamped`() {
        val ms = parseTimestampToMs("25:00")  // 25 min = 1_500_000ms
        val duration = 600_000L               // 10 min recording
        assertEquals(duration, simulateSeekTo(ms, duration))
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Feature 1 — Pulsanti Copia: text selection logic
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * The copy buttons pass `uiState.overview` and `uiState.finalTranscript`
     * to copyToClipboard(). We verify the correct text is available in the state.
     */
    @Test
    fun `overview text is available in uiState for copy`() {
        val state = TranscriptUiState(overview = "Questa è la panoramica della riunione.")
        assertFalse("Overview must not be blank to show copy button", state.overview.isBlank())
        assertEquals("Questa è la panoramica della riunione.", state.overview)
    }

    @Test
    fun `finalTranscript text is available in uiState for copy`() {
        val state = TranscriptUiState(finalTranscript = "Speaker 0:\nContenuto della trascrizione.")
        assertFalse("finalTranscript must not be blank", state.finalTranscript.isBlank())
        assertEquals("Speaker 0:\nContenuto della trascrizione.", state.finalTranscript)
    }

    @Test
    fun `copy button is not shown when overview is blank`() {
        val state = TranscriptUiState(overview = "")
        // Mirrors the condition: if (uiState.overview.isNotBlank()) { ... CopyButton ... }
        assertFalse(state.overview.isNotBlank())
    }

    @Test
    fun `copy button is not shown when finalTranscript is blank`() {
        val state = TranscriptUiState(finalTranscript = "   ")
        assertFalse(state.finalTranscript.isNotBlank())
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Feature 4 — Karaoke: state transitions during playback
    // ─────────────────────────────────────────────────────────────────────────

    private val transcriptWords = listOf(
        WordTimestamp("Buongiorno",  0.0,  0.8),
        WordTimestamp("a",           0.9,  1.0),
        WordTimestamp("tutti",       1.1,  1.7),
        WordTimestamp("come",        1.9,  2.2),
        WordTimestamp("andiamo",     2.3,  3.0),
    )

    @Test
    fun `at playback start no word is highlighted`() {
        assertEquals(-1, findActiveWordIndex(transcriptWords, 0L))
    }

    @Test
    fun `first word highlighted during first word`() {
        // 400ms = 0.4s → inside "Buongiorno" [0.0, 0.8)
        assertEquals(0, findActiveWordIndex(transcriptWords, 400L))
    }

    @Test
    fun `word changes correctly as playback progresses`() {
        // Simulate 5 ticks advancing through the transcript
        val expectedIndices = listOf(
            Pair(100L,   0),   // 0.1s → "Buongiorno"
            Pair(950L,   1),   // 0.95s → "a"
            Pair(1_200L, 2),   // 1.2s → "tutti"
            Pair(2_000L, 3),   // 2.0s → "come"
            Pair(2_500L, 4),   // 2.5s → "andiamo"
        )
        for ((ms, expectedIdx) in expectedIndices) {
            assertEquals(
                "At ${ms}ms expected word index $expectedIdx",
                expectedIdx,
                findActiveWordIndex(transcriptWords, ms)
            )
        }
    }

    @Test
    fun `highlight stays on last word after audio ends`() {
        // 5000ms = 5.0s — past end of "andiamo" [2.3, 3.0)
        assertEquals(4, findActiveWordIndex(transcriptWords, 5_000L))
    }

    @Test
    fun `inter-word gap keeps previous word highlighted`() {
        // 1800ms = 1.8s — gap between "tutti"[1.1,1.7) and "come"[1.9,2.2)
        assertEquals(2, findActiveWordIndex(transcriptWords, 1_800L))
    }

    @Test
    fun `karaoke disabled when wordTimestamps is empty in state`() {
        val state = TranscriptUiState(wordTimestamps = emptyList())
        // Mirrors the condition: if (uiState.wordTimestamps.isNotEmpty()) { ... KaraokeText ... }
        assertFalse(state.wordTimestamps.isNotEmpty())
    }

    @Test
    fun `karaoke enabled when wordTimestamps is populated in state`() {
        val state = TranscriptUiState(wordTimestamps = transcriptWords)
        assertTrue(state.wordTimestamps.isNotEmpty())
        assertEquals(5, state.wordTimestamps.size)
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Regression: LLM pipeline fields unaffected
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `TranscriptUiState retains all pre-existing fields alongside new player fields`() {
        val state = TranscriptUiState(
            meetingId = "abc123",
            title = "Riunione Q1",
            durationMs = 3_600_000L,
            rawTranscript = "Speaker 0:\nHello.",
            finalTranscript = "Verbale completo.",
            status = MeetingStatus.COMPLETED,
            overview = "Sintesi breve.",
            keywords = listOf("budget", "Q1"),
            bulletNotes = listOf("Punto 1", "Punto 2"),
            // New player fields
            audioFilePath = "/data/app/recordings/meeting_test.m4a",
            isPlayerReady = true,
            isPlayerPlaying = false,
            playerCurrentMs = 1_500L,
            playerDurationMs = 3_600_000L,
            wordTimestamps = transcriptWords
        )

        // Pre-existing fields intact
        assertEquals("abc123", state.meetingId)
        assertEquals("Riunione Q1", state.title)
        assertEquals(MeetingStatus.COMPLETED, state.status)
        assertEquals("Sintesi breve.", state.overview)
        assertEquals(2, state.keywords.size)
        assertEquals(2, state.bulletNotes.size)

        // New fields present and correct
        assertEquals("/data/app/recordings/meeting_test.m4a", state.audioFilePath)
        assertTrue(state.isPlayerReady)
        assertFalse(state.isPlayerPlaying)
        assertEquals(1_500L, state.playerCurrentMs)
        assertEquals(5, state.wordTimestamps.size)
    }

    @Test
    fun `default TranscriptUiState has empty player fields (backward-compat)`() {
        val state = TranscriptUiState()
        assertEquals("", state.audioFilePath)
        assertFalse(state.isPlayerReady)
        assertFalse(state.isPlayerPlaying)
        assertEquals(0L, state.playerCurrentMs)
        assertEquals(0L, state.playerDurationMs)
        assertTrue(state.wordTimestamps.isEmpty())
    }
}

/*
 * ═══════════════════════════════════════════════════════════════════════════
 *  NOTE: Tests NOT covered here (require Android runtime)
 * ═══════════════════════════════════════════════════════════════════════════
 *
 *  The following behaviors need Robolectric (unit) or on-device
 *  instrumentation tests to be fully verified:
 *
 *  1. CLIPBOARD (Feature 1):
 *     - ClipboardManager.setPrimaryClip() actually called
 *     - Verify using: ShadowClipboardManager (Robolectric) or
 *       Espresso + UiAutomator on-device
 *
 *  2. MEDIA PLAYER (Feature 2):
 *     - MediaPlayer.prepare() and .start()/.pause() lifecycle
 *     - setOnCompletionListener triggers isPlayerPlaying = false
 *     - Verify using: Robolectric ShadowMediaPlayer
 *
 *  3. COMPOSABLE RENDERING:
 *     - AudioPlayerCard renders Slider and play/pause icon correctly
 *     - KaraokeText highlights the correct word at a given currentMs
 *     - Scaletta row responds to click → calls onSeekTo
 *     - CopyButton transitions to "Copiato!" text after click
 *     - Verify using: Compose UI testing with composeTestRule
 *
 * ═══════════════════════════════════════════════════════════════════════════
 */
