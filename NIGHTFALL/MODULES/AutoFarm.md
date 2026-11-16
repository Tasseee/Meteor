# AutoFarm

- **Status:** Code geprüft; Build-/In-Game-Test offen
- **Java-Klasse / Anzeigename:** `AutoFarm`
- **Kategorie:** `WORLD`
- **Aktueller Quellcode-SHA:** `b47ee4cb0e4c7c79626388a3fa106f11f1bd1663`

## Zweck und Verhalten

Erntet ausschließlich die reife Pflanze unter dem Fadenkreuz. Es sucht keine Felder selbstständig ab und bewegt den Spieler nicht. Unterstützte Pflanzen sind Weizen, Karotten, Kartoffeln und Rote Bete. Optional wird nach der Ernte dieselbe Pflanze nachgesetzt, sofern passendes Saatgut in der Hotbar liegt und der Block darunter Ackerboden ist.

## Einstellungen

| Einstellung | Standard | Bedeutung |
|---|---:|---|
| Automatisch nachpflanzen | an | Saatgut nach der Ernte einsetzen |

## Technische Prüfung

- **Voraussetzungen:** Spieler, Welt und Interaktionsmanager müssen verfügbar sein.
- **Ziel:** aktueller Crosshair-Block muss eine reife unterstützte Pflanze sein.
- **Abbruch:** Ernteversuch wird nach 80 Break-Ticks beendet, falls der Block nicht verschwindet.
- **Lifecycle:** Pending-Block, Saatgut und Cooldown werden bei Aktivieren, Deaktivieren und Weltwechsel geleert.
- **Hotbar:** Saatgut-Slot wird vor dem Platzieren ausgewählt und danach zurückgesetzt.
- **Fehlerbehandlung:** zentrale Tick-Fehlerbehandlung; lokal kein catch.
- **In-Game/Build:** nicht ausgeführt.

## Befunde und Verbesserungen

### AFM-01 – Hotbar-Slot bei Interaktionsfehler wiederherstellen

**Behoben:** `e0e7f5ad5ea091d9ef995893c0c3edf3fdae3c1b`.

Die Rücksetzung des vorher ausgewählten Hotbar-Slots lag zuvor nach dem Interaktionsaufruf. Bei einer Exception hätte die Rücksetzung übersprungen werden können. Sie liegt jetzt in einem `finally`-Block.

**Verifikation:** Quellcode auf GitHub gespeichert und erneut gelesen; Build- und In-Game-Test offen.

- Es wird kein automatisches Ziel gesucht und keine Kampfaktion ausgelöst.
- Ohne Saatgut in den ersten neun Slots erfolgt kein Nachpflanzen; derzeit wird dazu keine separate Warnung angezeigt.

## Offene Tests

- [ ] jede der vier Pflanzen
- [ ] Replant an/aus
- [ ] Saatgut in verschiedenen Hotbar-Slots
- [ ] kein Saatgut / falscher Boden
- [ ] manuelles Zerstören während Pending
- [ ] Weltwechsel und deaktivieren während Break-Phase

## Änderungsverlauf

| Datum | Änderung |
|---|---|
| 2026-11-16 | Code geprüft; Hotbar-Wiederherstellung in finally abgesichert. |
