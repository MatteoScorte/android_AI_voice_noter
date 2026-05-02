package com.transcriber.app.api

data class LlmModel(
    val id: String,
    val displayName: String,
    val description: String
)

val AVAILABLE_MODELS = listOf(
    // ── Veloce / Economico ────────────────────────────────────────────────────
    LlmModel("openai/gpt-4o-mini",           "GPT-4o Mini",          "Economico e veloce, ideale per chat semplici"),
    LlmModel("anthropic/claude-haiku-4-5",    "Claude Haiku 4.5",     "Il più veloce di Anthropic, costo minimo"),
    LlmModel("google/gemini-2.0-flash-001",  "Gemini 2.0 Flash",     "Veloce, ottimo rapporto qualità/prezzo"),

    // ── Bilanciato ────────────────────────────────────────────────────────────
    LlmModel("anthropic/claude-sonnet-4-5",   "Claude Sonnet 4.5",    "Ottimo equilibrio velocità/qualità"),
    LlmModel("openai/gpt-4o",                "GPT-4o",               "Modello bilanciato di OpenAI"),
    LlmModel("meta-llama/llama-3.3-70b-instruct", "Llama 3.3 70B",  "Open-source, eccellente per l'italiano"),

    // ── Avanzato ──────────────────────────────────────────────────────────────
    LlmModel("anthropic/claude-opus-4",      "Claude Opus 4",        "Il più capace di Anthropic, per chat difficili"),
    LlmModel("openai/gpt-4-turbo",           "GPT-4 Turbo",          "Alta qualità, context window da 128k token"),
    LlmModel("google/gemini-2.5-pro-preview","Gemini 2.5 Pro",       "Context window enorme, ideale per trascrizioni lunghe"),

    // ── Ragionamento ──────────────────────────────────────────────────────────
    LlmModel("deepseek/deepseek-r1",         "DeepSeek R1",          "Ragionamento avanzato, eccellente per analisi"),
    LlmModel("openai/o3-mini",               "OpenAI o3 Mini",       "Modello di ragionamento compatto di OpenAI"),
)

data class ChatMessage(val role: String, val content: String)

data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.3,
    val max_tokens: Int = 4096
)

data class ChatResponse(val id: String?, val choices: List<Choice>?)
data class Choice(val message: ChatMessage?)
