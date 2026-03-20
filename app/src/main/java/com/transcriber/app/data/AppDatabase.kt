package com.transcriber.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [PromptCategoryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun promptCategoryDao(): PromptCategoryDao

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
                    .build()
                    .also { INSTANCE = it }
            }

        // ── Default categories ────────────────────────────────────────────────

        private class PrePopulateCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                // Use raw SQL so we don't need the DAO (which requires the DB to already exist).
                // Positional binding prevents any SQL injection from prompt text.
                defaultCategories().forEach { cat ->
                    db.execSQL(
                        "INSERT INTO prompt_categories (name, emoji, colorHex, systemPrompt, isDefault) VALUES (?, ?, ?, ?, ?)",
                        arrayOf(cat.name, cat.emoji, cat.colorHex, cat.systemPrompt, if (cat.isDefault) 1 else 0)
                    )
                }
            }
        }

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
    }
}
