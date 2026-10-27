# Lumen

Player IPTV leggero per **Android TV** e **Amazon Fire TV** (funziona anche su telefoni e tablet), scritto in Kotlin con Jetpack Compose e Media3 (ExoPlayer). Funziona con qualsiasi fornitore compatibile **Xtream Codes**. L'APK release pesa pochi MB, senza librerie native.

> **Lumen è solo un player.** Non include canali, liste o contenuti di alcun tipo e non è affiliato ad alcun fornitore. Serve un abbonamento personale presso un fornitore che hai il diritto di usare.

*English: see [README.md](README.md).*

## Funzioni

- Accesso con **indirizzo del server, username e password** (nulla è scritto nel codice)
- TV Live, Film e Serie, con categorie e ricerca
- Guida TV (EPG) del fornitore, con ricerca facoltativa in una guida XMLTV pubblica abbinando il nome del canale
- Preferiti, canali recenti e «Continua a guardare» con ripresa
- 4 profili, ognuno con i propri preferiti e la cronologia
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

## Licenza

[MIT](LICENSE)
