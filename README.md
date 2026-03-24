<div align="center">

# 🎙️ AI Voice Noter (Android)
**Registratore e trascrittore intelligente basato su Intelligenza Artificiale.**
Trasforma i tuoi audio, note vocali e lezioni universitarie in verbali, riassunti strutturati e liste di cose da fare in pochi secondi!

<br>

[![Scarica l'App](https://img.shields.io/badge/📥_SCARICA_L'APP_QUI_(APK)-Verde_Smeraldo?style=for-the-badge&color=00D28A)](https://github.com/MatteoScorte/android_AI_voice_noter/releases/latest/download/AI_Voice_Noter_v1.0.apk)
[![Aggiorna l'App](https://img.shields.io/badge/🔄_AGGIORNA_L'APP_(Se_già_installata)-Blu_Oceano?style=for-the-badge&color=007BFF)](https://github.com/MatteoScorte/android_AI_voice_noter/releases/latest/download/AI_Voice_Noter_v1.0.apk)

<br>

**📅 Ultimo Aggiornamento:** 24 Marzo 2026 *(v1.0.2)*

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
2. Nella nuova pagina che si apre, scorri verso il basso fino alla sezione intitolata **"Assets"**.
3. Clicca sul file che finisce per **`.apk`** (potrebbe chiamarsi `app-release.apk` o simile).
4. Il tuo telefono inizierà automaticamente a scaricare il file.

### Passo 2: Installare l'app sul telefono
Quando apri il file `.apk` appena scaricato, il tuo telefono Android ti avviserà che stai per installare un'app "Sconosciuta" (solo perché non viene scaricata dal Google Play Store). È una procedura di sicurezza standard:

1. Premi sul file scaricato per aprirlo.
2. Ti verrà mostrato un avviso di sicurezza. Fai tap su **Impostazioni**.
3. Trova l'interruttore vicino a **"Consenti da questa fonte"** (o "Installa app sconosciute") e **attivalo**.
4. Torna indietro e premi **Installa**.
5. Finito! Ora troverai **AI Voice Noter** nella tua schermata principale come qualsiasi altra app. 🎉

---

## 🌟 Funzionalità Principali

L'applicazione è progettata per offrire un set di feature estremamente avanzate racchiuse in un'interfaccia utente (UI) elegante e user-friendly:

- 🚀 **Trascrizione Rapida di Livello Superiore**: Integra tecnologie esterne AI top di gamma come **Deepgram (Modello Nova-2)** per una trascrizione dell'audio estremamente veloce ed accurata.
- 👥 **Diarizzazione Intelligente e Rinomina Speaker**: Capisce e distingue in automatico quando parlano persone diverse (Speaker 0, Speaker 1...). Ti permette inoltre di rinominare in tempo reale i partecipanti (es. "Speaker 0" -> "Matteo"), propagando il nome lungo tutto il verbale in maniera ubiqua. 
- 🤖 **Analisi Strutturata tramite LLM (Mappatura Prompts)**: Invia la tua trascrizione a un Modello Linguistico potenziato (via **OpenRouter**) per ottenere magicamente Punti Chiave, Liste di Azioni (Action Items, completi di responsabili e deadline), Paragrafi Riassuntivi e Keywords rilevanti testuali evidenziate nel corpo del testo grezzo.
- 🧠 **Cervello AI (Prompt per Categorie Dinamici)**: Non una semplice trascrizione! Puoi scegliere e creare *diverse lenti di osservazione*. Che sia una formale riunione del team, una lezione universitaria puramente teorica, o un brainstorming creativo, l'app ti permette di configurare dei **Prompt di Sistema personalizzati** salvati nella tua galleria locale (compiuti e indicati da simpatiche Emoji).
- ☁️ **Cloud Sync su Supabase**: Esporta e sincronizza fluidamente tutti i tuoi meeting, appunti testuali e verbali completi tramite un database Cloud scalabile, costruito su integrazioni Native REST per un'elevata flessibilità e accessibilità multipiattaforma.
- 🎶 **Pre-ascolto & Playback Audio in app**: Completa indipendenza. L'app contiene al proprio interno un Player Audio nativo con slider temporale (SeekBar) e comandi di Play/Pause per permetterti di riascoltare l'audio non appena è stato registrato (o importato) prima ancora di processarlo!
- 🎨 **Design Immersivo "Firefly" e Jetpack Compose**: Costruito unicamente con le ultimissime librerie **Jetpack Compose**. Presenta temi cromatici coesi (Deep Blue "DarkSurface", riflessi vividi "AccentGreen"), equalizzatori audio simulati tramite _Canvas fluida e animazioni Custom_ di ultima generazione, Text Selection testuale per agevolarne la copiatura, e BottomSheet nativi material-design-3.

---

## 🛠 Sviluppo & Stack Tecnologico

L'applicazione segue i più stretti canoni e best-practice dell'ingegneria del software su Android:
- **Linguaggio**: 100% Kotlin.
- **Architettura**: **MVVM** (Model-View-ViewModel) robusto implementato senza Single-Point-of-Failure. 
- **Gestore di Stato (State Management)**: Sfruttamento estensivo di `StateFlow` e Coroutines per la gestione e l'elaborazione di complesse chiamate Asincrone (elaborazioni IA, Network requests) senza alcun freeze dell'interfaccia utente principale (UI Thread blocking safe).
- **Network**: Client RESTful sicuri (OkHttp/Retrofit implicit architecture layout) incapsulati per Supabase, Deepgram e OpenRouter.
- **UI Framework**: Google Jetpack Compose, `LazyColumn` virtualizzati per rendering massivi di centinaia di dialoghi testuali senza drop del FrameRate (60fps garantiti).

---

> ⚠️ Nota Bene per gli Sviluppatori:
> Le implementazioni richiedono l'inserimento esplicito ed asincrono delle chiavi API di **Deepgram** e **OpenRouter** all'interno dei file di settings locali (Ignorati da GitHub per ovvi requisiti di Sicurezza Privacy/Tokens). Assicurati di dichiararle se intendi compilare il server autonomamente.
