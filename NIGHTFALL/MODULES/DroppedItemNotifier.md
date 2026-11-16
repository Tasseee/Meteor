# DroppedItemNotifier

- **Status:** Code geprüft; Build-/In-Game-Test offen
- **Java-Klasse / Anzeigename:** `DroppedItemNotifier`
- **Kategorie:** `VISUALS`
- **Quellpfad:** `src/main/java/dev/nightfall/modules/DroppedItemNotifier.java`
- **Aktueller Quellcode-SHA:** `ea47824401e2108dc062f9d3ba51a959e3d9720e`
- **Registrierung:** `ModuleManager.init()`

## Zweck und Verhalten

Das Modul beobachtet Item-Entities innerhalb einer konfigurierten Reichweite. Nach einem ersten Baseline-Tick meldet es neu auftauchende passende Items per Chat und/oder Sound. Bereits beim Aktivieren vorhandene Items lösen durch die Baseline-Logik keine Meldung aus.

Erkannt werden Elytra, Mace, Totem, Heavy Core, Netherite-Barren, Diamanten, Emeralds und verzauberte Goldäpfel.

## Einstellungen

| Einstellung | Standard | Grenzen / Bedeutung |
|---|---:|---|
| Reichweite | 48 | 8–128; Schritt 8 |
| Elytra | an | Elytra melden |
| Mace | an | Mace melden |
| Totem | an | Totem melden |
| Heavy Core | an | Heavy Core melden |
| Netherite-Barren | an | Netherite-Barren melden |
| Diamanten | an | Diamanten melden |
| Emeralds | an | Emeralds melden |
| Verzauberter Goldapfel | an | Verzauberte Goldäpfel melden |
| Sound bei Fund | an | Sound abspielen |
| Chat-Nachricht | an | Chatmeldung anzeigen |

## Technische Prüfung

- **Lebenszyklus:** beide UUID-Sets und Baseline-Status werden beim Aktivieren, Deaktivieren und Weltwechsel zurückgesetzt.
- **Nullfälle:** Tick-Verarbeitung läuft nur mit Spieler und Welt.
- **Deduplizierung:** UUIDs verhindern wiederholte Meldungen derselben Item-Entity; nicht mehr vorhandene UUIDs werden entfernt.
- **Reichweite:** quadrierte Distanz.
- **Threading:** keine eigenen Threads; Verarbeitung im Tick-Kontext.
- **Fehlerbehandlung:** zentraler Tick-Handler behandelt Ausnahmen.
- **Laufzeit/Version:** Build und In-Game-Test stehen noch aus.

## Befunde und Verbesserungen

### DIN-01 – temporäre Set-Allokation reduzieren

**Umgesetzt im Code:** `9f713623ae1ae41f8baea1610ff6386dd03a4400`.

Die UUID-Sammlung für aktuell sichtbare Item-Entities wird jetzt als Feld wiederverwendet und pro Tick geleert, statt bei jedem Tick ein neues `HashSet` anzulegen. Sie wird auch beim Aktivieren, Deaktivieren und Weltwechsel zurückgesetzt. Die Deduplizierungslogik bleibt unverändert.

**Verifikation:** Änderung und Quellcode wurden auf GitHub gespeichert und anschließend gelesen. Build- und In-Game-Test sind noch offen.

### DIN-02 – zusammengeführte Item-Entities

Wenn sich die Menge einer bereits bekannten Item-Entity erhöht, bleibt ihre UUID gleich. Deshalb erfolgt keine zweite Meldung zur Mengenänderung. Ob eine erneute Meldung erwünscht ist, bleibt eine Produktentscheidung und benötigt einen In-Game-Test.

## Nutzer- und In-Game-Tests

Offen: Baseline beim Aktivieren, neu gedroppte Items, Reichweitengrenzen, Weltwechsel, zusammengeführte Stacks, jede Filteroption sowie Chat-/Sound-Ausgabe getrennt.

## Änderungsverlauf

| Datum | Änderung |
|---|---|
| 2026-11-16 | Codeprüfung; Wiederverwendung der UUID-Sammlung zur Reduktion kurzlebiger Allokationen. |
