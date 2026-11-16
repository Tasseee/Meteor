# AutoFish

- **Status:** Code geprüft; Build-/In-Game-Test offen
- **Java-Klasse / Anzeigename:** `AutoFish`
- **Kategorie:** `MISC`
- **Aktueller Quellcode-SHA:** `8339706d7010b03c6dd950af9700365565c1072a`

## Zweck und Verhalten

Zieht die Angel bei erkanntem Biss ein. Das Modul wirft die Angel ausdrücklich nicht automatisch erneut aus. Erkannt wird ein Biss über den Zustand und die vertikale Geschwindigkeit des Fishing-Bobbers.

## Einstellungen

| Einstellung | Standard | Grenzen / Bedeutung |
|---|---:|---|
| Mindestabstand (Ticks) | 12 | 8–40 Ticks |
| Nur Haupthand | an | Wenn aus, kann eine Angel in der Nebenhand verwendet werden |

## Technische Prüfung

- **Nullfälle:** Spieler, Welt, Interaktionsmanager und aktiver Fishing-Bobber werden geprüft.
- **Cooldown:** wird beim Aktivieren, Deaktivieren und Weltwechsel zurückgesetzt.
- **Handauswahl:** bei aktivierter Option wird nur die Haupthand akzeptiert; ansonsten Haupthand bevorzugt, andernfalls Nebenhand.
- **Fehlerbehandlung:** keine lokale Fehlerbehandlung; zentraler Tick-Handler greift.
- **Threads/Ressourcen:** keine eigenen Listener, Ressourcen oder Threads.
- **In-Game/Build:** noch nicht ausgeführt.

## Befunde und Verbesserungen

### AF-01 – Nebenhand-Option verwendete zuvor trotzdem Haupthand

**Behoben im Code:** `33fd4e7175048b968c07ea8bd36c408af5b918ca`.

Wenn `Nur Haupthand` ausgeschaltet war, wurde eine Angel in der Nebenhand nicht zuverlässig unterstützt, da die Interaktion stets mit `MAIN_HAND` ausgeführt wurde. Die Hand wird jetzt vor der Interaktion anhand des tatsächlich gehaltenen Angel-Items gewählt.

**Verifikation:** Code gespeichert und erneut gelesen; Build- und In-Game-Test offen.

## Offene Tests

- [ ] Angel in Haupthand bei Option an/aus
- [ ] Angel nur in Nebenhand bei Option aus
- [ ] Keine Angel, kein aktiver Bobber
- [ ] Biss-Erkennung und Mindestabstand 8/40
- [ ] Weltwechsel während Cooldown

## Änderungsverlauf

| Datum | Änderung |
|---|---|
| 2026-11-16 | Nebenhand-Auswahl korrigiert; Build-/In-Game-Test offen. |
