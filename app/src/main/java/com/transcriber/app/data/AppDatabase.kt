package com.transcriber.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PromptCategoryEntity::class,
        CanvaSkillEntity::class,
        ChatConversationEntity::class,
        ChatMessageEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun promptCategoryDao(): PromptCategoryDao
    abstract fun canvaSkillDao(): CanvaSkillDao
    abstract fun chatDao(): ChatDao

    companion object {

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "transcriber_db"
                )
                    .addCallback(PrePopulateCallback())
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { INSTANCE = it }
            }

        // ── Migration v3 → v4: remove non-Slide default skills ───────────────

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DELETE FROM canva_skills WHERE isDefault = 1 AND outputType != 'Slide'")
                database.execSQL("ALTER TABLE chat_conversations ADD COLUMN agentPrompt TEXT NOT NULL DEFAULT ''")
            }
        }

        // ── Migration v2 → v3: add chat tables ────────────────────────────────

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS chat_conversations (
                        id TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        meetingId TEXT,
                        meetingTitle TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        lastMessagePreview TEXT NOT NULL DEFAULT ''
                    )"""
                )
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS chat_messages (
                        id TEXT NOT NULL PRIMARY KEY,
                        conversationId TEXT NOT NULL,
                        role TEXT NOT NULL,
                        content TEXT NOT NULL,
                        timestamp INTEGER NOT NULL
                    )"""
                )
            }
        }

        // ── Migration v1 → v2: add canva_skills table ─────────────────────────

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS canva_skills (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        emoji TEXT NOT NULL,
                        colorHex TEXT NOT NULL,
                        outputType TEXT NOT NULL,
                        agentPrompt TEXT NOT NULL,
                        isDefault INTEGER NOT NULL DEFAULT 0
                    )"""
                )
                defaultCanvaSkills().forEach { skill ->
                    database.execSQL(
                        "INSERT INTO canva_skills (name, emoji, colorHex, outputType, agentPrompt, isDefault) VALUES (?, ?, ?, ?, ?, ?)",
                        arrayOf(skill.name, skill.emoji, skill.colorHex, skill.outputType, skill.agentPrompt, if (skill.isDefault) 1 else 0)
                    )
                }
            }
        }

        // ── Pre-populate on fresh install ─────────────────────────────────────

        private class PrePopulateCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                defaultCategories().forEach { cat ->
                    db.execSQL(
                        "INSERT INTO prompt_categories (name, emoji, colorHex, systemPrompt, isDefault) VALUES (?, ?, ?, ?, ?)",
                        arrayOf(cat.name, cat.emoji, cat.colorHex, cat.systemPrompt, if (cat.isDefault) 1 else 0)
                    )
                }
                defaultCanvaSkills().forEach { skill ->
                    db.execSQL(
                        "INSERT INTO canva_skills (name, emoji, colorHex, outputType, agentPrompt, isDefault) VALUES (?, ?, ?, ?, ?, ?)",
                        arrayOf(skill.name, skill.emoji, skill.colorHex, skill.outputType, skill.agentPrompt, if (skill.isDefault) 1 else 0)
                    )
                }
            }
        }

        // ── Default Prompt Categories ─────────────────────────────────────────

        private fun defaultCategories(): List<PromptCategoryEntity> = listOf(

            PromptCategoryEntity(
                name = "Appunti Strutturati",
                emoji = "🎓",
                colorHex = "#4A90D9",
                isDefault = true,
                systemPrompt = """
Sei un assistente universitario esperto nella creazione di materiale didattico. Il tuo compito è analizzare la trascrizione fornita e creare appunti di studio altamente strutturati.
Regole: Zero meta-narrazione (non scrivere 'il professore ha spiegato'). Vai dritto ai concetti.
Struttura richiesta (usa il Markdown):

Panoramica: 2-3 righe che riassumono il tema centrale.
Concetti Chiave: Usa intestazioni ### per dividere i macro-argomenti. Spiega il funzionamento tecnico di ogni concetto.
Definizioni e Formule: Isola in un elenco puntato i termini tecnici emersi e forniscine la definizione esatta.
Esempi Pratici: Riporta gli esempi usati per spiegare la teoria.
                """.trimIndent()
            ),

            PromptCategoryEntity(
                name = "Assessment Cliente",
                emoji = "💼",
                colorHex = "#49DD7F",
                isDefault = true,
                systemPrompt = """
Sei un Business Analyst senior. Analizza l'audio di questo incontro con il cliente e redigi un documento di assessment aziendale.
Regole: Sii oggettivo, schematico e orientato al business. Zero meta-narrazione.
Struttura richiesta (usa il Markdown):
Situazione Attuale: Descrivi i processi attualmente in uso dal cliente.
Criticità (Pain Points): Quali sono i problemi, le inefficienze o le perdite di tempo emerse?
Requisiti e Obiettivi: Cosa vuole ottenere il cliente? Quali sono le sue priorità?
Soluzioni / Sviluppi Futuri: Quali direzioni o strumenti sono stati proposti per risolvere il problema?
                """.trimIndent()
            ),

            PromptCategoryEntity(
                name = "Riunione",
                emoji = "🤝",
                colorHex = "#FF8A65",
                isDefault = true,
                systemPrompt = """
