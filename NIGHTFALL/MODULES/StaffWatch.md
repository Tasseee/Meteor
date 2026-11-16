# StaffWatch

- **Status:** Code verbessert; Build- und In-Game-Test offen
- **Java-Klasse / Anzeigename:** `StaffWatch`
- **Kategorie:** `HUD` (über `HudModule`)
- **Quellcode-SHA StaffWatch:** `b17447e60f35d4df145c072c7890fa73887ec607`
- **Tab-API-Änderung:** `getListedPlayerListEntries()` / `method_45732()`
- **Quellcode-SHA Commands:** `59afd025d8e0f718488ed595b92cd67673fe6973`
- **Quellcode-SHA AntiVanishHints:** `dfd43801f0544d7e51ce2cab031a1c6a6538d997`
- **Mitgeprüfte Dateien:** `ListSetting.java`, `Commands.java`, `Config.java`, `Nightfall.java`, `ModuleManager.java`, `AntiVanishHints.java`

## Zweck und Verhalten

Das Modul zeigt clientseitige Präsenzhinweise für Namen, die der Nutzer selbst einträgt. Es identifiziert keine Staff-Konten und erkennt keinen serverseitig versteckten Status. Es vergleicht nur die manuelle Liste mit zwei bereits clientseitig verfügbaren Quellen:

1. Namen in der aktuellen Tab-Spielerliste (sofern ein Netzwerkhandler vorhanden ist).
2. Namen von Player-Entities, die in der aktuellen Client-Welt geladen sind.

Das Modul stellt weder fest, ob ein Name tatsächlich zu einem Teammitglied gehört, noch bestätigt es Vanish. Es fragt keine zusätzlichen Serverdaten ab und enthält keine automatische Namens- oder Staff-Erkennung.

## Einstellungen und Bedienung

| Einstellung / Befehl | Standard / Verhalten |
|---|---|
| `Beobachtete Namen` | Leere `ListSetting` |
| `/nf staff add <name>` | Fügt einen Namen hinzu |
| `/nf staff del <name>` | Entfernt einen Namen |
| `/nf staff list` | Listet die eingetragenen Namen |

`ListSetting` verwendet ein `TreeSet`, normalisiert Namen mit `Locale.ROOT` auf Kleinschreibung und verhindert doppelte Einträge unabhängig von der ursprünglichen Groß-/Kleinschreibung. Dadurch funktioniert der spätere Vergleich gegen die ebenfalls kleingeschriebenen Tab- und Entity-Namen. Die Menge ist alphabetisch sortiert; die HUD-Anzeige zeigt die ersten acht Einträge. Die Command-Argumente verwenden `StringArgumentType.word()`, daher sind Leerzeichen in Namen nicht möglich. Eine zusätzliche Formatprüfung auf ein bestimmtes Minecraft-Namensmuster findet in `StaffWatch` nicht statt.

## HUD-Statuslogik

| Tab-Eintrag | Entity geladen | Anzeige |
|---|---|---|
| ja | ja | `TAB + WELT` |
| ja | nein | `TAB/NETZ` |
| nein | ja | `NUR WELT` |
| nein | nein | `KEINE QUELLE` |

Ist der Netzwerkhandler nicht verfügbar, zeigt das HUD stattdessen `TAB N/A` an und wertet nur die Entity-Quelle aus.

Das HUD ist jetzt ein `HudModule` in der Kategorie `HUD`. Standardposition ist `(8, 42)`. Im HUD-Editor kann es verschoben und skaliert werden. Es zeigt maximal acht Namenszeilen und im Zähler beispielsweise `8/14`, wenn weitere Namen in der Liste stehen. Die Vorschau zeigt bei leerer Liste einen Hinweis statt eines leeren Elements.

Die Statuslabels unterscheiden `TAB + WELT`, `TAB/NETZ`, `NUR WELT` und `KEINE QUELLE`. Wenn kein Netzwerkhandler verfügbar ist, werden `TAB N/A`-Labels verwendet, damit ein fehlender Datenkanal nicht wie ein negativer Tab-Befund aussieht. Farben ergänzen die Textlabels, sind aber nicht die einzige Statusinformation.

## Tab-API-Prüfung (Minecraft 1.21.11)

Das Projekt baut gegen Minecraft `1.21.11` und das Intermediary-Mappings-Artefakt derselben Version.

