# FriendManager

- **Status:** Code geprüft; Integrationstest offen
- **Java-Klasse / Anzeigename:** `FriendManager`
- **Kategorie:** `PLAYER`
- **Modul-SHA:** `3f84ff65c9af8e440642db12833f1c1bb042f246`
- **Setting-Hilfsklasse:** `ListSetting`, SHA `2cbad8f3a26d9adc85aff64cf3a900480a90c0a6`

## Zweck und Verhalten

Verwaltet eine persistierbare Liste von Spieler-Namen, die von anderen Modulen als Freunde abgefragt werden können. Das Modul führt selbst keine Tick- oder Render-Aktion aus.

## Einstellungen und Daten

| Einstellung | Standard | Verhalten |
|---|---|---|
| Freunde | leere Liste | Sortierte, kleingeschriebene Namensliste |

Beim Hinzufügen werden Namen auf das übliche Minecraft-Namensformat `[A-Za-z0-9_]{3,16}` geprüft. `ListSetting` speichert die Werte in einer `TreeSet` und normalisiert Groß-/Kleinschreibung mit `Locale.ROOT`.

## Technische Prüfung

- **Nullfälle:** `isFriend` und `removeFriend` behandeln `null` sicher.
- **Duplikate:** `TreeSet` verhindert doppelte Einträge nach Normalisierung.
- **Persistenz:** erfolgt über das gemeinsame Setting-/Config-System.
- **Performance:** Set-Lookups sind für die übliche Freundesliste effizient.
- **Lifecycle/Threads:** keine eigenen Listener, Threads oder Ressourcen.
- **Integration:** wird von CombatUtil und weiteren Modulen als Datenquelle gelesen; diese Prüfung verändert keine Kampfautomatisierung.

## Befunde und Verbesserungen

- Kein eindeutiger Fehler in der Listenverwaltung bestätigt.
- `removeFriend` validiert das Namensformat nicht erneut, normalisiert aber über `ListSetting`; ein nicht passender Name wird einfach nicht gefunden. Das ist unkritisch.
- Für die GUI sollte ein klarer Hinweis vorhanden sein, dass die Liste case-insensitive ist.

## Offene Tests

- [ ] Hinzufügen gültiger/ungültiger Namen
- [ ] Groß-/Kleinschreibung und Duplikate
- [ ] Entfernen mit unterschiedlicher Schreibweise
- [ ] Config speichern/laden
- [ ] Abfrage in zulässigen Modulen (ohne Änderung an Kampfverhalten)

## Änderungsverlauf

| Datum | Änderung |
|---|---|
| 2026-11-16 | FriendManager und ListSetting geprüft. |
