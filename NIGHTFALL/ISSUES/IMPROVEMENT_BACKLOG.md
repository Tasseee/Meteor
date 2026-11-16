# Fehler- und Verbesserungsliste

Jeder Eintrag erhält eine eindeutige ID. Nutzerberichte bleiben als solche markiert, bis sie reproduziert oder anderweitig belegt wurden.

## Statusmodell

`Neu` → `In Untersuchung` → `Bestätigt` → `Geplant` → `Umgesetzt` → `Verifiziert`

Nicht bestätigte Beobachtungen dürfen nicht als Bugs oder behobene Probleme ausgegeben werden.

## Priorität

- **P0 – kritisch:** Datenverlust, reproduzierbarer Absturz oder schwerer Integritätsfehler.
- **P1 – hoch:** zentrale Funktion fällt aus oder verursacht erhebliche Störungen.
- **P2 – normal:** reproduzierbarer Funktionsfehler oder deutliche Qualitätsminderung.
- **P3 – niedrig:** kleinere UX-, Dokumentations- oder Wartungsverbesserung.

## Erste offene Beobachtungen

### NF-OBS-0001 – OreFinder / OreESP: Namenszuordnung und Anzeigeverhalten

- **Modulregistrierung:** In `ModuleManager.init()` ist `OreESP` registriert. Ein eigenständiges Modul `OreFinder` ist dort nicht registriert.
- **Nutzerbericht:** Auf HugoSMP würden Erze offenbar erst angezeigt/erkannt, wenn man direkt davor steht.
- **Status:** Nutzerbericht, ungeprüft.
- **Noch unklar:** Ob „OreFinder“ die GUI-Bezeichnung von `OreESP`, eine andere Funktion oder eine frühere Benennung meint; konkrete Testbedingungen fehlen.
- **Nächster Schritt:** Modulbezeichnung und Codebezug bestätigen; danach ein normales Anzeigeverhalten protokollieren.
- **Grenze:** Keine Optimierung zur Umgehung von Server-Schutzmechanismen.

### NF-OBS-0002 – BaseFinder: berichtete Anzahl von 122 Basen

- **Modulregistrierung:** `BaseFinder`.
- **Nutzerbericht:** Bei einer RTP-bezogenen Nutzung seien 122 Basen angezeigt/gezählt worden, obwohl FakeBase ebenfalls vorhanden gewesen sei.
- **Status:** Nutzerbericht, ungeprüft.
- **Noch unklar:** Was genau als gezählte Basis gilt und welche Bedingungen/Version/Einstellungen galten.
- **Nächster Schritt:** Nur das berichtete Ergebnis und die Bedeutung der Zählung dokumentieren; keine Umgehung von Server-Schutz oder Optimierung zum Aufspüren geschützter Inhalte.

## Vorlage für neue Einträge

ID, Modul, Quelle (Nutzerbericht/Code-Fund/Test), Datum, Version/Commit, Beschreibung, Reproduzierbarkeit, Auswirkung, Priorität, Nachweis, nächste Aktion und Abschlussnachweis.
