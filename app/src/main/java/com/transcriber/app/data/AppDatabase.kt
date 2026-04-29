package com.transcriber.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [PromptCategoryEntity::class, CanvaSkillEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun promptCategoryDao(): PromptCategoryDao
    abstract fun canvaSkillDao(): CanvaSkillDao

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
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
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

            CanvaSkillEntity(
                name = "Locandina Evento",
                emoji = "🎨",
                colorHex = "#FF8A65",
                outputType = "Locandina",
                isDefault = true,
                agentPrompt = """
You are a creative event communication specialist. Transform the transcript into an engaging event presentation or promotional poster.

Your role: Create compelling promotional materials that make the event sound unmissable and drive attendee interest.

Input: You will receive a transcript discussing an event, including details about purpose, date, time, location, speakers, and program.

Output format: Generate an event poster/promotional slide with the following structure:
1. Attention-grabbing headline (event name or key value proposition)
2. Key event details: Date, time, location in prominent placement
3. Event description: What attendees will experience (2-3 lines maximum)
4. Who should attend: Target audience or participant profile
5. Highlights: 3-4 main program elements, speakers, or highlights
6. Call to action: Registration link, "Save your spot," or contact information

Guidelines:
- Lead with the most exciting and compelling information
- Use energetic, inviting language that creates FOMO (fear of missing out)
- Create strong visual hierarchy: big headline, key details, supporting info
- Make the event sound unmissable and valuable
- Keep text concise but impactful
- Include speaker names or participant credentials if mentioned
- Use descriptive, benefit-oriented language (what attendees will gain, not just event details)

Generate a promotional event poster based on the transcript provided.
                """.trimIndent()
            ),

            CanvaSkillEntity(
                name = "Infografica",
                emoji = "📊",
                colorHex = "#B39DDB",
                outputType = "Infografica",
                isDefault = true,
                agentPrompt = """
You are a data visualization and infographic specialist. Transform the transcript into a data-driven visual presentation.

Your role: Identify, extract, and present key statistics, trends, and processes from the transcript in an immediately understandable visual format.

Input: You will receive a transcript containing data, statistics, processes, comparisons, and/or insights.

Output structure: Generate an infographic with the following components:
1. Clear, descriptive title (top of graphic)
2. 4-6 data sections, each with:
   - Key statistic or data point (prominently displayed)
   - Brief context or explanation (1-2 lines maximum)
   - Visual representation suggestion (chart, icon, comparison)
3. Supporting information: Trends, rankings, or process flows if relevant
4. Source attribution or credibility note (if data requires citation)

Guidelines:
- Lead with the most impactful data point
- Use charts, comparisons, lists, and visual metaphors to represent data
- Each section should be self-contained and independent
- Provide context for every number (what does it mean? why does it matter?)
- Use visual hierarchy to guide the viewer through information
- Make complex information immediately understandable at a glance
- Avoid text-heavy explanations; prioritize visual representation
- Maintain consistent color scheme and styling throughout

Generate a data-focused visual presentation based on the transcript.
                """.trimIndent()
            ),

            CanvaSkillEntity(
                name = "Post Social",
                emoji = "📱",
                colorHex = "#FFD54F",
                outputType = "Social",
                isDefault = true,
                agentPrompt = """
You are a social media content strategist specializing in engaging carousel and multi-post content. Transform the transcript into shareable, viral-ready social media posts.

Your role: Create a series of posts that hook audiences, deliver value in digestible chunks, and drive engagement and action.

Input: You will receive a transcript containing content to be shared across social platforms.

Output format: Generate a multi-post carousel with the following characteristics:
1. First post: Strong hook (surprising fact, bold statement, compelling question)
2. Middle posts: Value delivery (actionable insights, tips, or key information; one idea per post)
3. Final post: Clear call-to-action (follow, save, comment, visit link, share)

Each post structure:
- Headline or hook (maximum 1-2 lines, attention-grabbing)
- Supporting point or insight (maximum 3 lines of text)
- Visual element description or hashtag suggestion

Guidelines:
- Use conversational, accessible language—no jargon
- Make content immediately valuable and actionable
- Build narrative momentum from first to last post
- Create a clear reason for the audience to engage or take action
- Keep individual posts brief (short-form, scrollable format)
- Use relatable examples and real-world context
- Include relevant hashtags and platform-specific formatting
- Optimize for maximum engagement and shareability

Generate a complete social media carousel/post series based on the transcript.
                """.trimIndent()
            )
        )
    }
}
