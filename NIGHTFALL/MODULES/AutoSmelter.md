# AutoSmelter

- **Status:** Code geprüft; In-Game-/Build-Test offen
- **Java-Klasse / Anzeigename:** `AutoSmelter`
- **Kategorie:** `WORLD`
- **Quellpfad:** `src/main/java/dev/nightfall/modules/AutoSmelter.java`
- **Quellcode-SHA:** `18ee507a64c4fa5ef3005bcfb93f645148607675`
- **Registrierung:** `ModuleManager.init()`

## Zweck und Verhalten

Das Modul versucht in einem geöffneten Ofen-Menü passende Items in den Eingabe-Slot und Brennstoff in den Brennstoff-Slot zu verschieben. Es verwendet schnelle Slot-Übertragungen und führt pro Intervall höchstens eine Übertragung aus.

Es prüft den ScreenHandler-Typ `class_1720`, die Existenz von Spieler und Interaktionsmanager sowie die jeweiligen Slot-Inhalte. Aus dem Code allein ist keine Bestätigung möglich, dass sich das Verhalten für alle Ofenvarianten und Server-Menüs korrekt anfühlt.

## Einstellungen

| Einstellung | Standard | Verhalten |
|---|---:|---|
| Intervall (Ticks) | 10 | Grenzen 5–40, Schrittweite 1 |

## Technische Prüfung

- **Lebenszyklus:** Tickzähler wird beim Aktivieren und Deaktivieren zurückgesetzt.
- **Weltwechsel:** `onWorldLeave()` wird nicht überschrieben; der Tickzähler wird bei einem Weltwechsel daher nicht explizit zurückgesetzt.
- **Null-/Menüprüfung:** Spieler, Interaktionsmanager und ScreenHandler-Typ werden geprüft.
- **Slotlogik:** Slot 0 wird als Eingabe und Slot 1 als Brennstoff behandelt; Inventarsuche beginnt bei Slot 3.
- **Filter:** Schmelzbares wird anhand von Item-ID-Namensmustern erkannt; Brennstoff anhand einer festen Liste von Kohle, Blaze Rods, Planken, Logs und Wood.
- **Fehlerbehandlung/Logging:** kein lokales try/catch und kein modulspezifisches Logging; Fehler würden vom zentralen Modul-Tick-Handler behandelt.
- **Threads/Ressourcen:** keine eigenen Threads oder gehaltenen Ressourcen.
- **Laufzeit/Version:** nicht im Spiel getestet; Build nicht ausgeführt.

## Befunde und Verbesserungen

### Verbesserungskandidat AS-01 – Tickzähler beim Weltwechsel zurücksetzen

Anders als beispielsweise AutoBreed setzt AutoSmelter seinen internen Zähler bei `onWorldLeave()` nicht zurück. Ein Restwert kann dadurch in die nächste Welt bzw. Menü-Sitzung übernommen werden. Das ist kein belegter Absturzfehler, kann aber dazu führen, dass die erste Übertragung nach dem Öffnen eines Ofens früher als erwartet erfolgt.

**Empfehlung:** `onWorldLeave()` ergänzen und `ticks` auf 0 setzen. Vor Umsetzung sollte das erwartete Intervallverhalten im Spiel geprüft werden.

### Verbesserungskandidat AS-02 – Rezeptabdeckung

Die Schmelzbarkeit wird über Namensmuster statt über die tatsächlichen Rezepte des geöffneten Ofens erkannt. Dadurch können Items fehlen oder bei modifizierten Rezeptlisten falsch eingeordnet werden. Das ist eine bekannte Einschränkung der Filterstrategie, aber ohne konkrete In-Game-Beispiele noch kein bestätigter Fehler.

## Nutzer- und In-Game-Tests

Offen: normaler Ofen, Hochofen/Räucherofen (soweit vom ScreenHandler unterstützt), leere/volle Slots, Inventar ohne passende Items, Weltwechsel während aktivem Modul.

## Änderungsverlauf

| Datum | Änderung |
|---|---|
| 2026-11-16 | Erste Codeprüfung; keine Java-Änderung in diesem Audit-Eintrag. |
