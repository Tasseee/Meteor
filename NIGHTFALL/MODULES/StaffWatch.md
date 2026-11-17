# StaffWatch

- **Status:** Code verbessert; Build- und In-Game-Test offen
- **Java-Klasse / Anzeigename:** `StaffWatch`
- **Kategorie:** `HUD` (über `HudModule`)
- **Quellcode-SHA StaffWatch:** `6540dd03044809e53142736768919c8daa09c06d`
- **Tab-API-Änderung:** `getListedPlayerListEntries()` / `method_45732()`
- **Quellcode-SHA StaffPresence:** `0685ea75c3f989b54d22baabf1b5c2f84ffa45ca`
- **Quellcode-SHA StaffHudPanel:** `466201d2bafaa3eb94d584a0e99df2a9baaecd10`
- **Quellcode-SHA Commands:** `59afd025d8e0f718488ed595b92cd67673fe6973`
- **Quellcode-SHA AntiVanishHints:** `dfec4af070522db1385a52b524f2b22b7096d10c`
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
| `Max. HUD-Zeilen` | `8` (Min. `3`, Max. `12`) |
| `Kompaktes HUD` | `AN`/`AUS`, Standard `AUS`; reduziert Zeilen- und Kopfhoehe |
| `Sichtbare Treffer zuerst` | `AN`, Standard; sortiert nach TAB/WELT-Treffer, danach alphabetisch |
| `Legende anzeigen` | `AN`, Standard; blendet die Erklärungszeilen im normalen HUD ein/aus |
| `Untertitel anzeigen` | `AN`, Standard; blendet den Hinweis zur möglichen netzwerkweiten Tab-Liste ein/aus |
| `/nf staff add <name>` | Fügt einen Namen hinzu |
| `/nf staff del <name>` | Entfernt einen Namen |
| `/nf staff list` | Listet die eingetragenen Namen |

`ListSetting` verwendet ein `TreeSet`, normalisiert Namen mit `Locale.ROOT` auf Kleinschreibung und verhindert doppelte Einträge unabhängig von der ursprünglichen Groß-/Kleinschreibung. Dadurch funktioniert der spätere Vergleich gegen die ebenfalls kleingeschriebenen Tab- und Entity-Namen. Die gespeicherte Menge ist alphabetisch sortiert. Die Anzeige kann zwischen drei und zwoelf Zeilen zeigen. Mit `Sichtbare Treffer zuerst` werden Namen mit TAB-/WELT-Treffer vor Namen ohne aktuelle sichtbare Quelle sortiert; innerhalb derselben Gruppe bleibt die Reihenfolge alphabetisch. Die Command-Argumente verwenden `StringArgumentType.word()`, daher sind Leerzeichen in Namen nicht möglich. Eine zusätzliche Formatprüfung auf ein bestimmtes Minecraft-Namensmuster findet in `StaffWatch` nicht statt.

## HUD-Statuslogik

| Tab-Eintrag | Entity geladen | Anzeige |
|---|---|---|
| ja | ja | `TAB + WELT` |
| ja | nein | `TAB/NETZ` |
| nein | ja | `NUR WELT` |
| nein | nein | `KEINE QUELLE` |

Ist der Netzwerkhandler nicht verfügbar, zeigt das HUD stattdessen `TAB N/A` an und wertet nur die Entity-Quelle aus.

Das HUD ist jetzt ein `HudModule` in der Kategorie `HUD`. Standardposition ist `(8, 42)`. Im HUD-Editor kann es verschoben und skaliert werden. Die maximale Zeilenzahl ist ueber `Max. HUD-Zeilen` einstellbar (3–12); der Zaehler zeigt beispielsweise `8/14`, wenn weitere Namen in der Liste stehen. `Kompaktes HUD` reduziert die Zeilenhoehe, `Legende anzeigen` blendet die Erklaerung am unteren Rand ein oder aus. Die Vorschau zeigt bei leerer Liste einen Hinweis statt eines leeren Elements.

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