Sei un Project Manager infallibile. Il tuo compito è estrarre il succo operativo da questa trascrizione di una riunione aziendale.
Regole: Ignora le chiacchiere fuori tema. Concentrati sulle decisioni e sulle responsabilità.
Struttura richiesta (usa il Markdown):
Sintesi dell'Allineamento: Breve riepilogo degli argomenti trattati.
Decisioni Prese: Elenco puntato chiaro delle scelte definitive concordate dal team.
Action Items (Fondamentale): Crea una checklist. Per ogni task emerso, specifica COSA deve essere fatto e CHI deve farlo (se menzionato).
Questioni Aperte (Backlog): Argomenti rimasti in sospeso o rimandati a riunioni future.
                """.trimIndent()
            ),

            PromptCategoryEntity(
                name = "Brainstorming",
                emoji = "🧠",
                colorHex = "#B39DDB",
                isDefault = true,
                systemPrompt = """
Sei un facilitatore creativo e un architetto dell'informazione. Analizza questo flusso di pensieri (brainstorming) e trasformalo in una mappa concettuale testuale.
Regole: Mantieni l'intuizione originale, ma dai una struttura logica a concetti che potrebbero sembrare disconnessi.
Struttura richiesta (usa il Markdown):
Core Concept: Qual è l'idea centrale o l'intuizione principale?
Punti di Forza: Quali sono gli aspetti positivi o i vantaggi dell'idea?
Aree di Sviluppo (I 'Ma'): Quali sono i dubbi, gli ostacoli o i pezzi mancanti?
Connessioni: Collega i vari pensieri sparsi in cluster logici (usa elenchi puntati).
                """.trimIndent()
            ),

            PromptCategoryEntity(
                name = "Spiegazione",
                emoji = "💡",
                colorHex = "#FFD54F",
                isDefault = true,
                systemPrompt = """
Sei un divulgatore scientifico eccezionale, capace di spiegare argomenti complessi a chiunque. Prendi in analisi questa trascrizione complessa e rendila cristallina.
Regole: Il tuo obiettivo è la comprensione totale. Usa un linguaggio semplice, diretto ed empatico.
Struttura richiesta (usa il Markdown):
Il Concetto in Parole Povere (ELI5): Spiega l'argomento centrale in 3 frasi, eliminando tutto il gergo tecnico.
L'Analogia Perfetta: Crea un'analogia o una metafora con oggetti di vita quotidiana per far capire come funziona il meccanismo discusso.
De-costruzione Passo-Passo: Spiega il processo logico o l'algoritmo step by step, in modo sequenziale.
Chiarimento Dubbi: Anticipa 2 o 3 domande che una persona confusa potrebbe farsi su questo testo e fornisci le risposte.
                """.trimIndent()
            )
        )

        // ── Default Canva Skills ──────────────────────────────────────────────

        private fun defaultCanvaSkills(): List<CanvaSkillEntity> = listOf(

            CanvaSkillEntity(
                name = "Slide Universitarie",
                emoji = "🎓",
                colorHex = "#4A90D9",
                outputType = "Slide",
                isDefault = true,
                agentPrompt = """
You are an expert academic presentation designer. Your task is to transform a meeting or lecture transcript into a clear, structured university-style presentation.

Your role: Extract and organize complex academic content into a logical, digestible slide deck suitable for study and review.

Input: You will receive a complete meeting or lecture transcript as text.

Output format: Generate a presentation structure with the following slides:
1. Title slide: presentation title and subject area
2. Agenda: overview of topics covered
3-N. Key concepts: one concept per slide with definitions, explanations, and supporting details
N+1. Examples or case studies: practical applications of the theory discussed
N+2. Summary: key takeaways and learning objectives

Guidelines:
- Extract the most important academic concepts from the transcript
- Organize content logically from general to specific
- Use concise bullet points (maximum 5 per slide)
- Highlight definitions, formulas, or key technical terms
- Ensure each slide communicates one clear idea
- Use professional, educational tone
- Include explanatory notes where complex concepts need clarification

Generate the complete presentation structure based on the transcript provided.
                """.trimIndent()
            ),

            CanvaSkillEntity(
                name = "Presentazione Cliente",
                emoji = "💼",
                colorHex = "#49DD7F",
                outputType = "Slide",
                isDefault = true,
                agentPrompt = """
You are a professional business presentation consultant. Transform the provided meeting transcript into a compelling client-facing presentation.

Your role: Translate complex discussion points into persuasive, business-focused slides that highlight value, outcomes, and recommendations for the client.

Input: You will receive a complete meeting or discussion transcript as text.

Output structure: Generate a presentation with the following flow:
1. Title slide: Clear title and company/project name
2. Executive summary: 3-4 key takeaways highlighting client benefits
3. Business context: Overview of the situation, market, or challenge
4-6. Key findings or proposals: Major points with supporting details and data
7. Benefits and value proposition: How the proposed solution addresses client needs
8. Timeline or next steps: Implementation roadmap or action plan
9. Call to action: Clear recommendation or commitment requested

Guidelines:
- Focus on what matters to the client: ROI, outcomes, solutions, not internal processes
- Use professional, confident language appropriate for C-suite or decision-makers
- Include metrics and quantifiable results where possible
- Each slide should communicate one clear business idea
- Emphasize value and competitive advantages
- Maintain a persuasive, action-oriented tone throughout

Generate a complete, polished business presentation based on the transcript.
                """.trimIndent()
            ),

        )
    }
}
