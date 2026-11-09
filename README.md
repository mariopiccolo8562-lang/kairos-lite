# Kairos Lite

App IPTV leggera in stile Smarters per **Fire TV, Android TV, Google TV e telefoni**, anche vecchi (da Android 5.1: Fire TV Stick di 1ª e 2ª generazione comprese).
Si installa **accanto** a KAIROS TV e usa lo **stesso pannello Kairos**: stessi clienti, stesso codice di attivazione `KX..-....`.

## Cosa fa

- Home con tre riquadri (TV in diretta, Film, Serie TV) e «Continua a guardare».
- TV in diretta a tre colonne (categorie · canali · anteprima con guida adesso/dopo): **OK una volta** il canale parte nel riquadro, **OK di nuovo** si apre a schermo intero; cambio canale con ▲▼ e con i numeri.
- Film e serie con stagioni ed episodi, posizione di visione salvata, episodio successivo automatico.
- Ricerca unica, preferiti, profili (anche bambini).
- **Modalità leggera** automatica sulle chiavette con meno di ~1,8 GB di RAM: niente animazioni, immagini e buffer più piccoli.
- **Leggerissima** (Impostazioni → Modalità leggera) per le chiavette più datate: liste canali senza loghi, locandine e sfondi delle schede più leggeri, canali nella versione HD quando c'è.
- **Aggiornamenti automatici**: a ogni apertura (e ogni ora) l'app controlla le release e propone da sola la versione nuova.
- **Liste a tappe**: prima i canali (TV subito usabile), poi film e serie.
- Liste salvate in un archivio sul dispositivo e lette a pezzi: veloce anche con 100.000 titoli.

Dal pannello funzionano: attivazione e scadenza, playlist M3U/Xtream (unite o separate), cambio DNS, *Disconnetti*, limite di visioni contemporanee, permessi, profili, collegamento di altre TV con codice a 6 cifre, messaggi, VPN (*Consentita* / *Sempre attiva*), *Errori applicazione*. In *Utenti online* la versione compare come `lite 1.0.0`.

Non ci sono, di proposito: multischermo, giochi, temi e add-on, assistente IA, registrazioni, speed test, trailer, lettore VLC.

## Qualità dell'immagine (versioni dello stesso canale 4K / FHD / HD / SD)

Con la linea buona la qualità non si tocca, **anche sulle chiavette da 1 GB** (regole in `QualityPolicy.kt`, provate in `QualityPolicyTest`):

- **Il canale parte nella versione scelta.** Il dispositivo toglie solo il 4K, che non mostra: le chiavette deboli non lo decodificano e sugli schermi non 4K si parte dal FHD, che sembra uguale. Prima la chiavetta da 1 GB faceva partire tutto al massimo in HD (o in SD) anche con internet buono.
- **La linea abbassa la qualità solo con prove recenti:** blocchi su almeno 2 canali nell'ultima mezz'ora (solo in memoria) oppure banda misurata da meno di 2 ore sotto i 4 Mbps (SD solo sotto i 2,5 Mbps). Prima un giudizio valeva 12 ore e una banda vecchia poteva far partire tutto in SD per sempre.
- **Immagine «a scatti» con l'audio che va** (meno del 35% dei fotogrammi per 6 s): ricarica, versione più leggera, formato alternativo; sulle chiavette da 1 GB niente decodifica software oltre la SD. Se succede su 2 canali diversi in 7 giorni (decodifica hardware), i canali partono un gradino sotto per 7 giorni, poi si riprova.
- **Risalita** dopo 6 minuti senza blocchi verso la versione piena per il dispositivo. «Leggerissima»: canali in HD, come prima.
- Il dispositivo debole tiene solo i buffer più piccoli (memoria); i flussi HLS a più qualità seguono la linea, non la chiavetta.
- Anteprima nella lista canali: i canali FHD e 4K in HD se la playlist ce l'ha (prima l'SD).

## Adulti fuori dalla Home

Voce `adult` del controllo remoto (spunta del pannello «Nascondi gli adulti dalla Home»), uguale in tutte le app Kairos:
- Diversa da `on`, anche assente o vuota: nella Home (tre riquadri e «Continua a guardare») non compare niente per adulti. Da «Continua a guardare» escono i film e le serie delle categorie per adulti o nascoste e quelli con un nome per adulti; se resta vuota non si vede.
- Negli elenchi (Film, Serie TV, TV in diretta) le categorie per adulti vanno in fondo, come quelle nascoste, e restano raggiungibili. Ricerca, guida TV e zapping non cambiano; il PIN del controllo genitori resta com'è.
- `on`: tutto come prima, vale solo la lista «Categorie nascoste dalla Home» (`home.hide`). Quella lista vale sempre, in aggiunta al riconoscimento automatico.