- **Lifecycle:** `ModuleManager.tick()` aktualisiert `StaffPresence` einmal zentral pro Client-Tick, wenn mindestens eines der beiden HUD-Module aktiv ist. `onWorldLeave()` setzt den gemeinsamen Snapshot zurück. Die einzelnen Module sammeln keine eigenen Tab-/Entity-Daten mehr.
- **Nullfälle:** Spieler und Welt werden vor dem Sammeln der Daten geprüft. Netzwerkhandler, Tab-Einträge, Profile und Profilnamen werden separat geprüft; leere Namen werden übersprungen.
- **Fehlerbehandlung:** Das Modul hat keinen lokalen Catch-Block. `ModuleManager.renderHud` fängt Render-Ausnahmen zentral ab, protokolliert sie und deaktiviert das betroffene Modul.
- **Threading:** Das Sammeln läuft in `onTick()` und die Anzeige liest den Cache im HUD-Renderpfad. Das Modul startet keine parallelen Tasks; beide Pfade laufen über den Client-Lifecycle.
- **Performance:** `StaffPresence` hält gemeinsame `HashSet`-/`HashMap`-Caches. Tab- und Entity-Namen werden einmal zentral pro Client-Tick aktualisiert, nicht doppelt in beiden Modulen und nicht bei jedem HUD-Render-Aufruf. Ein Profiling-Nachweis liegt nicht vor.
- **Speicherung:** `staffNames` wird als normale Modul-Einstellung in `nightfall.json` gespeichert und beim Config-Laden wiederhergestellt.

## Befunde

### SW-01 – Listenänderungen durch Chat-Befehle werden nicht sofort gespeichert

Vor der Korrektur veränderten `/nf staff add` und `/nf staff del` die `ListSetting`, ohne danach `Config.save()` aufzurufen. Dadurch konnte eine Änderung bei einem Absturz vor dem nächsten regulären Speicherzeitpunkt verloren gehen.

- **Bewertung:** Reproduzierbarer Befund aus dem Codepfad; kein In-Game-Absturztest durchgeführt.
- **Status:** Behoben. Bei erfolgreichem Hinzufügen oder Entfernen ruft der Command-Zweig jetzt direkt `Config.save()` auf. Bei einem Duplikat bzw. unbekannten Namen wird nicht unnötig gespeichert. Die Rückmeldung nennt das Speichern ausdrücklich.

### SW-02 – Überschneidung mit AntiVanishHints

Vor dem HUD-Upgrade wurde dieselbe manuelle Namensliste in einem zweiten festen HUD-Bereich angezeigt und die Tab-/Entity-Mengen wurden beim Rendern erneut aufgebaut. Das führte zu doppelter Anzeige und wiederholter Arbeit.

- **Bewertung:** Aus dem Quellcode ersichtliche Überschneidung; möglicherweise bewusst als getrennte HUD-Ansicht angelegt.
- **Status:** Behoben. `AntiVanishHints.draw()` blendet sein Panel aus, wenn `StaffWatch` aktiv ist. Das Modul ist inzwischen selbst ein `HudModule`; beide Module lesen denselben zentralen `StaffPresence`-Snapshot. `ModuleManager.tick()` sammelt Tab-/Welt-Daten genau einmal pro Client-Tick. AntiVanishHints blendet Namen ohne sichtbaren Tab- oder Welt-Treffer aus und zeigt bei null Treffern ausdrücklich `Nichts sichtbar – kein Beweis`.

### Grenzen der Interpretation

Ein fehlender Tab-Eintrag oder eine nicht geladene Player-Entity ist kein Vanish-Beweis und sagt allein nichts Zuverlässiges über Rolle, Absicht oder Moderationsstatus eines Spielers aus. Sichtbarkeit kann von Client-Weltzustand und Serververhalten abhängen. Die Anzeige bleibt daher ausdrücklich ein unverbindlicher Präsenzhinweis.

## HUD und Statusdarstellung

- Wechsel auf `HudModule`; das Element erscheint dadurch im HUD-Editor.
- Position und Skalierung werden über die vorhandene HUD-Konfiguration verwaltet.
- Panel nutzt das aktuelle `Theme`-Farbschema, konfigurierbare Zeilenanzahl, optionalen Kompaktmodus und eine ein-/ausblendbare Legende.
- `Sichtbare Treffer zuerst` sortiert ausschließlich anhand der bereits verwendeten TAB-/WELT-Sichtbarkeit; fehlende Daten werden nicht als Vanish-Befund interpretiert.
- Die Statusmengen werden pro Client-Tick aktualisiert und für das Rendern wiederverwendet.
- HUD-Vorschau im Editor zeigt bei einer leeren Liste den Hinweis `Keine Namen eingetragen`.
- Die HUD-Anzeige verwendet, sobald ein Name in TAB oder Welt gefunden wird, dessen originale Profil-Schreibweise. Der interne Vergleich bleibt kleingeschrieben und damit case-insensitive.
- Es werden weiterhin ausschließlich manuell eingetragene Namen und lokal sichtbare TAB-/Entity-Daten verarbeitet.

## Offene Tests

