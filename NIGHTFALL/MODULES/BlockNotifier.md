# BlockNotifier

- **Status:** Code geprüft; Build-/In-Game-Test offen
- **Java-Klasse / Anzeigename:** `BlockNotifier`
- **Kategorie:** `VISUALS`
- **Modul-Quellcode-SHA:** `30996eec344f1e6ea2f9b57fa0343ce160b88768`
- **Quellpfad:** `src/main/java/dev/nightfall/modules/BlockNotifier.java`

## Zweck und Verhalten

Prüft in regelmäßigen Abständen geladene Chunks im Umkreis und meldet neu erkannte, clientseitig sichtbare Block-Entities. Der Chattext sagt ausdrücklich, dass der Inhalt nicht untersucht wird.

## Einstellungen

| Einstellung | Standard | Grenzen / Bedeutung |
|---|---:|---|
| Chests/Barrels | an | Truhen und Fässer melden |
| Shulker | an | Shulker-Boxen melden |
| Spawner | an | Spawner melden |
| Hopper | aus | Hopper melden |
| Range (Blocks) | 48 | 8–128 Blöcke; Schritt 8 |

## Technische Prüfung

- **Scanbereich:** Chunk-Radius wird aus Reichweite plus Chunk-Rand abgeleitet; danach filtert quadrierte Distanz exakt auf die eingestellte Reichweite.
- **Baseline:** beim Aktivieren wird initialisiert, ohne vorhandene Block-Entities als neu zu melden.
- **Lebenszyklus:** Seen-Zustand und temporäre Sammlung werden beim Aktivieren, Deaktivieren und Weltwechsel geleert.
- **Fehlerbehandlung:** keine lokale Exception-Behandlung; zentraler Tick-Handler protokolliert Fehler und deaktiviert das Modul.
- **Performance:** pro Scan wird eine Sammlung der aktuellen Positionen erstellt bzw. wiederverwendet; die Abfrage läuft nur periodisch.
- **In-Game/Build:** noch nicht ausgeführt.

## Befunde und Verbesserungen

### BN-01 – Scanintervall war einen Tick länger als eingestellt

**Behoben im Code:** `23b09c55eceb0463cf8e7adf9abda927a239a82c`.

Der Countdown prüfte zuvor `timer-- <= 0` und setzte anschließend auf 20. Damit lagen zwischen zwei Scans 21 geeignete Ticks. Der Grenzwert ist jetzt `timer-- <= 1`; der erste Scan erfolgt sofort und Folge-Scans liegen 20 geeignete Ticks auseinander.

### BN-02 – Set-Allokation wiederverwenden

**Ebenfalls behoben im selben Commit.** Die aktuelle Positionsmenge wird als Feld wiederverwendet und vor jedem Scan geleert, statt pro Scan eine neue `HashSet`-Instanz anzulegen.

**Verifikation:** Quellcode-Änderung auf GitHub gespeichert und erneut gelesen. Build und Laufzeitmessung stehen noch aus.

## Offene Tests

- [ ] Erster Scan direkt nach Aktivierung
- [ ] Folge-Scans im Abstand von 20 Ticks
- [ ] Reichweite 8 und 128
- [ ] Jede Block-Entity-Filteroption einzeln
- [ ] Weltwechsel und Chunk-Laden/Entladen
- [ ] Nachricht nur bei neuen Positionen

## Änderungsverlauf

| Datum | Änderung |
|---|---|
| 2026-11-16 | Scanintervall korrigiert und aktuelle Positionsmenge wiederverwendet; Tests offen. |
