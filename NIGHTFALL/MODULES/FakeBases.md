# FakeBases

- **Registrierter Modulname:** `FakeBases`
- **Quellpfad:** `src/main/java/dev/nightfall/modules/FakeBases.java`
- **Kategorie:** `WORLD`
- **Zweck:** Eigene, manuell gespeicherte TEST-/DECOY-Wegpunkte darstellen. Das Modul scannt keine Chunks und verändert keine Weltblöcke.

## Einstellungen

| Einstellung | Standard | Bedeutung |
|---|---:|---|
| Label | an | Zeigt den Namen beziehungsweise die Gruppengröße an |
| Tracer | aus | Zeichnet eine Linie von der Bildschirmmitte zum Marker |
| Max Distanz | 3.000 Blöcke | Maximale 3D-Anzeigedistanz |
| Nahe Marker gruppieren | an | Fasst nahe manuelle Wegpunkte zu einer Anzeigegruppe zusammen |
| Gruppierabstand | 32 Blöcke | Maximaler 3D-Abstand für benachbarte Punkte derselben Gruppe |
| Distanz anzeigen | an | Ergänzt die Entfernung am Label |

## Anzeige und Weltwechsel

- Es werden nur Wegpunkte der Kategorie `FakeBase` in der aktuellen Dimension angezeigt.
- Nahe Wegpunkte werden über zusammenhängende 3D-Nachbarschaften gruppiert und als gemeinsamer Marker mit Anzahl dargestellt.
- Ist die Gruppierung deaktiviert, wird jeder manuelle Wegpunkt einzeln angezeigt.
- Beim Welt-/Serverwechsel werden keine FakeBase-Wegpunkte gelöscht. Der `WaypointManager` lädt beim nächsten Zugriff die zum Server gehörende Datei.
- Waypoint-Dateien werden zunächst temporär geschrieben und danach atomar ersetzt, sofern das Dateisystem atomare Moves unterstützt.

**Wichtig:** FakeBases ist ausschließlich eine Anzeige für manuell gespeicherte Wegpunkte. Es ist kein automatischer BaseFinder und klassifiziert keine fremden Chunks.

**Teststatus:** Änderungen statisch geprüft; Build und In-Game-Test stehen noch aus.