- `class_634.method_2880()` ist in den 1.21.11-Mappings `ClientPlayNetworkHandler.getPlayerList()`.
- `class_634.method_45732()` ist `ClientPlayNetworkHandler.getListedPlayerListEntries()`.
- `class_640.method_2966()` ist `PlayerListEntry.getProfile()`; daraus wird der Profilname gelesen.
- `class_638.method_18456()` ist `ClientWorld.getPlayers()`, also die lokal geladenen Spieler-Entities.

Für den Status `TAB` ist `getListedPlayerListEntries()` die passendere Quelle als die allgemeinere `getPlayerList()`-Sammlung. StaffWatch und AntiVanishHints nutzen jetzt `method_45732()`. Das ist anhand der Yarn-1.21.11-Mappings bestätigt; ein In-Game-Abgleich ist noch offen. Die API-Zuordnung beweist nicht, dass ein Name Teammitglied ist oder dass ein nicht gelisteter Name versteckt wird. Außerdem kann ein Netzwerk die Tab-Liste so konfigurieren, dass sie Spieler mehrerer Unterserver enthält. Das Modul kann allein anhand dieser Einträge nicht erkennen, welchem Backend ein Eintrag zugeordnet ist.


## HUG-/Donut-SMP-Einordnung

StaffWatch hat absichtlich keine fest verdrahtete Sondererkennung für HUGSMP oder DonutSMP. Beide Server werden mit denselben zwei klar getrennten Clientquellen ausgewertet:

- `TAB/NETZ`: Der Name steht in den vom Server an den Client übermittelten, gelisteten Tab-Einträgen. Das kann eine lokale oder eine netzwerkweite Liste sein.
- `WELT`: Eine passende Spieler-Entity ist in der aktuell geladenen Client-Welt vorhanden.

Ein Eintrag mit `TAB/NETZ` allein sagt daher nicht, dass der Spieler auf genau dem Unterserver ist, auf dem du gerade spielst. `TAB + WELT` ist ein deutlich stärkerer Hinweis auf die aktuelle Welt. Auch `NUR WELT` und `KEINE QUELLE` sind nur Momentaufnahmen der geladenen Clientdaten. Die tatsächliche TAB-Konfiguration von HUGSMP bzw. DonutSMP kann ohne In-Game-Beobachtung auf dem jeweiligen Server nicht als lokal oder netzwerkweit bestätigt werden.

## Technische Prüfung

- **Lifecycle:** `onEnable()`, `onDisable()` und `onWorldLeave()` leeren den zwischengespeicherten Präsenzzustand. `onTick()` aktualisiert ihn nur bei aktivem Modul. Keine eigenen Event-Listener, Executor oder Hintergrundthreads.
- **Nullfälle:** Spieler und Welt werden vor dem Sammeln der Daten geprüft. Netzwerkhandler, Tab-Einträge, Profile und Profilnamen werden separat geprüft; leere Namen werden übersprungen.
- **Fehlerbehandlung:** Das Modul hat keinen lokalen Catch-Block. `ModuleManager.renderHud` fängt Render-Ausnahmen zentral ab, protokolliert sie und deaktiviert das betroffene Modul.
- **Threading:** Das Sammeln läuft in `onTick()` und die Anzeige liest den Cache im HUD-Renderpfad. Das Modul startet keine parallelen Tasks; beide Pfade laufen über den Client-Lifecycle.
- **Performance:** Zwei `HashSet`-Instanzen werden als Felder wiederverwendet. Tab- und Entity-Namen werden im `onTick()` aktualisiert, nicht mehr bei jedem HUD-Render-Aufruf. Pro aktivem Tick werden die vom Client als gelistet geführten Tab-Einträge und die aktuell geladenen Player-Entities durchlaufen. Ein Profiling-Nachweis liegt nicht vor.
- **Speicherung:** `staffNames` wird als normale Modul-Einstellung in `nightfall.json` gespeichert und beim Config-Laden wiederhergestellt.

## Befunde

### SW-01 – Listenänderungen durch Chat-Befehle werden nicht sofort gespeichert

Vor der Korrektur veränderten `/nf staff add` und `/nf staff del` die `ListSetting`, ohne danach `Config.save()` aufzurufen. Dadurch konnte eine Änderung bei einem Absturz vor dem nächsten regulären Speicherzeitpunkt verloren gehen.

