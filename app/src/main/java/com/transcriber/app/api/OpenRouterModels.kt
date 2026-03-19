package com.transcriber.app.api

data class LlmModel(
    val id: String,
    val displayName: String,
    val description: String
)

val AVAILABLE_MODELS = listOf(
    LlmModel("google/gemini-2.0-flash-001", "Gemini 2.0 Flash", "Veloce e preciso, ottimo rapporto qualità/prezzo"),
    LlmModel("anthropic/claude-3.5-sonnet", "Claude 3.5 Sonnet", "Eccellente per testi lunghi e analisi dettagliata"),
    LlmModel("openai/gpt-4o", "GPT-4o", "Modello premium, massima qualità"),
    LlmModel("openai/gpt-4o-mini", "GPT-4o Mini", "Economico ma capace"),
    LlmModel("meta-llama/llama-3.1-70b-instruct", "Llama 3.1 70B", "Open-source, buone prestazioni"),
    LlmModel("google/gemini-1.5-pro", "Gemini 1.5 Pro", "Context window enorme, ideale per trascrizioni lunghe")
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
