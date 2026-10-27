# Lumen

A lightweight IPTV player for **Android TV** and **Amazon Fire TV** (also runs on phones and tablets), built with Kotlin, Jetpack Compose and Media3 (ExoPlayer). It works with any **Xtream Codes** compatible provider. The release APK is only a few MB and has no native libraries.

> **Lumen is just a player.** It ships with no channels, playlists or content of any kind, and it is not affiliated with any provider. You need your own subscription with a provider you are legally entitled to use.

*Italiano: vedi [README.it.md](README.it.md).*

## Features

- Sign in with **server address, username and password** (nothing is hard-coded)
- Live TV, Movies and Series, with categories and search
- TV guide (EPG) from your provider, with an optional fallback to a public XMLTV guide matched by channel name
- Favorites, recent channels and "Continue watching" with resume position
- 4 profiles, each with its own favorites and history
- Audio track / subtitle selection, sleep timer, channel number entry, previous-channel key
- Built for the remote control (D-pad), with a built-in **Help** page in Settings
- Credentials stored encrypted on the device
- Translated into English, Italian, Spanish, French, German, Portuguese, Russian, Turkish, Polish and Dutch (follows the system language)

## Install

Download the APK from the [Releases](../../releases) page, then:

- **Fire TV Stick**: Settings → My Fire TV → Developer options → *Install unknown apps* (for *Downloader*), then install with the *Downloader* app, or run `adb connect <fire-tv-ip>` followed by `adb install -r Lumen-x.y.z.apk`.
- **Android TV / phone**: `adb install -r Lumen-x.y.z.apk`, or copy the APK to a USB drive / the device and open it.

On first launch enter the server address given by your provider (for example `http://example.com:8080`), your username and your password.

## Build from source

Requirements: Android Studio (for the bundled JDK 17+) and the Android SDK.

```bat
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
gradlew.bat :app:assembleDebug
```

For a signed release build create `keystore.properties` in the project root (it is git-ignored):

```properties
storeFile=keystore/lumen.jks
storePassword=...
keyAlias=lumen
keyPassword=...
```

then run `gradlew.bat :app:assembleRelease`. The APK is written to `app/build/outputs/apk/release/`. Keep your keystore: it is needed to update an installed app.

## Project layout

```
app/src/main/java/com/lumen/app/
  data/   Xtream Codes client, EPG parsing, encrypted storage
  ui/     Compose screens (login, home, players, help)
  AppViewModel.kt, MainActivity.kt
app/src/main/res/values*/strings.xml   translations
```

### Adding a language

Copy `app/src/main/res/values/strings.xml` to `values-<code>/strings.xml` and translate the values. Pull requests are welcome.

## License

[MIT](LICENSE)