- [ ] Nur Tab-Eintrag (`TAB/NETZ`), nur Entity, beides und keine sichtbare Quelle
- [ ] Auf HUGSMP und DonutSMP prüfen, ob TAB-Einträge lokal oder netzwerkweit sind; `TAB/NETZ` darf nicht als bestätigter Unterserverstatus verstanden werden
- [ ] Groß-/Kleinschreibung sowie doppelte Einträge
- [ ] Profil-Schreibweise für Treffer in TAB und geladener Welt
- [ ] Konfigurierbare Anzeigegrenze (3–12), Treffer-Sortierung und Legende
- [ ] Disconnect und Weltwechsel
- [ ] Config-Speicherung direkt nach erfolgreichem `/nf staff add` und `/nf staff del` (inkl. Neustart)
- [ ] Beide Module gleichzeitig aktiviert: nur StaffWatch-Ausgabe; AntiVanishHints allein bei deaktiviertem StaffWatch
- [ ] AntiVanishHints im HUD-Editor verschieben, skalieren und nach Neustart wiederfinden
- [ ] HUD-Editor: verschieben, skalieren, Raster-Snap, Neustart und Vorschau ohne eingetragene Namen
- [ ] Ungültige/leere Konfigurationslisteneinträge: Zähler und Zeilenanzahl bleiben konsistent
- [ ] Build und In-Game-Test

## Änderungsverlauf

| Datum | Änderung |
|---|---|
| 2026-11-16 | Listenänderungen sofort gespeichert; doppelte AntiVanishHints-Ausgabe unterdrückt und deren Nullprüfungen ergänzt. HUD auf `HudModule` umgestellt, Statusdarstellung verbessert, Präsenzdaten pro Tick aktualisiert, Tab-Abgleich auf `getListedPlayerListEntries()` korrigiert, alleinigen TAB-Status als `TAB/NETZ` gekennzeichnet und Original-Schreibweise gefundener Profilnamen im HUD wiederhergestellt. |
| 2026-11-16 | Zeilenlimit, Kompaktmodus, Treffer-Sortierung und Legenden-Schalter für StaffWatch ergänzt; AntiVanishHints als verschiebbares `HudModule` mit Zeilenlimit, Kompaktmodus und Tick-Cache umgesetzt. Beide Panels bleiben auf manuell eingetragene Namen und clientseitig sichtbare TAB-/Entity-Daten beschränkt. |


## Gemeinsamer Präsenz-Cache (2026-11-17)

- `StaffPresence` ist eine gemeinsame Hilfsklasse für beide Module.
- `ModuleManager.tick()` aktualisiert den Snapshot einmal pro Client-Tick; beide HUDs lesen denselben Stand.
- Der Cache wird zurückgesetzt, wenn die Welt verlassen wird. Bei deaktivierten beiden Modulen werden keine Spielerlisten durchlaufen.
- AntiVanishHints zeigt nur Einträge mit einem sichtbaren TAB- oder geladenen Welt-Treffer. Keine Treffer ergeben den neutralen Hinweis `Nichts sichtbar – kein Beweis`.
- Keine zusätzliche Backend-Erkennung: `TAB/NETZ` bleibt eine Netzwerk-Spielerliste, `WELT` eine aktuell geladene Entity. Ein Unterserverstatus wird nicht geraten.

## Claude-HUD-Entwurf und Umsetzung (2026-11-17)

Der visuelle Entwurf aus der bereitgestellten ZIP wurde an Nightfalls vorhandene Modul-, Setting-, Theme- und HUD-Editor-Infrastruktur angepasst. Es wurde kein zweites Namenslisten- oder Command-System eingeführt: `StaffWatch.staffNames`, `ListSetting`, `/nf staff add|del|list`, `Config.save()` und der zentrale `StaffPresence`-Snapshot bleiben die einzige Datenbasis.

- Gemeinsamer Renderer: `src/main/java/dev/nightfall/modules/StaffHudPanel.java`.
- Dunkles Theme-Panel mit Akzentkante, Zähler-Badge, Status-Pills, dezenten alternierenden Zeilen und pulsierendem Punkt für `TAB + WELT`.
- Kompaktmodus, Zeilenlimit, Treffer-Sortierung, Legenden- und Untertitel-Schalter bleiben als echte Nightfall-Settings konfigurierbar.
- Leere Listen zeigen im HUD-Editor Beispielzeilen; im Spiel erscheint stattdessen ein Hinweis zum Eintragen von Namen.
- AntiVanishHints verwendet denselben Renderer und bleibt im Spiel ausgeblendet, wenn StaffWatch aktiv ist. Im HUD-Editor kann das Panel separat positioniert werden.

## Unterserver-Erkennung: Rechercheergebnis

Die Minecraft-Player-Info-/Tab-Daten, die der Client standardmäßig erhält, enthalten Profil-, Listen-, Spielmodus-, Ping-/Anzeigeinformationen, aber kein Feld, das für jeden gelisteten anderen Spieler dessen Proxy-Backend oder Unterserver benennt. Eine Client-Mod kann daraus keine verlässliche Spieler-zu-Unterserver-Zuordnung ableiten.

