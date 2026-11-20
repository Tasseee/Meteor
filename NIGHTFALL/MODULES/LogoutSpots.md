# LogoutSpots

- **Status:** Implementiert; Build- und In-Game-Test offen
- **Java-Klasse:** `LogoutSpots`
- **Anzeigename:** `LogoutSpots`
- **Kategorie:** `PLAYER`
- **Quellpfad:** `src/main/java/dev/nightfall/modules/LogoutSpots.java`
- **Registrierung:** `ModuleManager.init()`

## Zweck und Verhalten

Speichert die zuletzt clientseitig beobachtete Position von Spielern und setzt einen ESP-Marker, wenn ihre UUID aus der aktuellen Spielerliste des verbundenen Servers verschwindet. Der Marker enthält den Spielernamen, die Distanz und optional die Koordinaten.

Das Modul muss aktiviert sein, während der Spieler noch beobachtet wird. Es kann keine Position rekonstruieren, die der Client zuvor nie gesehen hat. Über **Max. Positionsalter** werden alte Positions-Snapshots verworfen. Ein Wechsel zwischen Backend-/Unterservern kann wie ein Logout aussehen, wenn der Server die UUID kurzzeitig aus der Spielerliste entfernt.

## Einstellungen

| Einstellung | Standard | Bereich |
|---|---:|---:|
| Max. Distanz | 3000 | 64–10000 |
| Max. Positionsalter (s) | 120 | 5–600 |
| Marker-Dauer (Min.) | 60 | 1–1440 |
| Max. Marker | 48 | 1–128 |
| Koordinaten | an | Boolesch |
| Tracer | an | Boolesch |
| Farbe | Lila | Farbwahl |

## Technische Prüfung

- **Positionsquelle:** lokal geladene Spieler-Entities.
- **Logout-Erkennung:** Vergleich der UUIDs in `ClientPlayNetworkHandler.getPlayerUuids()` zwischen Client-Ticks.
- **Keine Chunk-Abfrage:** Das Modul lädt keine Chunks und versucht nicht, unbekannte Spielerpositionen zu erraten.
- **Lifecycle:** Snapshots und Marker werden bei Deaktivierung und Weltwechsel zurückgesetzt.
- **Build/In-Game:** noch nicht ausgeführt.

## Offene Tests

- [ ] Spieler-Logout nach zuvor beobachteter Position
- [ ] Spieler außerhalb der Renderreichweite mit frischem Snapshot
- [ ] Snapshot älter als eingestelltes Maximum wird verworfen
- [ ] Marker läuft nach der eingestellten Zeit ab
- [ ] Backend-/Unterserverwechsel erzeugt keinen irreführenden Marker oder wird als Einschränkung bestätigt
- [ ] Weltwechsel und Deaktivierung leeren Daten

## Änderungsverlauf

| Datum | Änderung |
|---|---|
| 2026-11-20 | Erstimplementierung und Registrierung als Player-Modul. |
