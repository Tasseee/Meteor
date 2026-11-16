# ParticleTrail

- **Status:** Code geprüft; In-Game-Test offen
- **Java-Klasse / Anzeigename:** `ParticleTrail`
- **Kategorie:** `VISUALS`
- **Quellcode-SHA:** `183265c6e0bfb287d146e88b051633bbb2b79636`

## Zweck und Verhalten

Erzeugt lokal eine Partikelspur um den Spieler. Der Partikelerzeugungsversuch erfolgt alle zwei Ticks; mit aktivierter Option `Nur beim Bewegen` wird bei sehr geringer Geschwindigkeit übersprungen.

## Einstellungen

| Einstellung | Standard | Grenzen / Optionen |
|---|---:|---|
| Partikel | Enchant | Enchant, Soul, Portal, Herzen, Wolke, Flamme |
| Dichte | 3 | 1–8 Partikel pro Ausgabe |
| Nur beim Bewegen | an | Ausgabe an Bewegung koppeln |

## Technische Prüfung

- **Nullfälle:** Spieler und Welt werden geprüft.
- **Intervall:** Tickzähler wird beim Weltwechsel auf 0 gesetzt; bei Aktivieren/Deaktivieren nicht.
- **Performance:** maximal acht lokale Partikel alle zwei Ticks; keine Entity-Suche.
- **Threads/Ressourcen:** keine eigenen Threads oder Listener.
- **Fehlerbehandlung:** zentraler Tick-Handler.
- **In-Game/Build:** noch nicht ausgeführt.

## Befunde und Verbesserungen

- Kein eindeutiger Fehler festgestellt.
- Tickzähler beim Aktivieren/Deaktivieren zurücksetzen wäre eine kleine Lifecycle-Vereinheitlichung, hat aber nur geringen sichtbaren Einfluss.
- Im Spiel prüfen, ob der Bewegungsgrenzwert `0.0025` bei sehr langsamer Bewegung wie gewünscht reagiert.

## Offene Tests

- [ ] Alle sechs Partikelstile
- [ ] Dichte 1 und 8
- [ ] Stillstehen mit `Nur beim Bewegen` an/aus
- [ ] Weltwechsel und Modul-Umschalten
- [ ] FPS-Auswirkung bei maximaler Dichte

## Änderungsverlauf

| Datum | Änderung |
|---|---|
| 2026-11-16 | Erste Codeprüfung anhand der Modulklasse. |
