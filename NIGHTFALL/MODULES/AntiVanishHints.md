# AntiVanishHints

- **Modulname:** `AntiVanishHints`
- **Kategorie:** `HUD` (über `HudModule`)
- **Quellpfad:** `src/main/java/dev/nightfall/modules/AntiVanishHints.java`
- **Quellcode-SHA:** `9ed794839c571599ec5a7f596617551d219dde23`
- **Registrierung:** `src/main/java/dev/nightfall/module/ModuleManager.java`
- **Gemeinsame Datenquelle:** `src/main/java/dev/nightfall/modules/StaffPresence.java`
- **Status:** Code aktualisiert und dokumentiert; Build- und In-Game-Test offen

## Zweck und Grenzen

Das Modul zeigt unverbindliche Präsenzhinweise zu den Namen, die in `StaffWatch.staffNames` manuell eingetragen wurden. Es prüft ausschließlich zwei Datenquellen, die der Minecraft-Client bereits erhalten hat:

1. Gelistete Profile der aktuellen Tab-Spielerliste (`getListedPlayerListEntries()`), falls ein Netzwerkhandler verfügbar ist.
2. Player-Entities, die in der aktuellen Client-Welt geladen sind.

Es gibt keine automatische Namenssuche, keine Erkennung von Staff-Rollen, keine zusätzlichen Serverabfragen und keinen Zugriff auf versteckte Spieler- oder Moderationsdaten. Ein fehlender Tab-Eintrag oder eine nicht geladene Entity beweist keinen Vanish und erlaubt keine zuverlässige Aussage über Rolle oder Aufenthaltsort.

Wenn `StaffWatch` aktiv ist, blendet `AntiVanishHints` sein eigenes Panel aus, damit nicht zwei fast identische Anzeigen übereinander liegen. Bei deaktiviertem StaffWatch kann dieses Modul dieselbe manuelle Liste separat darstellen. Es zeigt nur Namen mit einem aktuellen TAB- oder geladenen Welt-Treffer. Namen ohne sichtbaren Treffer werden nicht als Vanish-Kandidaten aufgelistet; stattdessen erscheint `Nichts sichtbar – kein Beweis`.

## Einstellungen

| Einstellung | Standard | Bereich / Wirkung |
|---|---:|---|
| `Max. HUD-Zeilen` | 5 | 3–10 sichtbare Namen; Zähler zeigt sichtbare/gesamte Einträge |
| `Kompaktes HUD` | AUS | Reduziert Kopf- und Zeilenhöhe |

Beide Einstellungen sind normale `Setting`-Einträge und werden über die vorhandene Config-Verwaltung gespeichert.

## Statusdarstellung

| Tab-Eintrag | Entity geladen | Label |
|---|---|---|
| ja | ja | `TAB + WELT` |
| ja | nein | `TAB/NETZ` |
| nein | ja | `NUR WELT` |
| nein | nein | `KEINE QUELLE` |

Ist der Netzwerkhandler nicht verfügbar, kennzeichnet das HUD die fehlende Tab-Quelle mit `TAB N/A`. Farbe unterstützt die Einordnung, ersetzt aber nicht die ausgeschriebenen Labels. Die Fusszeile erklärt die beiden Datenquellen und weist ausdrücklich darauf hin, dass Abwesenheit kein Vanish-Beweis ist.

## HUD und Lifecycle

- Das Modul erbt von `HudModule`, gehört damit zur HUD-Kategorie und kann im HUD-Editor verschoben und skaliert werden.
- Standardposition: `(8, 90)`.
- Das Panel verwendet das aktive `Theme`-Farbschema und passt seine Höhe an Zeilenlimit und Kompaktmodus an.
- `ModuleManager.tick()` aktualisiert `StaffPresence` einmal zentral pro Client-Tick, wenn StaffWatch oder AntiVanishHints aktiv ist. Beide HUD-Module lesen denselben Snapshot; `draw()` führt keine Tab-/Entity-Schleifen aus.
- `onWorldLeave()` setzt den gemeinsamen Snapshot zurück. Sind beide Module deaktiviert, wird keine Präsenzabfrage ausgeführt.
- Tab-Einträge, Profile, Profile-Namen, Welt-Spieler und Entity-Profile werden vor dem Zugriff auf fehlende Werte geprüft.
- Das Modul startet keine Threads, Executor oder zusätzlichen Netzwerkzugriffe.
- Wenn StaffWatch aktiv ist oder keine Welt vorhanden ist, wird das Panel ausgeblendet. Im HUD-Editor bleibt eine Vorschau bei leerer Namensliste sichtbar.

## Offene Tests

- [ ] HUD-Editor: verschieben, skalieren, Raster-Snap und Position nach Neustart
- [ ] Zeilenlimit 3 und 10 sowie mehr Einträge als angezeigt
- [ ] Kompaktmodus an/aus
- [ ] Statusfälle: nur TAB, nur geladene Entity, beides, keine Quelle und kein Netzwerkhandler
- [ ] Weltwechsel, Disconnect, Modulwechsel und gleichzeitige Aktivierung mit StaffWatch
- [ ] Config-Speicherung nach Änderung der beiden Einstellungen
- [ ] Build und In-Game-Test

## Änderungsverlauf

| Datum | Änderung |
|---|---|
| 2026-11-16 | Auf `HudModule` umgestellt; Zeilenlimit und Kompaktmodus ergänzt; Tab-/Entity-Daten werden pro Tick gecacht statt bei jedem Rendern neu gesammelt; Quellen- und Vanish-Hinweise deutlicher dargestellt. |


## Gemeinsame Datenverarbeitung (2026-11-17)

- StaffWatch und AntiVanishHints verwenden dieselbe Hilfsklasse `StaffPresence`.
- Die Aktualisierung erfolgt zentral einmal pro Client-Tick in `ModuleManager.tick()`; doppelte Scans der Tab-Liste und der Welt-Entities entfallen.
- AntiVanishHints filtert Einträge ohne sichtbaren TAB-/WELT-Treffer heraus und zeigt stattdessen `Nichts sichtbar – kein Beweis`.
- `TAB/NETZ` ist kein Unterservernachweis. `WELT` bedeutet nur, dass die Entity aktuell in der Client-Welt geladen ist.
