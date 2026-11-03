# Kairos Lite

App IPTV leggera in stile Smarters per **Fire TV, Android TV, Google TV e telefoni**, anche vecchi (da Android 5.1: Fire TV Stick di 1ª e 2ª generazione comprese).
Si installa **accanto** a KAIROS TV e usa lo **stesso pannello Kairos**: stessi clienti, stesso codice di attivazione `KX..-....`.

## Cosa fa

- Home con tre riquadri (TV in diretta, Film, Serie TV) e «Continua a guardare».
- TV in diretta a tre colonne (categorie · canali · anteprima con guida adesso/dopo), cambio canale con ▲▼ e con i numeri.
- Film e serie con stagioni ed episodi, posizione di visione salvata, episodio successivo automatico.
- Ricerca unica, preferiti, profili (anche bambini).
- **Modalità leggera** automatica sulle chiavette con meno di ~1,8 GB di RAM: niente animazioni né anteprima video, immagini e buffer più piccoli.
- Liste salvate in un archivio sul dispositivo e lette a pezzi: veloce anche con 100.000 titoli.

Dal pannello funzionano: attivazione e scadenza, playlist M3U/Xtream (unite o separate), cambio DNS, *Disconnetti*, limite di visioni contemporanee, permessi, profili, collegamento di altre TV con codice a 6 cifre, messaggi, VPN (*Consentita* / *Sempre attiva*), *Errori applicazione*. In *Utenti online* la versione compare come `lite 1.0.0`.

Non ci sono, di proposito: multischermo, giochi, temi e add-on, assistente IA, registrazioni, speed test, trailer, lettore VLC.

## Messa in funzione (una volta sola)

1. **Il repository deve essere pubblico**: altrimenti Downloader e l'aggiornamento automatico non possono scaricare l'APK.
2. **Chiave di firma** — in *Settings → Secrets and variables → Actions → New repository secret* crea `ANDROID_KEYSTORE_B64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_PASSWORD`, `ANDROID_KEY_ALIAS`.
   - Più semplice: usa **gli stessi valori** di KAIROS TV (file `LEGGIMI-SEGRETI.txt` che hai conservato).
   - Oppure: *Actions → «Prepara segreti» → Run workflow*, scarica l'allegato e copia i 4 valori. Conserva `release.jks` e poi elimina l'esecuzione.
   > ⚠️ Se perdi la chiave di firma non potrai più aggiornare l'app sui dispositivi dei clienti.
3. Facoltativo, in *Variables*: `LITE_APP_NAME` (nome sotto l'icona, predefinito «Kairos Lite»), `KAIROS_SERVER_URL` (se il pannello cambia indirizzo; predefinito quello attuale su workers.dev).

## Pubblicare una versione

*Releases → Draft a new release* → tag `v1.0.0` (poi `v1.0.1`, `v1.1.0`…) → *Publish*. Dopo pochi minuti alla release sono allegati `kairos-lite.apk` e `kairos-lite.json`.

- Link fisso per **Downloader** e per lo store:
  `https://github.com/mariopiccolo8562-lang/kairos-lite/releases/latest/download/kairos-lite.apk`
- Il pannello Kairos legge `kairos-lite.json` da solo: le app installate propongono l'aggiornamento all'avvio e ogni 3 ore.

Numerazione: correzioni sulla terza cifra (1.0.1, 1.0.2…), cambiamenti grossi sulla versione maggiore (2.0.0).

## Struttura

```
app/src/main/java/com/kairos/lite/   codice (Kotlin, viste Android classiche)
app/src/main/res/                    grafica: schermate, colori, icone
.github/workflows/build.yml          compilazione e pubblicazione dell'APK
.github/workflows/segreti.yml        creazione di una chiave di firma nuova (facoltativa)
```

L'app è solo un lettore e non contiene canali: i contenuti arrivano dalle playlist che assegni dal pannello.
