# DroppedItemNotifier

- **Status:** Code geprüft; In-Game-/Build-Test offen
- **Java-Klasse / Anzeigename:** `DroppedItemNotifier`
- **Kategorie:** `VISUALS`
- **Quellpfad:** `src/main/java/dev/nightfall/modules/DroppedItemNotifier.java`
- **Quellcode-SHA:** `4bec8bd15dadf52243d61bb522ab1852e639bf29`
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

- **Lebenszyklus:** UUID-Menge und Baseline-Status werden beim Aktivieren, Deaktivieren und Weltwechsel zurückgesetzt.
- **Nullfälle:** Tick-Verarbeitung wird nur bei vorhandenem Spieler und Welt ausgeführt.
- **Deduplizierung:** UUIDs verhindern mehrfache Meldungen derselben Item-Entity; nicht mehr vorhandene UUIDs werden entfernt.
- **Reichweite:** Vergleich anhand quadrierter Distanz; kein unnötiges Wurzelziehen im Distanzvergleich.
- **Performance:** pro Tick werden eine neue HashSet-Instanz und eine aktuelle UUID-Menge erzeugt; in Szenen mit vielen Item-Entities entstehen dadurch vermeidbare kurzlebige Allokationen.
- **Thread-Sicherheit:** keine eigenen Threads; Verarbeitung findet im Tick-Kontext statt.
- **Fehlerbehandlung:** kein modulspezifisches try/catch; zentraler Tick-Handler protokolliert Exceptions.
- **Laufzeit/Version:** nicht im Spiel getestet; Build nicht ausgeführt.

## Befunde und Verbesserungen

### Verbesserungskandidat DIN-01 – temporäre Set-Allokation reduzieren

`currentlyPresent` wird bei jedem Tick neu angelegt, obwohl es nur als temporäre UUID-Sammlung dient. Eine wiederverwendete Instanz mit `clear()` würde kurzlebige Allokationen reduzieren. Das ist eine Performance-Verbesserung, kein bestätigter Fehler.

### Einschränkung DIN-02 – zusammengeführte Item-Entities

Wenn der Client die Menge einer bereits bekannten Item-Entity erhöht, bleibt ihre UUID gleich. Der Code meldet deshalb nicht erneut, dass sich die Menge geändert hat. Ob dies gewünschtes Verhalten ist, sollte durch einen Test mit zusammenlaufenden Item-Stacks geklärt werden.

## Nutzer- und In-Game-Tests

Offen: Baseline beim Aktivieren, neu gedroppte Items, Items außerhalb/innerhalb der Reichweite, Weltwechsel, zusammengeführte Stacks, jede Filteroption einzeln sowie Chat-/Sound-Ausgabe getrennt.

## Änderungsverlauf

| Datum | Änderung |
|---|---|
| 2026-11-16 | Erste Codeprüfung; keine Java-Änderung in diesem Audit-Eintrag. |
