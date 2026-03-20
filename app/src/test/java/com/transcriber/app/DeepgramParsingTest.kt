package com.transcriber.app

import com.google.gson.Gson
import com.transcriber.app.api.DeepgramResponse
import com.transcriber.app.data.WordTimestamp
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests that the Deepgram JSON response (as returned by the real API) is parsed
 * correctly, specifically that word-level timestamps are extracted and mapped to
 * [WordTimestamp] objects.
 *
 * Uses a realistic mock JSON structure matching the Deepgram nova-2 response shape.
 */
class DeepgramParsingTest {

    private lateinit var gson: Gson

    // ── Minimal valid Deepgram JSON with words=true ───────────────────────────
    // Mirrors what Deepgram nova-2 returns for "smart_format=true&words=true&diarize=true"
    private val mockDeepgramJson = """
    {
      "results": {
        "channels": [
          {
            "alternatives": [
              {
                "transcript": "Ciao mondo come stai oggi",
                "confidence": 0.98,
                "words": [
                  { "word": "ciao",    "punctuated_word": "Ciao",   "start": 0.0,  "end": 0.48, "speaker": 0 },
                  { "word": "mondo",   "punctuated_word": "mondo",  "start": 0.6,  "end": 1.1,  "speaker": 0 },
                  { "word": "come",    "punctuated_word": "come",   "start": 1.2,  "end": 1.5,  "speaker": 0 },
                  { "word": "stai",    "punctuated_word": "stai",   "start": 1.6,  "end": 2.0,  "speaker": 0 },
                  { "word": "oggi",    "punctuated_word": "oggi?",  "start": 2.1,  "end": 2.6,  "speaker": 1 }
                ],
                "paragraphs": null
              }
            ]
          }
        ]
      }
    }
    """.trimIndent()

    // ── Deepgram JSON with paragraphs (smart_format primary path) ────────────
    private val mockDeepgramJsonWithParagraphs = """
    {
      "results": {
        "channels": [
          {
            "alternatives": [
              {
                "transcript": "Buongiorno a tutti",
                "words": [
                  { "word": "buongiorno", "punctuated_word": "Buongiorno", "start": 0.1, "end": 0.9,  "speaker": 0 },
                  { "word": "a",          "punctuated_word": "a",          "start": 1.0, "end": 1.1,  "speaker": 0 },
                  { "word": "tutti",      "punctuated_word": "tutti.",     "start": 1.2, "end": 1.8,  "speaker": 0 }
                ],
                "paragraphs": {
                  "transcript": "Buongiorno a tutti.",
                  "paragraphs": [
                    {
                      "sentences": [{ "text": "Buongiorno a tutti.", "start": 0.1, "end": 1.8 }],
                      "speaker": 0,
                      "start": 0.1,
                      "end": 1.8,
                      "num_words": 3
                    }
                  ]
                }
              }
            ]
          }
        ]
      }
    }
    """.trimIndent()

    // ── Deepgram JSON with missing start/end on some words ───────────────────
    private val mockJsonWithPartialTimestamps = """
    {
      "results": {
        "channels": [
          {
            "alternatives": [
              {
                "transcript": "test",
                "words": [
                  { "word": "con",    "punctuated_word": "con",   "start": 0.0, "end": 0.3, "speaker": 0 },
                  { "word": "senza",  "punctuated_word": "senza"                              },
                  { "word": "tempo",  "punctuated_word": "tempo.", "start": 1.0, "end": 1.5, "speaker": 0 }
                ],
                "paragraphs": null
              }
            ]
          }
        ]
      }
    }
    """.trimIndent()

    @Before
    fun setUp() {
        gson = Gson()
    }

    // ── Word count ────────────────────────────────────────────────────────────

    @Test
    fun `parses 5 words from mock response`() {
        val dgResponse = gson.fromJson(mockDeepgramJson, DeepgramResponse::class.java)
        val words = dgResponse.results?.channels?.firstOrNull()?.alternatives?.firstOrNull()?.words
        assertEquals(5, words?.size)
    }

    // ── Timestamp extraction ──────────────────────────────────────────────────

    @Test
    fun `first word start timestamp is 0_0`() {
        val dgResponse = gson.fromJson(mockDeepgramJson, DeepgramResponse::class.java)
        val firstWord = dgResponse.results!!.channels!!.first().alternatives!!.first().words!!.first()
        assertEquals(0.0, firstWord.start!!, 0.001)
    }

    @Test
    fun `first word end timestamp is 0_48`() {
        val dgResponse = gson.fromJson(mockDeepgramJson, DeepgramResponse::class.java)
        val firstWord = dgResponse.results!!.channels!!.first().alternatives!!.first().words!!.first()
        assertEquals(0.48, firstWord.end!!, 0.001)
    }

    @Test
    fun `last word start timestamp is 2_1`() {
        val dgResponse = gson.fromJson(mockDeepgramJson, DeepgramResponse::class.java)
        val words = dgResponse.results!!.channels!!.first().alternatives!!.first().words!!
        assertEquals(2.1, words.last().start!!, 0.001)
    }

    // ── WordTimestamp mapping ─────────────────────────────────────────────────

