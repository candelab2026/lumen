# Changelog

## 2.2.1

- Live player on TV: when the bottom bar is shown, ◀ ▶ OK move between its buttons (Channels, Guide, Pause, Options…) instead of opening the list or guide; Back closes the bar.
- Recordings: the video is copied as is instead of being re-encoded (channels with non-square pixels, e.g. SD 16:9 or HD 1440x1080, failed to convert on Fire TV); conversion is also much faster. The last conversion error is shown in the recordings list.
- Pause live TV: more patient reconnection for single-connection subscriptions, both when pausing and when going back to live.
- Profiles can be renamed (Settings → Rename profile); the active profile is always shown below the channel list.

## 2.2.0

- TV guide reminders: hold OK (or Menu) on a channel, pick an upcoming programme; a notice appears a minute before it starts (dialog with the app open, notification otherwise) and opens the channel.
- Recording of the live channel being watched (Options → Record, or the Record key), saved by default as .mp4 in Download/Lumen (converted when the recording ends; kept as .ts if conversion fails), or on a USB drive / SD card, or any folder on phones. Recordings interrupted by closing the app or switching off are converted at the next launch. Uses the same connection as playback. Stops at the end of the programme, on channel change or when leaving the player.
- Recordings list in Settings: play back or delete.
- Pause live TV: Play/Pause (or the Pause button) keeps downloading the channel to a temporary file; resume watches it delayed, "Back to live" returns to the live edge. Same single connection.
- MP2 audio (common on TV channels) is decoded in software (JLayer) so recordings can be converted to MP4 with AAC audio.

## 2.1.7

- Phones: full-screen video (system bars hidden while playing).
- Phones: « » buttons in the TV guide to change the time slot by touch.
- Phones: compact layout for home, movie and series pages; the series Favorites button is always reachable.
- Player buttons scroll instead of going off-screen.
- The intro animation can be skipped with OK or a tap.
- Hint for adding channels to favorites; translated favorites option in the live player.

## 2.1.6

- Anonymous random install ID in the usage counts, to count installs and active users.

## 2.1.5

- Fixed an out-of-memory crash while refreshing the TV guide.
- Miscellaneous improvements.

## 2.1.4

- Anonymous usage counts: on launch (once a day) and on a few features (play live/movie/series, search, favorites, TV guide, playback error) the app sends a fixed event name, app name, version, "android" and device language to candelab.net/stats. No identifier, no account or viewing data. See "Privacy" in the README.

## 2.1.3

- Miscellaneous improvements.

## 2.1.2

- Login screen is now scrollable so the sign-in button is always reachable on phones; keyboard Done signs in.

## 2.1.1

- Settings: buttons laid out in an ordered two-column grid.
- Clearer dialogs (exit/renewal) and visible press feedback on buttons.
- "Check for updates" now shows its result in an on-screen message.

## 2.1.0
- In-app update from GitHub Releases: a button in Settings downloads and installs the new APK (same signature, so user data is kept).
- Automatic update check every 5 launches, with a 5-second on-screen notice.
- Channel/VOD lists and guide now refresh every 5 launches (was 3).

## 2.0.0
- Renamed everything to Lumen (package `com.lumen.app`).
- Server address is now entered on the sign-in page; no server is built into the app.
- Generic IPTV player: public EPG fallback is optional and follows the device country.
- Built-in Help page in Settings.
- Translations: en, it, es, fr, de, pt, ru, tr, pl, nl.
