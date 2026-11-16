# StaffWatch

- **Status:** Code geprüft; In-Game-Test offen
- **Java-Klasse / Anzeigename:** `StaffWatch`
- **Kategorie:** `MISC`
- **Quellcode-SHA:** `d872be110f93b70307decdd1bfc397ecaf1dc5d7`

## Zweck und Verhalten

Zeigt clientseitige Präsenzhinweise für Namen, die der Nutzer selbst in eine Liste einträgt. Das Modul vergleicht den Tab-Eintrag mit geladenen Player-Entities. Es behauptet ausdrücklich keine sichere Vanish-Erkennung.

## Einstellungen

| Einstellung | Standard | Verhalten |
|---|---|---|
| Beobachtete Namen | leere Liste | manuelle Namensliste |

Es werden maximal acht Einträge pro HUD-Zeichnung angezeigt.

## Statuslogik

| Tab-Eintrag | Entity geladen | Anzeige |
|---|---|---|
| ja | ja | Entity geladen + Tab-Eintrag |
| nein | ja | Entity geladen · kein Tab-Eintrag |
| ja | nein | Tab-Eintrag · Entity nicht geladen |
| nein | nein | weder Entity noch Tab · unklar |

## Technische Prüfung

- **Normalisierung:** Namen werden mit `Locale.ROOT` kleingeschrieben.
- **Datenquellen:** Tab-Liste und aktuell geladene Player-Entities.
- **Nullfälle:** Spieler/Welt und Netzwerkhandler werden geprüft.
- **Performance:** zwei HashSets werden pro HUD-Render-Aufruf erstellt; Liste selbst ist auf acht Namen begrenzt.
- **Threads/Ressourcen:** keine eigenen Threads oder Listener.
- **In-Game/Build:** nicht ausgeführt.

## Befunde und Verbesserungen

- Kein eindeutiger Fehler in der Präsenzlogik bestätigt.
- Die Einordnung ist ausdrücklich nur ein clientseitiger Hinweis. Fehlender Tab-/Entity-Eintrag kann mehrere Ursachen haben und ist keine Vanish-Bestätigung.
- Die beiden HashSets könnten bei dauerhaftem HUD-Betrieb als Felder wiederverwendet werden; ohne Profiling ist dies eine kleine Allokations-Optimierung.
- Keine automatische Staff-Erkennung oder Server-Umgehung ergänzt.

## Offene Tests

- [ ] nur Tab, nur Entity, beides, keines
- [ ] Groß-/Kleinschreibung
- [ ] mehr als acht Namen
- [ ] Disconnect / Weltwechsel
- [ ] Listen-Config speichern/laden

## Änderungsverlauf

| Datum | Änderung |
|---|---|
| 2026-11-16 | StaffWatch codebasiert geprüft; keine Java-Änderung. |
