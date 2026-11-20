# Ghostlag

- Eigenständiges Nightfall-Modul in der Kategorie **Combat**; das vorhandene FakeLag bleibt unverändert.
- Fängt eingehende Bewegungsupdates anderer Spieler ab und übernimmt pro Spieler das erste Update eines Snapshot-Intervalls.
- Weitere Bewegungsupdates desselben Spielers werden bis zum Ablauf des Intervalls clientseitig ausgelassen.
- Einstellungen:
  - **Snapshot-Intervall (ms):** 0–30.000 ms, Standard 10.000 ms
  - **Spieler-Radius:** 1–64 Blöcke, Standard 12
  - **HUD anzeigen:** Status und Zahl aktuell verzögerter Spieler
- Beim Deaktivieren und beim Weltwechsel werden alle Snapshot-Zustände zurückgesetzt.
- Die Funktion verändert keine Serverpositionen; sie beeinflusst nur, welche empfangenen Bewegungsupdates der lokale Client verarbeitet.

## Test
1. Modul über das Nightfall-Menü aktivieren.
2. Mit einem zweiten Spieler innerhalb des Radius testen.
3. Prüfen, dass dessen Bewegung clientseitig in Snapshot-Schritten aktualisiert wird.
4. Radius, Intervall, HUD und Deaktivieren/Weltwechsel prüfen.