# Lumen

Player IPTV leggero per **Android TV** e **Amazon Fire TV** (funziona anche su telefoni e tablet), scritto in Kotlin con Jetpack Compose e Media3 (ExoPlayer). Funziona con qualsiasi fornitore compatibile **Xtream Codes**. L'APK release pesa pochi MB, senza librerie native.

> **Lumen è solo un player.** Non include canali, liste o contenuti di alcun tipo e non è affiliato ad alcun fornitore. Serve un abbonamento personale presso un fornitore che hai il diritto di usare.

*English: see [README.md](README.md).*

## Funzioni

- Accesso con **indirizzo del server, username e password** (nulla è scritto nel codice)
- TV Live, Film e Serie, con categorie e ricerca
- Guida TV (EPG) del fornitore, con ricerca facoltativa in una guida XMLTV pubblica abbinando il nome del canale
- **Promemoria** dalla Guida TV (tieni premuto OK su un canale e scegli il programma): avviso un minuto prima dell'inizio, anche ad app chiusa
- **Registrazione** del canale live che stai guardando in .ts nella cartella Download/Lumen (si apre con Lumen, VLC e quasi tutti i lettori) (oppure su chiavetta USB / scheda SD, o sui telefoni in una cartella a scelta); le registrazioni si rivedono nell'app
- **Pausa della diretta** e ripresa in differita, con ritorno alla diretta
- Preferiti, canali recenti e «Continua a guardare» con ripresa
- 4 profili con un nome a scelta, ognuno con i propri preferiti e la cronologia
- Scelta di audio e sottotitoli, timer di spegnimento, numero del canale, tasto canale precedente
- Pensata per il telecomando, con una pagina **Guida** nelle Impostazioni
- Credenziali salvate cifrate sul dispositivo
- Tradotta in inglese, italiano, spagnolo, francese, tedesco, portoghese, russo, turco, polacco e olandese (segue la lingua di sistema)

## Installazione

Scarica l'APK dalla pagina [Releases](../../releases), poi:

- **Fire TV Stick**: Impostazioni → Il mio Fire TV → Opzioni sviluppatore → *Installa app sconosciute* (per *Downloader*), quindi installa con *Downloader* oppure `adb connect <ip-fire-tv>` e `adb install -r Lumen-x.y.z.apk`.
- **Android TV / telefono**: `adb install -r Lumen-x.y.z.apk`, oppure copia l'APK su USB o sul dispositivo e aprilo.

Al primo avvio inserisci l'indirizzo del server fornito dal tuo fornitore (es. `http://esempio.it:8080`), username e password.

## Compilare

Servono Android Studio (JDK incluso) e l'Android SDK.

```bat
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
gradlew.bat :app:assembleDebug
```

Per l'APK firmato crea `keystore.properties` nella radice (è escluso da git) con `storeFile`, `storePassword`, `keyAlias`, `keyPassword`, poi `gradlew.bat :app:assembleRelease`. Conserva il keystore: serve per aggiornare l'app installata.

## Privacy

Lumen non ha account né server incorporato. All'avvio (una volta al giorno) e quando usi alcune funzioni (avvio di Live, film o serie, ricerca, preferiti, guida TV, errore di riproduzione) invia un conteggio anonimo (nome fisso dell'evento, nome app, versione, "android", lingua del dispositivo) a `https://candelab.net/stats` per sapere quanto sono usate le funzioni e quali versioni. Ogni installazione ha un codice casuale anonimo (nessun legame con te o col dispositivo), usato solo per contare installazioni e utenti attivi. Non invia mai indirizzo del server, credenziali, canali o ciò che guardi. Il codice è in `app/src/main/java/com/lumen/app/AppStats.kt`.

## Licenza

[MIT](LICENSE).