    @Test
    fun `WordTimestamp list has correct size after filtering nulls`() {
        val dgResponse = gson.fromJson(mockDeepgramJson, DeepgramResponse::class.java)
        val alternative = dgResponse.results!!.channels!!.first().alternatives!!.first()
        val wordTimestamps: List<WordTimestamp> = alternative.words!!.mapNotNull { w ->
            val s = w.start ?: return@mapNotNull null
            val e = w.end   ?: return@mapNotNull null
            WordTimestamp(word = w.punctuated_word ?: w.word, start = s, end = e)
        }
        assertEquals(5, wordTimestamps.size)
    }

    @Test
    fun `WordTimestamp uses punctuated_word when available`() {
        val dgResponse = gson.fromJson(mockDeepgramJson, DeepgramResponse::class.java)
        val alternative = dgResponse.results!!.channels!!.first().alternatives!!.first()
        val wordTimestamps = alternative.words!!.mapNotNull { w ->
            val s = w.start ?: return@mapNotNull null
            val e = w.end   ?: return@mapNotNull null
            WordTimestamp(word = w.punctuated_word ?: w.word, start = s, end = e)
        }
        // "ciao" has punctuated_word = "Ciao"
        assertEquals("Ciao", wordTimestamps.first().word)
        // "oggi" has punctuated_word = "oggi?" (punctuation added by smart_format)
        assertEquals("oggi?", wordTimestamps.last().word)
    }

    @Test
    fun `WordTimestamp start and end match JSON values`() {
        val dgResponse = gson.fromJson(mockDeepgramJson, DeepgramResponse::class.java)
        val alternative = dgResponse.results!!.channels!!.first().alternatives!!.first()
        val wordTimestamps = alternative.words!!.mapNotNull { w ->
            val s = w.start ?: return@mapNotNull null
            val e = w.end   ?: return@mapNotNull null
            WordTimestamp(word = w.punctuated_word ?: w.word, start = s, end = e)
        }
        // Third word: "come" [1.2, 1.5]
        assertEquals(1.2, wordTimestamps[2].start, 0.001)
        assertEquals(1.5, wordTimestamps[2].end,   0.001)
    }

    // ── Robustness: words missing timestamps are dropped ─────────────────────

    @Test
    fun `words without start or end are excluded from WordTimestamp list`() {
        val dgResponse = gson.fromJson(mockJsonWithPartialTimestamps, DeepgramResponse::class.java)
        val alternative = dgResponse.results!!.channels!!.first().alternatives!!.first()
        val wordTimestamps = alternative.words!!.mapNotNull { w ->
            val s = w.start ?: return@mapNotNull null
            val e = w.end   ?: return@mapNotNull null
            WordTimestamp(word = w.punctuated_word ?: w.word, start = s, end = e)
        }
        // "senza" has no timestamps — should be excluded → 2 results only
        assertEquals(2, wordTimestamps.size)
        assertEquals("con",    wordTimestamps[0].word)
        assertEquals("tempo.", wordTimestamps[1].word)
    }

    // ── Paragraphs path also extracts word timestamps ─────────────────────────

    @Test
    fun `word timestamps are still extracted when paragraphs path is used`() {
        val dgResponse = gson.fromJson(mockDeepgramJsonWithParagraphs, DeepgramResponse::class.java)
        val alternative = dgResponse.results!!.channels!!.first().alternatives!!.first()

        // Verify paragraphs are present (primary path)
        assertNotNull(alternative.paragraphs?.paragraphs)
        assertFalse(alternative.paragraphs!!.paragraphs!!.isEmpty())

        // Words timestamps are still available in the same alternative
        val wordTimestamps = alternative.words!!.mapNotNull { w ->
            val s = w.start ?: return@mapNotNull null
            val e = w.end   ?: return@mapNotNull null
            WordTimestamp(word = w.punctuated_word ?: w.word, start = s, end = e)
        }
        assertEquals(3, wordTimestamps.size)
        assertEquals(0.1, wordTimestamps[0].start, 0.001)
        assertEquals(1.8, wordTimestamps[2].end,   0.001)
    }

    // ── Karaoke integration: parsed words feed correctly into findActiveWordIndex

    @Test
    fun `parsed WordTimestamp list feeds correctly into findActiveWordIndex`() {
        val dgResponse = gson.fromJson(mockDeepgramJson, DeepgramResponse::class.java)
        val alternative = dgResponse.results!!.channels!!.first().alternatives!!.first()
        val wordTimestamps = alternative.words!!.mapNotNull { w ->
            val s = w.start ?: return@mapNotNull null
            val e = w.end   ?: return@mapNotNull null
            WordTimestamp(word = w.punctuated_word ?: w.word, start = s, end = e)
        }

        // Import the utility function here for the integration step
        val idx800ms  = com.transcriber.app.util.findActiveWordIndex(wordTimestamps, 800L)
        val idx2300ms = com.transcriber.app.util.findActiveWordIndex(wordTimestamps, 2_300L)

        assertEquals("At 800ms should highlight 'mondo' (index 1)", 1, idx800ms)
        assertEquals("At 2300ms should highlight 'oggi' (index 4)", 4, idx2300ms)
    }
}
