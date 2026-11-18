# BoatFly

- **Status:** Ersetzt; statische Codeprüfung durchgeführt, In-Game-Test offen
- **Kategorie:** `MOVEMENT`
- **Quellpfad:** `src/main/java/dev/nightfall/modules/BoatFly.java`

## Neues Verhalten (2026-11-18)

Das vorhandene `BoatFly` wurde ersetzt. Es wurde kein zweites Boot-Modul angelegt und die vorhandene Registrierung in `ModuleManager` bleibt unverändert.

- Horizontale Bewegung orientiert sich an der Blickrichtung des Spielers.
- Vorwärts, Rückwärts und Seitwärts werden kombiniert und diagonal normalisiert.
- Hoch und Runter verwenden die Sprung- und Ducken-Taste.
- `Hover` hält die vertikale Geschwindigkeit ohne Hoch-/Runter-Eingabe auf null. Ist Hover ausgeschaltet, bleibt die bisherige vertikale Geschwindigkeit erhalten.
- `Noclip` schaltet die Entity-Kollision des Boots optional aus.
- `Blickrichtung sperren` übernimmt die Yaw-Richtung des Spielers.
- Die Bewegungswerte werden als Blöcke pro Sekunde angegeben und für den Tick aufgeteilt.

## Einstellungen

| Einstellung | Standard | Bereich |
|---|---:|---:|
| Geschwindigkeit | 20 Blöcke/s | 1–200 |
| Vertikalgeschwindigkeit | 10 Blöcke/s | 0–100 |
| Noclip | AN | AN/AUS |
| Hover | AN | AN/AUS |
| Blickrichtung sperren | AN | AN/AUS |

## Lifecycle / Wiederherstellung

- Beim Wechsel auf ein anderes Boot wird das vorherige Boot zuerst wiederhergestellt.
- Beim Aussteigen, Deaktivieren und Weltwechsel werden die ursprünglichen `noClip`- und `noGravity`-Werte des kontrollierten Boots zurückgesetzt.
- Es wird kein eigener Event- oder Mixin-Hook benötigt; das Modul nutzt Nightfalls vorhandenes `onTick()`-System.

## Tests offen

- [ ] Vorwärts/Rückwärts/Seitwärts und diagonale Bewegung
- [ ] Vertikal hoch/runter und Hover
- [ ] Noclip AN/AUS
- [ ] Yaw-Lock AN/AUS
- [ ] Aussteigen und Bootwechsel
- [ ] Weltwechsel und Deaktivieren
- [ ] Ursprüngliche NoClip-/NoGravity-Zustände bleiben erhalten
- [ ] Build mit Java 21
