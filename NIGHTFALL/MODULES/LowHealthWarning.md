# LowHealthWarning

- **Status:** Code geprüft; In-Game-/Build-Test offen
- **Java-Klasse / Anzeigename:** `LowHealthWarning`
- **Kategorie:** `HUD`
- **Quellpfad:** `src/main/java/dev/nightfall/modules/LowHealthWarning.java`
- **Quellcode-SHA:** `956204ae986d82e57d4e6a1d2640c66ebdcef606`
- **Registrierung:** `ModuleManager.init()`

## Zweck und Verhalten

Zeigt bei niedrigem Lebensstand eine pulsierende HUD-Warnung und sendet beim Eintritt in den Warnzustand eine Notification. Das Modul aktiviert sich bereits im Konstruktor. Beim Weltwechsel wird der interne Warnzustand zurückgesetzt.

## Einstellungen

| Einstellung | Standard | Grenzen / Bedeutung |
|---|---:|---|
| Schwelle Herzen | 4,0 | 1,0–10,0; Schritt 0,5 |

Der Vergleich erfolgt in Minecraft-Lebenspunkten: Herzschwelle × 2.

## Technische Prüfung

- **HUD-Platzierung:** Standardposition horizontal zentriert, 36 px von oben; HUD-Breite passt sich dem Text an.
- **Animation:** Alpha-Puls basiert auf `System.currentTimeMillis()`; keine eigenen Timer oder Threads.
- **Nullfälle:** ohne Spieler wird `wasLow` zurückgesetzt; beim Zeichnen wird ohne Spieler keine Warnung gerendert.
- **Weltwechsel:** `wasLow` wird zurückgesetzt.
- **Hysterese:** nach dem Warnzustand wird erst zurückgesetzt, wenn die Lebenspunkte mehr als eine zusätzliche Herzschwelle über dem eingestellten Grenzwert liegen. Das reduziert wiederholte Meldungen nahe der Grenze.
- **Preview:** HUD-Editor zeigt bei vorhandenem Spieler eine Vorschau auch bei normalem Lebensstand.
- **Fehlerbehandlung/Logging:** keine eigene Fehlerbehandlung; der zentrale Render-/Tick-Handler greift.
- **Laufzeit/Version:** nicht in Minecraft getestet; Build nicht ausgeführt.

## Befunde und Verbesserungen

- Bei der Codeprüfung wurde kein eindeutiger Fehler bestätigt.
- Die Benachrichtigung beim ersten Tick mit niedrigem Leben und die Hysterese sollten im Spiel geprüft werden.
- Die Vorschau ist an einen vorhandenen Spieler gebunden. Ein HUD-Editor ohne Spieler zeigt keine Vorschau; ob das relevant ist, hängt vom Editor-Lifecycle ab.

## Nutzer- und In-Game-Tests

Offen: Aktivierung mit hohem/niedrigem Lebensstand, Schwellwertänderung zur Laufzeit, Erholung knapp oberhalb der Schwelle, Erholung über der Hysterese-Grenze, Weltwechsel und HUD-Editor ohne Spieler.

## Änderungsverlauf

| Datum | Änderung |
|---|---|
| 2026-11-16 | Erste Codeprüfung; keine Java-Änderung in diesem Audit-Eintrag. |
