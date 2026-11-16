# Testbereich

Jeder Test wird als eigener Markdown-Bericht gespeichert, zum Beispiel:

`NIGHTFALL/TESTING/RESULTS/2026-11-16-OreESP-HugoSMP-01.md`

Verwende die [Testbericht-Vorlage](TEST_REPORT_TEMPLATE.md). Ein Bericht pro Versuch oder klar abgegrenzter Testserie hält Bedingungen und Ergebnisse auseinander.

## Quellenkennzeichnung

- `Nutzerbericht`: vom Nutzer übermittelte Beobachtung, noch nicht reproduziert.
- `Manueller Test`: tatsächlich in der angegebenen Umgebung ausgeführt.
- `Codeanalyse`: aus dem benannten Commit und Quellcode abgeleitet.
- `CI`: automatisierter Build/Test mit Run-Link oder Statusnachweis.

## Grundsätze

- Version, Commit und Bedingungen immer notieren.
- Negative Ergebnisse ebenso speichern wie positive.
- Keine Screenshots oder Logs erfinden. Fehlt ein Nachweis, `nicht vorhanden` notieren.
- Servernamen und nötige Kontextangaben genügen; private Adressen, Kontodaten und Zugangsdaten weglassen.
- Tests dürfen keine Server-Schutzmechanismen umgehen und keine unfaire Automatisierung verbessern.
