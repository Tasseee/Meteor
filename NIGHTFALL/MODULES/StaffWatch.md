# StaffWatch

- **Status:** Code verbessert; Build- und In-Game-Test offen
- **Java-Klasse / Anzeigename:** `StaffWatch`
- **Kategorie:** `MISC`
- **Quellcode-SHA StaffWatch:** `d872be110f93b70307decdd1bfc397ecaf1dc5d7` (unverändert)
- **Quellcode-SHA Commands:** `59afd025d8e0f718488ed595b92cd67673fe6973`
- **Quellcode-SHA AntiVanishHints:** `b220bed5952f3e7705dd85a2f0a2a986be538010`
- **Mitgeprüfte Dateien:** `ListSetting.java`, `Commands.java`, `Config.java`, `Nightfall.java`, `ModuleManager.java`, `AntiVanishHints.java`

## Zweck und Verhalten

Das Modul zeigt clientseitige Präsenzhinweise für Namen, die der Nutzer selbst einträgt. Es vergleicht diese Liste mit zwei bereits clientseitig verfügbaren Quellen:

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
| ja | ja | Entity geladen + Tab-Eintrag |
| nein | ja | Entity geladen · kein Tab-Eintrag |
| ja | nein | Tab-Eintrag · Entity nicht geladen |
| nein | nein | weder Entity noch Tab · unklar |

Das HUD beginnt bei Pixelposition `(8, 44)`. Es zeichnet eine Überschrift und maximal acht Namenszeilen. Fehlt der Netzwerkhandler, bleibt die Tab-Menge leer; die Welt-Entities werden weiterhin separat ausgewertet.

## Technische Prüfung

- **Lifecycle:** Kein eigener `onEnable`-/`onDisable`-Code, keine eigenen Listener und keine Hintergrundthreads.
- **Nullfälle:** Spieler und Welt werden vor dem Rendern geprüft. Der Netzwerkhandler wird separat auf `null` geprüft. Tab-Eintrag und Profilname werden vor der Übernahme in die Vergleichsmenge geprüft.
- **Fehlerbehandlung:** Das Modul hat keinen lokalen Catch-Block. `ModuleManager.renderHud` fängt Render-Ausnahmen zentral ab, protokolliert sie und deaktiviert das betroffene Modul.
- **Threading:** Der Zugriff erfolgt im HUD-Renderpfad auf den Client-/Weltzustand; das Modul startet keine parallelen Tasks.
- **Performance:** Pro Render-Aufruf werden zwei neue `HashSet`-Instanzen aufgebaut: einmal aus der Tab-Liste, einmal aus allen aktuell geladenen Player-Entities. Danach sind die Namensabfragen durchschnittlich konstant. Die HUD-Ausgabe ist auf acht Namen begrenzt, der Aufbau der beiden Mengen aber nicht. Ohne Profiling ist daraus kein relevanter Performancefehler belegt.
- **Speicherung:** `staffNames` wird als normale Modul-Einstellung in `nightfall.json` gespeichert und beim Config-Laden wiederhergestellt.

## Befunde

### SW-01 – Listenänderungen durch Chat-Befehle werden nicht sofort gespeichert

`/nf staff add` und `/nf staff del` verändern die `ListSetting` direkt. Die beiden Command-Zweige rufen danach nicht `Config.save()` auf. Die Config wird zwar beim Disconnect, beim Client-Shutdown und beim Schließen des ClickGUI gespeichert; ein Client-Absturz vor einem dieser Speicherzeitpunkte kann aber die letzte Listenänderung verlieren.

- **Bewertung:** Reproduzierbarer Befund aus dem Codepfad; kein In-Game-Absturztest durchgeführt.
- **Status:** Behoben. Bei erfolgreichem Hinzufügen oder Entfernen ruft der Command-Zweig jetzt direkt `Config.save()` auf. Bei einem Duplikat bzw. unbekannten Namen wird nicht unnötig gespeichert. Die Rückmeldung nennt das Speichern ausdrücklich.

### SW-02 – Überschneidung mit AntiVanishHints

`AntiVanishHints` liest dieselbe `StaffWatch.staffNames`-Liste und baut dieselben beiden Präsenzmengen erneut auf. Es zeigt bis zu fünf Namen in einem zweiten HUD-Bereich ab `y = 90`. Sind beide Module aktiviert, können die Hinweise daher doppelt bzw. in leicht anderer Form erscheinen und die Tab-/Entity-Mengen werden doppelt pro HUD-Frame aufgebaut.

- **Bewertung:** Aus dem Quellcode ersichtliche Überschneidung; möglicherweise bewusst als getrennte HUD-Ansicht angelegt.
- **Status:** Behoben. `AntiVanishHints.renderHud()` kehrt jetzt sofort zurück, wenn `StaffWatch` aktiviert ist. So bleibt bei gleichzeitig aktivierten Modulen nur die StaffWatch-Ausgabe sichtbar und der zweite Renderpfad baut nicht zusätzlich seine Vergleichsmengen auf. Ist StaffWatch deaktiviert, kann AntiVanishHints weiterhin die gemeinsame manuelle Liste anzeigen.

### Grenzen der Interpretation

Ein fehlender Tab-Eintrag oder eine nicht geladene Player-Entity ist kein Vanish-Beweis und sagt allein nichts Zuverlässiges über Rolle, Absicht oder Moderationsstatus eines Spielers aus. Sichtbarkeit kann von Client-Weltzustand und Serververhalten abhängen. Die Anzeige bleibt daher ausdrücklich ein unverbindlicher Präsenzhinweis.

## Offene Tests

- [ ] Nur Tab-Eintrag, nur Entity, beides und keines
- [ ] Groß-/Kleinschreibung sowie doppelte Einträge
- [ ] Mehr als acht eingetragene Namen (Sortierung und Anzeigegrenze)
- [ ] Disconnect und Weltwechsel
- [ ] Config-Speicherung direkt nach erfolgreichem `/nf staff add` und `/nf staff del` (inkl. Neustart)
- [ ] Beide Module gleichzeitig aktiviert: nur StaffWatch-Ausgabe; AntiVanishHints allein bei deaktiviertem StaffWatch
- [ ] Build und In-Game-Test

## Änderungsverlauf

| Datum | Änderung |
|---|---|
| 2026-11-16 | Zweitprüfung: erfolgreiche Listenänderungen werden sofort gespeichert; doppelte AntiVanishHints-Ausgabe bei aktivem StaffWatch unterdrückt. |