Riconoscimento in `AdultFilter.kt` (normalizzazione NFKD e le due espressioni del contratto comune: «Adult Swim» resta, per i titoli contano solo le parole forti, quindi «Sex Education» resta), regole in `HomeCfg.kt`. Prove: `AdultFilterTest` (i 94 esempi di `src/test/resources/adult-vectors.json`) e `HomeCfgTest`. Ogni categoria si riconosce una volta sola (cache per nome), i titoli di «Continua a guardare» solo quando si apre la Home; niente lavoro in più durante la visione.

## Riquadro della qualità nel lettore

Voce `qbox` del controllo remoto, uguale in tutte le app Kairos, per il riquadro in alto a destra dei canali in diretta («FHD · 1080p · 50 fps»: ~6 s all'apertura del canale, ~4 s a ogni cambio di risoluzione e sempre con la barra informazioni):
- `off`: il riquadro non compare mai, come con il permesso «resInd» del cliente bloccato.
- `noq`: il riquadro resta senza la sigla di qualità: «1080p · 50 fps».
- `noq` e `off`: anche l'etichetta in cima alla barra informazioni perde la sigla («LIVE» invece di «FHD · LIVE»; su film e serie sparisce).
- `full`, vuota, assente o qualsiasi altro valore: come prima.

Il permesso «resInd» bloccato lo nasconde sempre. Non cambiano i nomi dei canali, gli avvisi, la qualità adattiva, e i dati mandati al pannello (risoluzione e FPS in «Online ora»). Una voce cambiata durante la visione vale dal prossimo aggiornamento del riquadro. Regole in `QBox.kt`, prove in `QBoxTest`.

## Messa in funzione (una volta sola)

1. **Il repository deve essere pubblico**: altrimenti Downloader e l'aggiornamento automatico non possono scaricare l'APK.
2. **Chiave di firma** — in *Settings → Secrets and variables → Actions → New repository secret* crea `ANDROID_KEYSTORE_B64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_PASSWORD`, `ANDROID_KEY_ALIAS`.
   - Più semplice: usa **gli stessi valori** di KAIROS TV (file `LEGGIMI-SEGRETI.txt` che hai conservato).
   - Oppure: *Actions → «Prepara segreti» → Run workflow*, scarica l'allegato e copia i 4 valori. Conserva `release.jks` e poi elimina l'esecuzione.
   > ⚠️ Se perdi la chiave di firma non potrai più aggiornare l'app sui dispositivi dei clienti.
3. Facoltativo, in *Variables*: `LITE_APP_NAME` (nome sotto l'icona, predefinito «Kairos Lite»), `KAIROS_SERVER_URL` (se il pannello cambia indirizzo; predefinito quello attuale su workers.dev), `KAIROS_DL_BASE` (indirizzo dei download e degli aggiornamenti; predefinito `https://store-k.download`).

## Pubblicare una versione

*Releases → Draft a new release* → tag `v1.0.0` (poi `v1.0.1`, `v1.1.0`…) → *Publish*. Dopo pochi minuti alla release sono allegati `kairos-lite.apk` e `kairos-lite.json`.

- Link fisso per **Downloader** e per lo store:
  `https://github.com/mariopiccolo8562-lang/kairos-lite/releases/latest/download/kairos-lite.apk`
- Le app installate chiedono gli aggiornamenti a `https://store-k.download/get/lite.json` (indirizzo dei download `DL_BASE` in `app/build.gradle.kts`, variabile `KAIROS_DL_BASE`), poi al pannello `https://panel-kairos.com/get/lite.json`; scaricano l'APK dal link che c'è dentro (se non riesce, dalle basi successive) e propongono l'aggiornamento da sole all'avvio e ogni 3 ore. Solo se nessuno dei due risponde leggono `kairos-lite.json` dell'ultima release di **questo** repository (riserva finché il repository è pubblico). Kairos Lite è separata da KAIROS TV: del pannello Kairos usa i clienti (attivazione, playlist, profili…) e i download.

Numerazione: correzioni sulla terza cifra (1.0.1, 1.0.2…), cambiamenti grossi sulla versione maggiore (2.0.0).

## Struttura

```
app/src/main/java/com/kairos/lite/   codice (Kotlin, viste Android classiche)
app/src/main/res/                    grafica: schermate, colori, icone
.github/workflows/build.yml          compilazione e pubblicazione dell'APK
.github/workflows/segreti.yml        creazione di una chiave di firma nuova (facoltativa)
```

L'app è solo un lettore e non contiene canali: i contenuti arrivano dalle playlist che assegni dal pannello.
