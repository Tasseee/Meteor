# AutoBreed

- **Status:** Code geprüft; In-Game-/Build-Test offen
- **Java-Klasse / Anzeigename:** `AutoBreed`
- **Kategorie:** `WORLD`
- **Quellpfad:** `src/main/java/dev/nightfall/modules/AutoBreed.java`
- **Quellcode-SHA:** `229bf8a6a4f56e4486971e18dc8492efce320bcb`
- **Registrierung:** `ModuleManager.init()`

## Zweck und Verhalten

Das Modul sucht in der Nähe nach lebenden Tieren, die noch nicht im Liebesmodus sind und das gehaltene Item als Futter akzeptieren. Es wählt das nächstgelegene passende Tier und interagiert damit. Es führt keine Kampfaktion aus.

## Einstellungen

| Einstellung | Standard | Grenzen / Bedeutung |
|---|---:|---|
| Reichweite | 4 | 2–6 Blöcke; Schritt 1 |
| Intervall (Ticks) | 20 | 10–80 Ticks; Schritt 5 |

## Technische Prüfung

- **Voraussetzungen:** Spieler, Welt und Interaktionsmanager müssen vorhanden sein; die Haupthand darf nicht leer sein.
- **Filter:** lebendes Tier, nicht bereits im Liebesmodus und akzeptiert das gehaltene Futter.
- **Auswahl:** nächstes geeignetes Tier nach quadrierter Distanz.
- **Cooldown:** wird bei Aktivierung, Deaktivierung und Weltwechsel auf 0 gesetzt.
- **Nullfälle:** Voraussetzungen werden geprüft; leere Hand wird übersprungen.
- **Performance:** Suche erfolgt nur, wenn der Cooldown abgelaufen ist; die Entity-Abfrage ist auf eine erweiterte Spielerbox begrenzt.
- **Fehlerbehandlung/Logging:** kein lokaler catch; zentraler Tick-Handler greift.
- **Laufzeit/Version:** nicht im Spiel getestet; Build nicht ausgeführt.

## Befunde und Verbesserungen

- Kein eindeutiger Codefehler bei der ersten Prüfung bestätigt.
- Die Tick-Interaktion setzt den Cooldown unabhängig davon, ob die Interaktion serverseitig erfolgreich war. Das ist normales Clientverhalten, sollte aber bei fehlgeschlagener Interaktion im Spiel beobachtet werden.
- Der Intervallwert bezeichnet die Zahl der Cooldown-Ticks; der nächste Versuch erfolgt beim darauffolgenden Tick, wenn der Zähler wieder 0 ist. Das genaue Timing sollte im Spiel gegen die UI-Beschriftung geprüft werden.

## Nutzer- und In-Game-Tests

Offen: Tier außerhalb/innerhalb der Reichweite, verschiedene Futterarten, bereits verliebte Tiere, leere Hand, mehrere Tiere mit unterschiedlicher Distanz, Weltwechsel sowie Intervall-Minimum/Maximum.

## Änderungsverlauf

| Datum | Änderung |
|---|---|
| 2026-11-16 | Erste Codeprüfung; keine Java-Änderung in diesem Audit-Eintrag. |
