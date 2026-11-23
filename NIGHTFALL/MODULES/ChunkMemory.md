# ChunkMemory

- **Status:** Code angelegt und in die Module integriert; Build-/In-Game-Test offen
- **Java-Klasse / Anzeigename:** `ChunkMemory`
- **Quellpfad:** `src/main/java/dev/nightfall/modules/ChunkMemory.java`
- **Kategorie:** `WORLD`
- **Sitzungsspeicher:** Nur im Arbeitsspeicher; keine Speicherung auf der Festplatte

## Verhalten

ChunkMemory durchlaeuft automatisch die Chunk-Koordinaten innerhalb seiner eingestellten Reichweite. Pro Client-Tick werden so viele Koordinaten geprueft, wie die Einstellung Scan-Geschwindigkeit vorgibt. Ein Chunk wird nur dann gespeichert, wenn er bereits im Client-Chunk-Manager vorhanden ist. Der Lookup fordert keine Chunks beim Server an.

Die Daten werden an ihren Weltkoordinaten gehalten. Die Implementierung merkt sich die clientseitigen Chunk-Objekte, statt jeden Block in ein eigenes dauerhaftes Format zu kopieren. Solange ein Chunk geladen ist, werden Aenderungen am Objekt weiter sichtbar; beim erneuten Laden wird die Referenz beim naechsten Durchlauf aktualisiert. Nach dem Verlassen der Welt oder einem Dimensions-/Serverwechsel wird der komplette Cache geleert.

## Einstellungen

- **Scan-Reichweite (Chunks):** Radius um den Spieler; wenn Freecam aktiv ist und die Option eingeschaltet ist, dient die Freecam-Position als Scan-Zentrum
- **Scan-Geschwindigkeit (Chunks/Tick):** Anzahl der Chunk-Koordinaten, die pro Client-Tick abgefragt werden
- **Max. gespeicherte Chunks:** Obergrenze fuer den LRU-Cache; aelteste Eintraege werden zuerst entfernt
- **Freecam als Scan-Zentrum:** Verwendet die aktuelle manuelle Freecam-Position statt der Spielerposition

## Verbraucher

- **BaseFinder:** Kann fuer den eigenen Scan auf gespeicherte Chunks zurueckgreifen. Die interne Fake-Pattern-Erkennung von BaseFinder bleibt getrennt von den manuellen FakeBases-Markierungen.
- **BlockESP und OreESP:** Koennen Blockzustaende aus gespeicherten Chunk-Sektionen lesen, wenn der Chunk nicht mehr aktiv geladen ist.
- **Block-Entity-ESP-Unterbau:** StorageESP und andere davon abgeleitete Module koennen gespeicherte Block-Entity-Informationen weiter darstellen.

## Grenzen / offene Punkte

Die aktuelle Version besitzt einen automatischen Hintergrund-Scan-Cursor, aber noch keine zweite, separat gerenderte Freecam-Entitaet, die autonom durch die Welt fliegt. Die normale Freecam kann als Scan-Zentrum verwendet werden. Der Cache wertet nur Daten aus, die der Client bereits erhalten hat; er erzeugt keine zusaetzlichen Serverdaten. Ein echter Build- und In-Game-Test mit Minecraft 1.21.11 / Fabric / Java 21 steht noch aus.
