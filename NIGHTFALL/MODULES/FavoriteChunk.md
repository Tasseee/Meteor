# FavoriteChunk

- **Status:** Code geprüft; Speicher-/In-Game-Test offen
- **Java-Klasse / Anzeigename:** `FavoriteChunk`
- **Kategorie:** `WORLD`
- **Modul-Quellcode-SHA:** `01af4259da07fb94f7a967c95653b04ec3abef6b`
- **Hilfsklasse:** `src/main/java/dev/nightfall/scanner/ChunkFavorites.java`
- **Hilfsklassen-SHA:** `ef50cc905a240a7285c28d12987c707c0ea5fb78`

## Zweck und Verhalten

Einmalige Hotkey-Aktion: markiert den aktuellen Chunk als Favorit oder entfernt ihn wieder aus den Favoriten. Nach der Aktion deaktiviert sich das Modul selbst. Die Favoriten werden getrennt nach Server und Dimension gespeichert.

## Einstellungen

Keine modulspezifischen Einstellungen.

## Technische Prüfung

- **Pending-Zustand:** wird beim Aktivieren gesetzt und beim Deaktivieren zurückgesetzt.
- **Nullfälle:** wartet, bis Spieler und Welt vorhanden sind; erst dann wird die Aktion ausgeführt.
- **Persistenz:** `ChunkFavorites` nutzt synchronisierte öffentliche Zugriffsmethoden und speichert X/Z-Paare in einer Textdatei.
- **Kontexttrennung:** Dateiname basiert auf Server- und Dimensionskontext; Sonderzeichen werden ersetzt.
- **Fehlerbehandlung:** Lese- und Schreib-`IOException` werden in der Hilfsklasse still ignoriert.
- **Weltwechsel:** das Modul setzt `pending` nicht selbst in `onWorldLeave()` zurück. Eine noch ausstehende Hotkey-Aktion kann deshalb bis zur nächsten Welt bestehen bleiben.
- **In-Game/Dateitest:** noch nicht ausgeführt.

## Befunde und Verbesserungen

### FC-01 – stille Persistenzfehler

Wenn das Laden oder Schreiben der Favoriten-Datei fehlschlägt, wird die Exception verworfen. Der Nutzer erhält keine Rückmeldung und kann eine nicht gespeicherte Änderung übersehen. Sinnvolle Verbesserung: Fehler über den Projekt-Logger melden und gegebenenfalls eine verständliche Notification ausgeben.

### FC-02 – ausstehende Aktion bei Weltwechsel

Ein Weltwechsel setzt den Pending-Schalter im Modul nicht zurück. Es sollte geprüft werden, ob das gewünschte Verhalten eine ausstehende Aktion über einen Weltwechsel hinweg tatsächlich ausführen soll. Aktuell ist das nicht ausdrücklich dokumentiert.

## Offene Tests

- [ ] Favorit hinzufügen und entfernen
- [ ] Neustart und erneutes Laden
- [ ] Unterschiedliche Server und Dimensionen
- [ ] Schreibschutz/Dateifehler
- [ ] Weltwechsel zwischen Hotkey und Tick
- [ ] Leere bzw. beschädigte Datei

## Änderungsverlauf

| Datum | Änderung |
|---|---|
| 2026-11-16 | Erste Codeprüfung von Modul und Persistenz-Hilfsklasse. |
