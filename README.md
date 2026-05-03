<div align="center">

# 🎙️ Voxlog — AI Voice Noter (Android)
**Registratore e trascrittore intelligente basato su Intelligenza Artificiale.**
Trasforma i tuoi audio, note vocali e lezioni universitarie in verbali, riassunti strutturati e liste di cose da fare in pochi secondi!

<br>

[![Scarica l'App](https://img.shields.io/badge/📥_SCARICA_L'APP_QUI_(APK)-Verde_Smeraldo?style=for-the-badge&color=00D28A)](https://github.com/MatteoScorte/android_AI_voice_noter/releases/latest/download/AI_Voice_Noter_v1.0.apk)
[![Aggiorna l'App](https://img.shields.io/badge/🔄_AGGIORNA_L'APP_(Se_già_installata)-Blu_Oceano?style=for-the-badge&color=007BFF)](https://github.com/MatteoScorte/android_AI_voice_noter/releases/latest/download/AI_Voice_Noter_v1.0.apk)

<br>

**📅 Ultimo Aggiornamento:** 3 Maggio 2026 *(v1.1.0)*

*Clicca uno dei pulsanti qui sopra per scaricare la primissima volta o per aggiornare l'applicazione!*

</div>

---

## 📲 Come scaricare, installare o aggiornare l'app (Per tutti)

Se non sei un programmatore e vuoi solo usare l'applicazione sul tuo telefono Android, segui questi semplici passaggi:

### 🔄 Hai già l'App e vuoi scaricare l'ultimo aggiornamento?
Su Android, "aggiornare" un'app scaricata da GitHub è identico a scaricarla per la prima volta:
1. Clicca sul pulsante blu **"🔄 AGGIORNA L'APP"** qui in alto.
2. Apri il file `.apk` scaricato. Il telefono ti avviserà: *"Vuoi installare un aggiornamento per questa applicazione esistente? I dati non andranno persi"*.
3. Clicca su **Aggiorna**. Avrai le ultime fantastiche funzioni e manterrai automaticamente tutte le tue trascrizioni, account e categorie salvate!

---

### Passo 1: Installare l'app per la prima volta
1. Clicca sul grande pulsante verde **"📥 SCARICA L'APP QUI (APK)"** che si trova in cima a questa pagina, oppure clicca [qui](https://github.com/MatteoScorte/android_AI_voice_noter/releases/latest/download/AI_Voice_Noter_v1.0.apk).
2. Il tuo telefono inizierà automaticamente a scaricare il file `.apk`.

### Passo 2: Installare l'app sul telefono
Quando apri il file `.apk` appena scaricato, il tuo telefono Android ti avviserà che stai per installare un'app "Sconosciuta" (solo perché non viene scaricata dal Google Play Store). È una procedura di sicurezza standard:

1. Premi sul file scaricato per aprirlo.
2. Ti verrà mostrato un avviso di sicurezza. Fai tap su **Impostazioni**.
3. Trova l'interruttore vicino a **"Consenti da questa fonte"** (o "Installa app sconosciute") e **attivalo**.
4. Torna indietro e premi **Installa**.
5. Finito! Ora troverai **Voxlog** nella tua schermata principale come qualsiasi altra app. 🎉

---

## 🌟 Funzionalità Principali

- 🚀 **Trascrizione Rapida di Livello Superiore**: Integra **Deepgram (Modello Nova-2)** per una trascrizione dell'audio estremamente veloce ed accurata.
- 👥 **Diarizzazione Intelligente e Rinomina Speaker**: Distingue automaticamente chi parla (Speaker 0, Speaker 1...) e permette di rinominare i partecipanti in tempo reale, propagando il nome lungo tutto il verbale.
- 🤖 **Analisi Strutturata tramite LLM**: Invia la trascrizione a un modello LLM (via **OpenRouter**) per ottenere Keywords, Riassunto, Punti Chiave e Action Items.
- 🧠 **Categorie Prompt AI personalizzabili**: Crea e gestisci prompt di sistema personalizzati per diversi tipi di analisi (lezioni, riunioni, brainstorming...).
- 💬 **Navigazione a 3 tab**: Chat, Rec (registrazione) e History — con swipe gesture fluido tra le sezioni.
- 📥 **Inbox**: Importa file audio esterni (WhatsApp, registratori, etc.) per processarli con le stesse funzionalità AI.
- ☁️ **Cloud Sync su Supabase**: Sincronizza meeting, appunti e verbali su un database Cloud scalabile.
- 🎶 **Player Audio integrato**: Ascolta le registrazioni direttamente in app con slider temporale e controlli Play/Pause.
- 🗂️ **Cartelle di organizzazione**: Organizza le registrazioni in cartelle colorate con gestione completa (crea, rinomina, elimina).
- 🎨 **Design Immersivo "Firefly"**: Costruito con **Jetpack Compose** e Material 3. Tema dark elegante con animazioni fluide.

---

## 🛠 Sviluppo & Stack Tecnologico

- **Linguaggio**: 100% Kotlin
- **Architettura**: MVVM con StateFlow e Coroutines
- **UI**: Jetpack Compose + Material 3
- **Trascrizione**: Deepgram Nova-2 (REST API)
- **AI Analysis**: OpenRouter (modelli LLM configurabili)
- **Cloud**: Supabase (REST + Storage)
- **Local DB**: Room + DataStore
- **Audio**: MediaRecorder + MediaPlayer foreground services

---

> ⚠️ **Nota per gli Sviluppatori:**
> Le chiavi API di **Deepgram** e **OpenRouter** vanno inserite nelle Impostazioni dell'app dopo l'installazione. Non sono incluse nel codice sorgente per motivi di sicurezza.
