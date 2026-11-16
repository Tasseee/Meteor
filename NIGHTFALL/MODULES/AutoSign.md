# AutoSign

- **Status:** Code geprüft; In-Game-Test offen
- **Java-Klasse / Anzeigename:** `AutoSign`
- **Kategorie:** `MISC`
- **Modul-SHA:** `0a7f7e053aeebc69a1c96633969a79110c145526`
- **Mixin:** `AbstractSignEditScreenMixin`
- **Mixin-SHA:** `d5ad19c07d3f530589c9509430a6b668008f2ba1`

## Zweck und Verhalten

Füllt beim Öffnen des Schild-Bearbeitungsbildschirms bis zu vier konfigurierte Zeilen vor. Das Speichern des Schildes bleibt eine manuelle Aktion. Die Mixin-Logik wird pro Screen-Instanz höchstens einmal ausgeführt.

## Einstellungen

| Einstellung | Standard | Verhalten |
|---|---|---|
| Zeile 1 | leer | Text für erste Zeile; maximal 80 Zeichen |
| Zeile 2 | leer | Text für zweite Zeile; maximal 80 Zeichen |
| Zeile 3 | leer | Text für dritte Zeile; maximal 80 Zeichen |
| Zeile 4 | leer | Text für vierte Zeile; maximal 80 Zeichen |
| Vorhandenen Text ersetzen | an | Vorhandene Zeile wird ersetzt, wenn der konfigurierte Eintrag nicht leer ist |

## Technische Prüfung

- **Screen-Lifecycle:** `nightfall$prefilled` verhindert mehrfaches Vorfüllen auf derselben Screen-Instanz.
- **Null-/Grenzfälle:** Mixin nutzt `Math.min(screenLines, configuredLines)`; die Anzahl der zu bearbeitenden Zeilen ist begrenzt.
- **Leere Konfiguration:** leere konfigurierte Zeilen überschreiben auch bei aktivem `overwrite` keinen vorhandenen Text.
- **Fehlerbehandlung:** fängt `IllegalStateException` bei fehlender Modulregistrierung, andere Fehler laufen in den regulären Clientpfad.
- **Datenintegrität:** das Modul sendet/speichert das Schild nicht selbst.
- **In-Game:** noch nicht getestet.

## Befunde und Verbesserungen

- Kein eindeutiger Fehler bei der einmaligen Befüllung gefunden.
- Die Option `Vorhandenen Text ersetzen` ersetzt nur mit nichtleeren Konfigurationszeilen. Das ist aus Code ersichtlich, sollte aber in der GUI-Beschreibung genauer erklärt werden.
- Die 80-Zeichen-Grenze der Settings ist länger als manche versions-/serverseitige Schilderwartung; tatsächliche Annahme bleibt Server-/Versionstest.

## Offene Tests

- [ ] Vier konfigurierte Zeilen
- [ ] Vorhandener Text mit overwrite an/aus
- [ ] Leere Zeilen mit vorhandenem Text
- [ ] Wiederöffnen desselben und eines neuen Schilds
- [ ] Zeilenlängen nahe 80 Zeichen
- [ ] Manuelles Speichern bleibt erforderlich

## Änderungsverlauf

| Datum | Änderung |
|---|---|
| 2026-11-16 | AutoSign-Modul und Screen-Mixin geprüft; keine Java-Codeänderung. |
