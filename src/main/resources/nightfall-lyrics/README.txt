Nightfall Lyrics – lokaler Musikdienst

1) Python 3.11 oder neuer installieren.
2) In diesem Ordner ein Terminal öffnen.
3) Unter Windows ausführen:
   py -3 -m pip install -r requirements-windows.txt
   py -3 lyrics_sidecar.py
   Unter Linux:
   python3 -m pip install -r requirements.txt
   sudo apt install playerctl
   python3 lyrics_sidecar.py

Nightfall startet den Dienst beim Aktivieren des Moduls automatisch, sofern Python und
die Abhängigkeiten vorhanden sind. Das manuelle Starten ist nur nötig, wenn Auto-Start
deaktiviert ist oder die Abhängigkeiten noch fehlen.


Spotify verbinden

Windows:
- In der Spotify-Desktop-App anmelden und Musik abspielen oder pausieren.
- Nightfall liest den Spotify-Titel automatisch aus der Windows-Mediensteuerung (SMTC).
- Es ist kein Spotify-Passwort, kein API-Schlüssel und kein Spotify-Login im Client nötig.

Linux:
- Spotify-Desktop-App starten und anmelden.
- Sicherstellen, dass playerctl Spotify als MPRIS-Player erkennt: playerctl -l
- Nightfall bevorzugt nun explizit den Player mit "spotify" im Namen. Falls Spotify nicht
  läuft oder keine Metadaten liefert, wird automatisch der Standard-MPRIS-Player verwendet.

Hinweis:
Nightfall steuert damit nicht dein Spotify-Konto und startet keine Wiedergabe aus Minecraft.
Es liest nur die Metadaten und Wiedergabeposition der lokal angemeldeten Spotify-App.
Die Lyrics selbst kommen weiterhin zeitlich synchronisiert von LRCLIB.

Windows: liest die aktive Windows-Mediensteuerung (SMTC); kompatible Apps/Browsertabs
werden automatisch erkannt. Linux: verwendet playerctl/MPRIS; Spotify, Browser und
andere MPRIS-Player werden erkannt, sofern sie dort sichtbar sind.

Lyrics werden bei Trackwechsel über LRCLIB gesucht. Es werden nur Titel, Interpret,
Album und Laufzeit übertragen – keine Audiodaten und keine Login-Tokens. Die Verbindung
zwischen Sidecar und Nightfall bleibt auf 127.0.0.1 beschränkt.

Wenn kein synchronisierter LRCLIB-Eintrag existiert, zeigt Nightfall weiter den
Now-Playing-Track, aber keine erfundenen Zeilen. Manche Titel haben nur ungetimte Lyrics
oder sind nicht in der Datenbank.

Logs: sidecar.log in diesem Ordner.