- **Bewertung:** Reproduzierbarer Befund aus dem Codepfad; kein In-Game-Absturztest durchgeführt.
- **Status:** Behoben. Bei erfolgreichem Hinzufügen oder Entfernen ruft der Command-Zweig jetzt direkt `Config.save()` auf. Bei einem Duplikat bzw. unbekannten Namen wird nicht unnötig gespeichert. Die Rückmeldung nennt das Speichern ausdrücklich.

### SW-02 – Überschneidung mit AntiVanishHints

`AntiVanishHints` liest dieselbe `StaffWatch.staffNames`-Liste und baut dieselben beiden Präsenzmengen erneut auf. Es zeigt bis zu fünf Namen in einem zweiten HUD-Bereich ab `y = 90`. Sind beide Module aktiviert, können die Hinweise daher doppelt bzw. in leicht anderer Form erscheinen und die Tab-/Entity-Mengen werden doppelt pro HUD-Frame aufgebaut.

- **Bewertung:** Aus dem Quellcode ersichtliche Überschneidung; möglicherweise bewusst als getrennte HUD-Ansicht angelegt.
- **Status:** Behoben. `AntiVanishHints.renderHud()` kehrt jetzt sofort zurück, wenn `StaffWatch` aktiviert ist. So bleibt bei gleichzeitig aktivierten Modulen nur die StaffWatch-Ausgabe sichtbar und der zweite Renderpfad baut nicht zusätzlich seine Vergleichsmengen auf. Ist StaffWatch deaktiviert, kann AntiVanishHints weiterhin die gemeinsame manuelle Liste anzeigen.

### Grenzen der Interpretation

Ein fehlender Tab-Eintrag oder eine nicht geladene Player-Entity ist kein Vanish-Beweis und sagt allein nichts Zuverlässiges über Rolle, Absicht oder Moderationsstatus eines Spielers aus. Sichtbarkeit kann von Client-Weltzustand und Serververhalten abhängen. Die Anzeige bleibt daher ausdrücklich ein unverbindlicher Präsenzhinweis.

## HUD und Statusdarstellung

- Wechsel auf `HudModule`; das Element erscheint dadurch im HUD-Editor.
- Position und Skalierung werden über die vorhandene HUD-Konfiguration verwaltet.
- Panel nutzt das aktuelle `Theme`-Farbschema, kompakte Zeilen und eine Legende.
- Die Statusmengen werden pro Client-Tick aktualisiert und für das Rendern wiederverwendet.
- HUD-Vorschau im Editor zeigt bei einer leeren Liste den Hinweis `Keine Namen eingetragen`.
- Es werden weiterhin ausschließlich manuell eingetragene Namen und lokal sichtbare TAB-/Entity-Daten verarbeitet.

## Offene Tests

- [ ] Nur Tab-Eintrag (`TAB/NETZ`), nur Entity, beides und keine sichtbare Quelle
- [ ] Auf HUGSMP und DonutSMP prüfen, ob TAB-Einträge lokal oder netzwerkweit sind; `TAB/NETZ` darf nicht als bestätigter Unterserverstatus verstanden werden
- [ ] Groß-/Kleinschreibung sowie doppelte Einträge
- [ ] Mehr als acht eingetragene Namen (Sortierung und Anzeigegrenze)
- [ ] Disconnect und Weltwechsel
- [ ] Config-Speicherung direkt nach erfolgreichem `/nf staff add` und `/nf staff del` (inkl. Neustart)
- [ ] Beide Module gleichzeitig aktiviert: nur StaffWatch-Ausgabe; AntiVanishHints allein bei deaktiviertem StaffWatch
- [ ] HUD-Editor: verschieben, skalieren, Raster-Snap, Neustart und Vorschau ohne eingetragene Namen
- [ ] Ungültige/leere Konfigurationslisteneinträge: Zähler und Zeilenanzahl bleiben konsistent
- [ ] Build und In-Game-Test

## Änderungsverlauf

| Datum | Änderung |
|---|---|
| 2026-11-16 | Zweitprüfung: Listenänderungen sofort gespeichert; doppelte AntiVanishHints-Ausgabe unterdrückt. HUD auf `HudModule` umgestellt, Statusdarstellung verbessert, Präsenzdaten pro Tick aktualisiert, Tab-Abgleich auf `getListedPlayerListEntries()` korrigiert und den alleinigen TAB-Status als `TAB/NETZ` gekennzeichnet. |
