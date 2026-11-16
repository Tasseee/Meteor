# FakeStats

- **Status:** Code geprüft; HUD-Test offen
- **Java-Klasse / Anzeigename:** `FakeStats`
- **Kategorie:** `HUD`
- **Quellcode-SHA:** `67294f2ce28cf7c2241d37e809af4b61a1434042`

## Zweck und Verhalten

Zeigt eine rein lokale Statistik-Karte mit frei eingestelltem Anzeigenamen, Kills, Toden und Siegen. Der HUD-Titel sagt ausdrücklich `FAKE STATS · LOKAL`; die Beschreibung stellt klar, dass keine Serverdaten verändert werden.

## Einstellungen

| Einstellung | Standard | Grenzen |
|---|---:|---|
| Anzeigename | Nightfall | maximal 32 Zeichen |
| Fake Kills | 42 | 0–999.999 |
| Fake Tode | 3 | 0–999.999 |
| Fake Siege | 17 | 0–999.999 |

## Technische Prüfung

- **Datenquelle:** ausschließlich lokale Settings.
- **HUD:** Breite passt sich der längsten Zeile an; fünf Zeilen mit festem Abstand.
- **Position:** Standard rechts mittig.
- **Nullfälle:** verwendet den gemeinsamen HudModule-Renderpfad.
- **Persistenz:** Settings werden über das Config-System gespeichert.
- **Lifecycle/Threads:** keine eigenen Listener, Ressourcen oder Threads.
- **In-Game/Build:** nicht ausgeführt.

## Befunde und Verbesserungen

- Kein eindeutiger Fehler in der statischen Prüfung bestätigt.
- Die Kennzeichnung `FAKE STATS · LOKAL` ist gut sichtbar und sollte bei jeder optischen Überarbeitung erhalten bleiben.
- Bei sehr langem Anzeigenamen wächst die HUD-Breite; ein Test bei kleiner Auflösung ist sinnvoll.

## Offene Tests

- [ ] Werte 0 und 999.999
- [ ] Anzeigename am 32-Zeichen-Limit
- [ ] kleine Auflösung und GUI-Skalierung
- [ ] Config speichern/laden
- [ ] Modul aktivieren/deaktivieren

## Änderungsverlauf

| Datum | Änderung |
|---|---|
| 2026-11-16 | FakeStats codebasiert geprüft; keine Java-Änderung. |
