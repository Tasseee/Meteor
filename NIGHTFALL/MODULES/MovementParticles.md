# MovementParticles

- **Status:** Code geprüft; In-Game-Test offen
- **Java-Klasse / Anzeigename:** `MovementParticles`
- **Kategorie:** `VISUALS`
- **Quellcode-SHA:** `2d93d78b8bb4d49c57c22f8e3a2351fda895e8db`

## Zweck und Verhalten

Erzeugt lokale Partikel beim Laufen und eine größere Ring-Partikelgruppe beim Erkennen einer Landung. Partikel werden nur clientseitig über den Partikelmanager erzeugt.

## Einstellungen

| Einstellung | Standard | Grenzen / Optionen |
|---|---:|---|
| Effekt | Totem | Totem, Soul, Portal, Herzen, Wolke, Flamme, End Rod |
| Menge | 3 | 1–10 |
| Beim Laufen | an | Laufspur aktivieren |
| Beim Springen/Landen | an | Ring beim erkannten Landen |

## Technische Prüfung

- **Voraussetzungen:** Spieler und Welt müssen vorhanden sein.
- **Laufspur:** nur bei Bewegung am Boden; Burst alle drei qualifizierten Ticks.
- **Sprungstatus:** `wasGrounded` wird genutzt, um Übergänge zu erkennen; der aktuelle Code erzeugt den Ring nur beim Übergang von Luft zu Boden (Landung), nicht beim Abheben.
- **Lebenszyklus:** `wasGrounded` und Tickzähler werden beim Weltwechsel zurückgesetzt, aber nicht ausdrücklich bei Aktivieren/Deaktivieren.
- **Performance:** höchstens zehn Laufpartikel oder vierzig Ringpartikel pro Auslösung.
- **Threads/Ressourcen:** keine eigenen Threads oder Listener.
- **In-Game/Build:** noch nicht ausgeführt.

## Befunde und Verbesserungen

### MP-01 – Einstellungsname verspricht mehr als der Code ausführt

Die Option heißt `Beim Springen/Landen`, der Ring wird aber nur bei der Landung ausgelöst. Das ist ein nachweisbarer Unterschied zwischen Einstellungsbezeichnung und Codeverhalten. Erst im Spiel klären, ob dies beabsichtigt ist; mögliche Lösung wäre eine explizite Option für Abheben und Landen.

### MP-02 – Zustandsreset beim Umschalten

Der Übergangszustand wird nicht beim Deaktivieren zurückgesetzt. Wird das Modul während der Luftphase deaktiviert und später am Boden wieder aktiviert, kann der erste Tick als Landung interpretiert werden. Ein gezielter In-Game-Test sollte dies bestätigen, bevor Verhalten geändert wird.

## Offene Tests

- [ ] Laufspur mit Bewegung und ohne Bewegung
- [ ] Sprungbeginn und Landung getrennt
- [ ] Ein-/Ausschalten während des Springens
- [ ] Weltwechsel
- [ ] Partikelmenge an Minimal-/Maximalwerten

## Änderungsverlauf

| Datum | Änderung |
|---|---|
| 2026-11-16 | Erste Codeprüfung; Abweichung bei Sprung/Landung dokumentiert. |