Ein Proxy-Plugin kann serverseitig den aktuellen Zielserver eines Spielers kennen. Der Client würde diese Information aber nur erhalten, wenn HugoSMP sie ausdrücklich und öffentlich an den jeweiligen Client übermittelt, beispielsweise über eine sichtbare Tab-Kennzeichnung oder einen offiziell dokumentierten Client-Kanal. In den öffentlich auffindbaren HugoSMP-Informationen wurde keine solche clientseitige Zuordnung dokumentiert. Eine In-Game-Verifikation auf HugoSMP steht aus.

Daher bleiben die Status bewusst begrenzt:
- `TAB/NETZ`: Profil ist in der an den Client gelieferten Liste; Backend unbekannt.
- `TAB + WELT`: Profil ist gelistet und eine passende Entity ist in der aktuell geladenen Client-Welt vorhanden.
- Ein TAB-Eintrag allein wird niemals als bestätigter Unterserverstatus ausgegeben. Es gibt keine Abfrage fremder Backend-Listen oder versteckter Spielerinformationen.

## Offene HUD-Prüfungen (2026-11-17)

- [ ] Build auf dem neuen gemeinsamen Panel und beiden Modulen
- [ ] In-Game: Name-/Statusüberlappung, lange Namen, kleine Auflösung und Skalierung
- [ ] HUD-Editor: Vorschau, verschieben, skalieren, speichern und Neustart
- [ ] HUGSMP: sichtbare Tab-Kennzeichnung / offizielle öffentliche Backend-Information suchen; keine Information erraten

- **Build-Korrektur 2026-11-17:** Fehlender Import von `ModuleManager` in `StaffPresence.java` ergänzt; der erste CI-Lauf des Panel-Einzelcommits hatte diesen Compilerfehler gemeldet. Neuer Build für den korrigierten Gesamtstand läuft.

## HugoSMP-Status und Nähe-Alarm (2026-11-17)

StaffWatch erkennt eine Verbindung zu HugoSMP über die offizielle Adresse `hugosmp.net` bzw. Subdomains davon. Grundlage ist die vom Spieler in Minecraft eingetragene Serveradresse (`ServerInfo.address`), nicht eine externe Serverabfrage. Wenn eine direkte IP statt `hugosmp.net` verwendet wird, kann der Client diese Verbindung nicht sicher als HugoSMP zuordnen und zeigt `HUGOSMP N/A`.

| Anzeige | Bedeutung |
|---|---|
| `ONLINE` | Der Name steht in der vom verbundenen HugoSMP-Server gelieferten gelisteten Tab-Spielerliste. |
| `SERVER` | Eine passende Spieler-Entity ist in deiner aktuell geladenen Client-Welt vorhanden. Das ist ein Hinweis auf denselben verbundenen Spielserver/Backend. |
| `OFFLINE` | HugoSMP-Verbindung und Tab-Daten sind verfügbar, aber weder Tab-Profil noch Entity liefern einen Treffer. |
| `TAB N/A` | Der Netzwerkhandler bzw. die Tab-Datenquelle ist nicht verfügbar. |
| `HUGOSMP N/A` | Die aktuelle Serveradresse ist nicht als `hugosmp.net` erkannt. |

`SERVER` ist kein ausgelesener Unterservername. Der Client kann damit sagen, dass die Entity in seiner aktuellen Welt geladen ist, aber nicht den Namen eines internen HugoSMP-Backends ermitteln. `ONLINE` hängt davon ab, welche Spieler HugoSMP in der an den Client gelieferten Tab-Liste aufführt; diese Liste kann lokal oder netzwerkweit sein.

### Nähe-Alarm

- Die Setting-Option `Nähe-Alarm` ist standardmäßig aktiviert und nur aktiv, wenn StaffWatch eingeschaltet ist.
- Eine Meldung kommt nur, wenn ein manuell eingetragener Name als Entity in der aktuellen Welt geladen wird und innerhalb der aktuellen Minecraft-Renderdistanz liegt (Client-View-Distance in Chunks × 16 Blöcke).
- Die Meldung lautet `<Name> is in your render distance` und wird beim Eintritt in den Bereich einmal ausgelöst. Verlässt der Spieler den Bereich und kommt später zurück, kann eine neue Meldung erscheinen.
- Die Anzeige erkennt Nähe, nicht Blickrichtung oder Absicht. Sie behauptet deshalb nicht, dass der Spieler tatsächlich zusieht.
- Es werden keine zusätzlichen Netzwerkdaten, versteckten Spielerlisten oder Backend-Abfragen verwendet.