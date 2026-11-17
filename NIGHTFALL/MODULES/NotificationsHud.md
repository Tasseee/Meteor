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

- [ ] Registrierung und Kategorie
- [ ] Einstellungen, Grenzwerte und Standardwerte
- [ ] GUI-/HUD-Anzeige und Statusmeldungen
- [ ] Aktivieren/Deaktivieren und Weltwechsel
- [ ] Listener, Ressourcen und Cleanup
- [ ] Null-/Grenzfälle und Fehlerbehandlung
- [ ] Logging und Thread-Sicherheit
- [ ] Speicher-/Performance-Verhalten
- [ ] Versionskompatibilität
- [ ] Tests und Dokumentation

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
