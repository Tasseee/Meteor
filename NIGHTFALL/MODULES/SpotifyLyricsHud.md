# SpotifyLyricsHud

- **Status:** Code geprüft; OAuth-/In-Game-/Build-Test offen
- **Java-Klasse:** `SpotifyLyricsHud`
- **Anzeigename:** `SpotifyLyricsHUD`
- **Kategorie:** `HUD`
- **Aktueller Quellcode-SHA:** `8eff7f275707e192ee687e02b1ff216bd05f62f0`

## Zweck und Verhalten

Verbindet sich über Spotify OAuth mit dem aktuell wiedergegebenen Track, fragt Track-/Playback-Daten alle drei Sekunden ab und lädt synchronisierte oder ungetimte Liedtexte von LRCLIB. Das Player-HUD ist standardmäßig verborgen; andere Module wie WorldLyrics können die Liedtextdaten unabhängig vom Player-HUD verwenden.

## Einstellungen

| Einstellung | Standard | Grenzen / Bedeutung |
|---|---|---|
| Spotify Client ID | leer | max. 120 Zeichen; aus Spotify Developer Dashboard |
| Synchronisierte Lyrics | an | Lyrics abrufen und anzeigen |
| Naechste Zeile anzeigen | an | nächste Zeile im HUD anzeigen |
| Animierter Equalizer | an | lokale Balkenanimation |
| Player-HUD anzeigen | aus | Player-/Lyrics-HUD sichtbar machen |
| HUD-Breite | 220 | 180–320; Schritt 10 |

## Verbindung, Datenschutz und Lifecycle

- OAuth Authorization Code mit PKCE (S256) und zufälligem State.
- Callback-Server bindet ausschließlich an `127.0.0.1:8888/callback`; externe Netzwerkschnittstellen werden nicht geöffnet.
- Benötigte Spotify-Scopes: `user-read-currently-playing`, `user-read-playback-state`.
- Client ID liegt in der Setting-Konfiguration; Access Token und Verifier liegen nur im Arbeitsspeicher und werden beim Deaktivieren entfernt.
- Track-Metadaten (Titel, Interpret, Album, Dauer) werden an `lrclib.net` gesendet, um Lyrics zu suchen.
- Polling läuft in einem Daemon-Single-Thread-Executor; OAuth-Callback verwendet einen separaten Executor.
- Deaktivieren beendet Worker, Callback-Server und Callback-Executor und leert Track-/Lyrics-Daten.
- Weltwechsel beendet Spotify nicht; `onWorldLeave()` ist absichtlich leer.

## Technische Prüfung

- **Netzwerk:** HTTP-Timeouts sind gesetzt; Redirects werden nicht automatisch verfolgt.
- **Null-/Fehlerfälle:** Statusmeldungen behandeln fehlende Client ID, 204, 401 und sonstige HTTP-Fehler.
- **Token-Erneuerung:** kein automatischer Refresh-Token-Flow; bei 401 fordert der Status dazu auf, das Modul aus-/einzuschalten.
- **Thread-Sicherheit:** Track, Access Token, Status und Lyrics sind volatile; `lyricsLoading` ebenfalls. Mehrere Aufrufpfade können Polling auslösen; Konkurrenztests stehen aus.
- **LRC:** Zeitmarken werden sortiert; ungetimte Plain Lyrics erhalten gleichmäßig verteilte Ersatzzeiten.
- **HUD:** Progress wird anhand des letzten Poll-Zeitpunkts interpoliert und auf Trackdauer begrenzt.
- **Build-/Verbindungstest:** nicht ausgeführt.

## Behobene Befunde

### SL-01 – mehrere Zeitmarken pro LRC-Zeile

**Behoben:** `ed3a3d1378aa9c757b023b144b9bf1446837c3e6`.

Der Parser verwendete zuvor einen greedy Text-Capture und konnte bei Zeilen wie `[00:10.00][00:20.00]Text` die zweite Zeitmarke nicht getrennt übernehmen. Der Parser sammelt jetzt alle Zeitmarken vor dem Text und erzeugt für jede einen Eintrag. Das Zeitmarken-Pattern wird außerdem einmalig statisch kompiliert.

### SL-02 – OAuth-Executor wurde nicht beendet

**Behoben im selben Commit.**

Der Callback-Executor wird jetzt als Feld verwaltet und beim Schließen des lokalen Callback-Servers mit `shutdownNow()` beendet. Das verhindert zurückbleibende Executor-Threads bei wiederholten Login-Versuchen.

### SL-03 – ungültige Callback-Rückgabe ließ Login-Zustand hängen

**Behoben:** `68f00f13ac9118f17385a13210d51ab7fae26c6f`.

Wenn State oder Authorization Code fehlten/nicht passten, wurde der HTTP-Fehler beantwortet, aber `loginInProgress` blieb zuvor wahr. Der Fehlerpfad setzt jetzt Status und Login-Flag zurück; die HTTP-Exchange wird in jedem Callback-Pfad geschlossen.

**Verifikation:** Quellcode-Änderungen wurden gespeichert und erneut aus GitHub gelesen. Build, echter OAuth-Login und LRC-Test stehen noch aus.

## Offene Befunde und Verbesserungen

- Bei leerer/fehlgeschlagener Lyrics-Antwort wird der Track-Key schon vor dem Request gespeichert. Für denselben Track wird dadurch nicht automatisch erneut gesucht. Ein moderates Retry-Backoff wäre sinnvoll, ist aber noch nicht umgesetzt.
- `lastPollAt` und `positionAtReceipt` werden gesetzt, aber im aktuellen Code nicht gelesen.
- Die getrennten Methoden `worldLyricLines()` und `worldLyricTimes()` kopieren beide Listen in Arrays. WorldLyrics ruft diese Daten pro HUD-Frame ab; ein gemeinsamer unveränderlicher Snapshot könnte Allokationen vermeiden.
- Der konkrete Callback-Fehlerpfad und parallele Polling-Aufrufe müssen mit einem echten Login getestet werden.

## Offene Tests

- [ ] Client ID fehlt / ist eingetragen
- [ ] Port 8888 belegt
- [ ] erfolgreicher OAuth-Flow
- [ ] ungültiger State / fehlender Code
- [ ] Modul während OAuth deaktivieren
- [ ] Spotify 204 / 401 / 429 / 5xx
- [ ] LRC mit einer und mehreren Zeitmarken pro Zeile
- [ ] Plain Lyrics und fehlende Lyrics
- [ ] Weltwechsel, deaktivieren und erneutes aktivieren
- [ ] WorldLyrics-Datenzugriff bei gleichzeitigem Polling

## Änderungsverlauf

| Datum | Änderung |
|---|---|
| 2026-11-16 | OAuth-/Lyrics-Code geprüft; LRC-Parser, Callback-Executor und ungültigen Callback-Zustand korrigiert. |
