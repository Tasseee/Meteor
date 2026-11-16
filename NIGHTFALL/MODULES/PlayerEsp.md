# PlayerEsp

- **Status:** Code geprüft; In-Game-Test offen
- **Java-Klasse:** `PlayerEsp`
- **Anzeigename:** `PlayerESP`
- **Kategorie:** `VISUALS`
- **Modul-SHA:** `b62ccb45777f98b8cf5df81a2549d8bced59ab78`
- **Gemeinsame Renderlogik:** `EntityScanEsp` / `EspBase`

## Zweck und Verhalten

Markiert andere geladene Spieler. Zusätzlich besitzt das Modul eine Funktion `Auto Invsee`, die bei passenden Spielern automatisch den Chatbefehl `/invsee <name>` sendet. Ob der Befehl funktioniert, hängt vollständig von den Serverrechten und Server-Plugins ab; der Client liest keine fremden Inventare direkt aus.

## Einstellungen

| Einstellung | Standard |
|---|---:|
| Auto Invsee | an |
| Health (geerbt) | an |
| Nur sichtbare (geerbt) | aus |
| Reichweite (korrigierter Default) | 256 |
| Max Objekte | 512 |
| Box / Name / Distanz | geerbte ESP-Einstellungen |

## Technische Prüfung

- **Namensvalidierung:** akzeptiert nur Namen mit 3–16 ASCII-Buchstaben/Ziffern/Unterstrichen.
- **Deduplizierung:** sendet den Befehl pro Name höchstens einmal je Weltobjekt; Set wird bei Weltwechsel, fehlender Verbindung und Deaktivierung geleert.
- **Sichtbarkeit:** bei aktivierter geerbter Option prüft Projection die Sichtlinie.
- **Serverinteraktion:** Befehl wird über die bestehende Verbindung gesendet; es gibt keine Bestätigung, dass der Server ihn akzeptiert.
- **Lifecycle:** trackedWorld wird beim Weltwechsel aktualisiert und beim Deaktivieren gelöscht.
- **Build/In-Game:** nicht ausgeführt.

## Befunde und Verbesserungen

### PE-01 – Standardreichweite aus gemeinsamem Konstruktor war falsch

**Behoben im Code:** `c03f2c5205b1a6bec2c01d23ad48cdc4fe488a8d`.

Der gemeinsame Konstruktor ignorierte den vorgesehenen Default 256 und setzte die maximale Reichweite 512. Der Default ist jetzt korrekt 256.

### PE-02 – Auto-Invsee ist eine serverseitige Aktion

Die Option steht standardmäßig auf an und sendet automatisch einen Serverbefehl. Es wird nicht geprüft, ob der Server diesen Befehl für den Nutzer erlaubt. Diese Funktion wird nicht zur Umgehung von Serverrechten oder Schutzmechanismen erweitert; im Zweifel deaktivieren und nur mit entsprechender Berechtigung nutzen.

## Offene Tests

- [ ] Auto Invsee deaktiviert/aktiviert
- [ ] Spielername valid/invalid
- [ ] mehrere Spieler in Reichweite
- [ ] Wechsel von Welt/Server und Deduplizierung
- [ ] Sichtbarkeitsfilter
- [ ] Reichweite-Default 256 und Maximum 512

## Änderungsverlauf

| Datum | Änderung |
|---|---|
| 2026-11-16 | PlayerEsp und Shared-Renderpfad geprüft; Range-Default korrigiert. |
