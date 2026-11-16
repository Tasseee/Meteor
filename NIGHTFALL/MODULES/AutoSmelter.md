# AutoSmelter

- **Status:** Code geprüft; In-Game-/Build-Test offen
- **Java-Klasse / Anzeigename:** `AutoSmelter`
- **Kategorie:** `WORLD`
- **Quellpfad:** `src/main/java/dev/nightfall/modules/AutoSmelter.java`
- **Quellcode-SHA:** `18ee507a64c4fa5ef3005bcfb93f645148607675`
- **Registrierung:** `ModuleManager.init()`

## Zweck und Verhalten

Das Modul versucht in einem geöffneten Ofen-Menü passende Items in den Eingabe-Slot und Brennstoff in den Brennstoff-Slot zu verschieben. Es verwendet schnelle Slot-Übertragungen und führt pro Intervall höchstens eine Übertragung aus.

Es prüft den ScreenHandler-Typ `class_1720`, die Existenz von Spieler und Interaktionsmanager sowie die jeweiligen Slot-Inhalte. Ob die Filter alle relevanten Rezepte und Ofenvarianten abdecken, muss im Spiel geprüft werden.

## Einstellungen

| Einstellung | Standard | Verhalten |
|---|---:|---|
| Intervall (Ticks) | 10 | Grenzen 5–40, Schrittweite 1 |

## Technische Prüfung

- **Lebenszyklus:** Tickzähler wird beim Aktivieren, Deaktivieren **und beim Weltwechsel** zurückgesetzt.
- **Null-/Menüprüfung:** Spieler, Interaktionsmanager und ScreenHandler-Typ werden geprüft.
- **Slotlogik:** Slot 0 wird als Eingabe und Slot 1 als Brennstoff behandelt; Inventarsuche beginnt bei Slot 3.
- **Filter:** Schmelzbares wird anhand von Item-ID-Namensmustern erkannt; Brennstoff anhand einer festen Liste von Kohle, Blaze Rods, Planken, Logs und Wood.
- **Fehlerbehandlung/Logging:** kein lokales try/catch und kein modulspezifisches Logging; Fehler würden vom zentralen Modul-Tick-Handler behandelt.
- **Threads/Ressourcen:** keine eigenen Threads oder gehaltenen Ressourcen.
- **Laufzeit/Version:** nicht im Spiel getestet; Build nicht ausgeführt.

## Befunde und Verbesserungen

- **Kein bestätigter Fehler** bei der statischen Codeprüfung.
- Die Schmelzbarkeit wird über Item-ID-Namensmuster statt über die tatsächlichen Rezepte erkannt. Bei modifizierten Rezepten kann die Erkennung unvollständig sein. Das ist ein dokumentierter Einschränkungspunkt, noch kein reproduzierter Fehler.
- Das Intervall zählt nur Ticks, in denen ein passendes Ofen-Menü geöffnet ist und die Voraussetzungen erfüllt sind.

## Nutzer- und In-Game-Tests

Offen: normaler Ofen, Hochofen/Räucherofen (soweit unterstützt), leere/volle Slots, Inventar ohne passende Items, Weltwechsel während aktivem Modul und Intervall-Minimum/Maximum.

## Änderungsverlauf

| Datum | Änderung |
|---|---|
| 2026-11-16 | Erste Codeprüfung; keine Java-Codeänderung. |
