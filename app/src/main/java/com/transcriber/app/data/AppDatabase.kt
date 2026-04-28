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
Crea una presentazione Canva per studio e ripasso universitario.
Stile: accademico, chiaro, ordinato. Sfondo scuro o neutro.
Struttura:
- Slide 1: titolo della lezione + materia
- Slide 2-3: contesto e obiettivi
- Slide 4-8: concetti chiave (uno per slide, con definizione e bullet points)
- Slide 9: esempi pratici o casi studio
- Slide 10: domande di ripasso
Regole: max 5 righe per slide, usa bullet points, evidenzia i termini tecnici in colore diverso.
                """.trimIndent()
            ),

            CanvaSkillEntity(
                name = "Presentazione Cliente",
                emoji = "💼",
                colorHex = "#49DD7F",
                outputType = "Slide",
                isDefault = true,
                agentPrompt = """
Crea una presentazione professionale da mostrare a un cliente.
Stile: corporate, pulito, elegante. Palette colori coerente e professionale.
Struttura:
- Slide 1: titolo + sottotitolo + logo placeholder
- Slide 2: executive summary (3 punti chiave)
- Slide 3-4: contesto e problema affrontato
- Slide 5-7: soluzione proposta con dettagli
- Slide 8: risultati attesi o già ottenuti
- Slide 9: timeline / prossimi step
- Slide 10: contatti e call to action
Regole: tono formale, dati e numeri dove possibile, max 4 righe per slide.
                """.trimIndent()
            ),

            CanvaSkillEntity(
                name = "Locandina Evento",
                emoji = "🎨",
                colorHex = "#FF8A65",
                outputType = "Locandina",
                isDefault = true,
                agentPrompt = """
Crea una locandina verticale (formato A4 o Story) per un evento.
Stile: visivo, d'impatto, moderno. Usa colori vivaci e tipografia grande.
Elementi obbligatori:
- Titolo dell'evento (grande, in evidenza)
- Data, ora e luogo
- Breve descrizione (max 2 righe)
- Nome dell'organizzatore o brand
- Eventuale QR code o link placeholder
Regole: priorità all'impatto visivo, testo minimo ma essenziale, usa immagini o sfondi grafici.
                """.trimIndent()
            ),

            CanvaSkillEntity(
                name = "Infografica",
                emoji = "📊",
                colorHex = "#B39DDB",
                outputType = "Infografica",
                isDefault = true,
                agentPrompt = """
Crea un'infografica verticale che sintetizza visivamente le informazioni principali.
Stile: colorato, iconografico, facile da leggere a colpo d'occhio.
Struttura:
- Titolo in cima (grande e chiaro)
- 4-6 sezioni con icona + titoletto + 1-2 righe di testo
- Dati numerici evidenziati graficamente (cerchi, barre, frecce)
- Fonte o firma in fondo
Regole: zero muri di testo, ogni sezione deve essere autonoma, usa icone per rappresentare ogni concetto.
                """.trimIndent()
            ),

            CanvaSkillEntity(
                name = "Post Social",
                emoji = "📱",
                colorHex = "#FFD54F",
                outputType = "Social",
                isDefault = true,
                agentPrompt = """
Crea un set di post per social media (formato quadrato 1:1 o verticale 4:5).
Stile: moderno, scroll-stopping, adatto a Instagram/LinkedIn.
Per ogni post:
- Titolo o hook forte nella prima riga
- Max 3-4 punti chiave ben spaziati
- Call to action finale (es. "Scopri di più", "Salva questo post")
- Palette colori coerente tra tutti i post del set
Regole: testo grande e leggibile, contrasto alto, usa emoji con parsimonia.
                """.trimIndent()
            )
        )
    }
}
