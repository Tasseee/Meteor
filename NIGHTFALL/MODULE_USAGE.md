# Modul-Nutzungsanzeige

- **Implementierung:** `ModuleUsageStats`
- **GUI:** `ClickGuiScreen.drawModuleRow()`
- **Integration:** `Module.setEnabled()` und `Nightfall`
- **Speicherort:** `config/nightfall/module-usage.json`
- **Geltungsbereich:** alle registrierten Module

## Was angezeigt wird

In jeder Modulzeile des ClickGUI steht unter dem Modulnamen eine Kurzangabe:

`Aktivierungen × · gesamte aktive Zeit`

Beispiele: `0x · 0s`, `4x · 17m`, `12x · 2h 35m`, `3x · 1d 4h`.

Die Zeile zeigt die Zeit live einschließlich der aktuell laufenden Aktivierungsphase.

## Definitionen

- **Aktivierungen:** Anzahl der Übergänge von deaktiviert zu aktiviert. Module, die nach dem Laden der gespeicherten Konfiguration bereits aktiv sind, werden beim Start als eine Aktivierung gezählt.
- **Aktive Zeit:** aufsummierte Zeit, während der das Modul eingeschaltet war. Sie ist keine Zählung einzelner Aktionen und bedeutet nicht, dass das Modul in jedem Moment tatsächlich etwas ausgeführt hat.
- Die Zeit läuft auch in Menüs und während einer Servertrennung weiter, solange das Modul eingeschaltet bleibt.

## Speicherung und Datenschutz

- Alle Daten bleiben lokal im Minecraft-Konfigurationsordner.
- Pro Modul werden nur Aktivierungszahl und aktive Millisekunden gespeichert.
- Es werden keine Spieler-/Kontonamen, Serveradressen, Welten, Chatnachrichten, Ziele, Aktionen oder Modulresultate protokolliert.
- Gespeichert wird spätestens alle 15 Sekunden, außerdem beim Disconnect und beim regulären Clientende.
- Bei einem harten Prozessabbruch kann der letzte Zeitraum seit der letzten Speicherung fehlen.

## Grenzen

- Die Daten beginnen ab der Version mit dieser Funktion; frühere Nutzung wird nicht rückwirkend rekonstruiert.
- Der Zähler misst Aktivierungsphasen, nicht die Anzahl von Klicks, Treffern, Scans oder anderen Modulaktionen.
- Ein separates Zurücksetzen einzelner Modulzähler ist aktuell nicht vorhanden.

## Änderungsverlauf

| Datum | Änderung |
|---|---|
| 2026-11-16 | Lokales Aktivierungs- und Laufzeittracking für alle Module integriert; Anzeige in jeder ClickGUI-Modulzeile. |
