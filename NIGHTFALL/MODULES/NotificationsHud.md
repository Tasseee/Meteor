# NotificationsHud

- **Java-Klasse:** `NotificationsHud`
- **Sichtbarer Modulname:** `Notifications`
- **Auditstatus:** Vollständiger statischer Code-Audit; In-Game-Test offen
- **Quellpfad:** `src/main/java/dev/nightfall/modules/NotificationsHud.java`
- **Inventur-Commit:** `21dc9f822a6295cdb6238037f344da799bbcca26`
- **Registrierungsquelle:** `src/main/java/dev/nightfall/module/ModuleManager.java`

> Der aktuelle Quellcode wurde am 2026-11-17 erneut geprüft. Die Modulklasse heißt `NotificationsHud`; der in GUI und Konfiguration verwendete Name ist `Notifications`.

## Zweck und Verhalten

Zeigt die neuesten Status- und Warnmeldungen als HUD-Karten. Im HUD-Editor erscheint eine Beispielkarte, wenn keine Meldung aktiv ist. Das Modul aktiviert sich bereits im Konstruktor.

## Einstellungen

| Einstellung | Standardwert | Bedeutung | geprüft |
|---|---|---|---|
| Max. Meldungen | 4 | 1–6; Schritt 1 | Ja |

## Technische Prüfung

- [x] **Registrierung/Kategorie:** einmalige Registrierung in `ModuleManager.init()`; Kategorie HUD; sichtbarer Name `Notifications`.
- [x] **Einstellungen:** `Max. Meldungen`, Standard 4, Bereich 1–6, Schritt 1.
- [x] **UI/HUD:** neueste Meldungen zuerst, feste Breite 176 px, Höhe abhängig von der Anzahl; Editor-Vorschau bei leerer Queue.
- [x] **Lifecycle:** keine eigenen Enable-/Disable-/World-Leave-Hooks, Listener oder Ressourcen.
- [x] **Datenquelle:** `NotificationCenter` synchronisiert Push-/Snapshot-Zugriffe, begrenzt die Queue auf 12 und entfernt abgelaufene Einträge.
- [x] **Null-/Grenzfälle:** `trim()` behandelt null Texte; Toast-Fortschritt wird auf 0–1 begrenzt; Mindestdauer 750 ms.
- [x] **Fehlerbehandlung/Logging:** keine lokale Fehlerbehandlung; der zentrale HUD-Render-Handler protokolliert und deaktiviert bei Renderfehlern.
- [x] **Threading:** keine eigenen Threads; Queue-Methoden sind synchronisiert, Toast-Felder final.
- [x] **Performance:** höchstens 12 Toasts je Frame; Snapshot-Kopie ist durch Queue-Limit beschränkt.
- [x] **Kompatibilität:** Projektziel Minecraft 1.21.11 / Java 21; der CI-Build auf dem geprüften Java-Stand war erfolgreich.
- [x] **Tests/Dokumentation:** keine eigene `src/test`-Suite vorhanden; Dokumentation aktualisiert. In-Game-Verhalten bleibt offen.

## Nutzererfahrungen und Testberichte

Neue Beobachtungen als einzelne Berichte in `NIGHTFALL/TESTING/RESULTS/` speichern und hier verlinken.

| Test-ID | Datum | Umgebung | Ergebnis | Beleg |
|---|---|---|---|---|
| Noch keine Testberichte | – | – | Nicht getestet | – |

## Fehler und Verbesserungsideen

| ID | Beschreibung | Priorität | Status |
|---|---|---|---|
| Noch keine bestätigten Einträge | – | – | – |

## Änderungsverlauf

| Datum | Commit | Änderung | Nachweis |
|---|---|---|---|
| 2026-11-16 | `21dc9f8` | Modulakte angelegt; keine Java-Codeänderung | Dokumentationsinitialisierung |
