# ChunkMemory

- **Status:** Code angelegt und integriert; automatischer Kamera-Build-/In-Game-Test offen
- **Java-Klasse / Anzeigename:** `ChunkMemory`
- **Quellpfad:** `src/main/java/dev/nightfall/modules/ChunkMemory.java`
- **Kategorie:** `WORLD`
- **Sitzungsspeicher:** Nur im Arbeitsspeicher; keine Speicherung auf der Festplatte

## Verhalten

ChunkMemory durchlaeuft automatisch die Chunk-Koordinaten innerhalb eines einstellbaren horizontalen Blockradius (Standard 50). Pro Client-Tick werden so viele Koordinaten geprueft, wie die Einstellung Scan-Geschwindigkeit vorgibt. Sobald das Ende der Liste erreicht ist, beginnt der Durchlauf wieder am Anfang. Der Bereich wird damit fortlaufend erneut abgefragt; wenn sich das Zentrum in einen anderen Chunk bewegt, wird derselbe relative Scanpfad automatisch um das neue Zentrum verschoben. Ein Chunk wird nur dann gespeichert, wenn er bereits im Client-Chunk-Manager vorhanden ist. Der Lookup fordert keine Chunks beim Server an.

Der 50-Block-Radius wird vor jedem Chunk-Lookup gegen die aktuelle Hintergrundkamera-Position geprueft. Er umfasst die horizontalen Richtungen rund um die Kamera; jeder aufgenommene Chunk liefert alle seine vertikalen Sektionen. Die Daten werden an ihren Weltkoordinaten gehalten. Die Implementierung merkt sich die clientseitigen Chunk-Objekte, statt jeden Block in ein eigenes dauerhaftes Format zu kopieren. Solange ein Chunk geladen ist, werden Aenderungen am Objekt weiter sichtbar; bei jedem neuen Rundlauf wird die Referenz fuer weiterhin geladene Chunks aktualisiert. Nach dem Verlassen der Welt oder einem Dimensions-/Serverwechsel wird der komplette Cache geleert.

## Einstellungen

- **Scan-Radius (Bloecke):** Standard 50 Bloecke horizontal um das aktive Scan-Zentrum. Jeder Chunk wird aufgenommen, wenn seine 16x16-Grundflaeche den Radius schneidet; die gespeicherte Chunk-Datenstruktur enthaelt alle vertikalen Sektionen.
- **Scan-Geschwindigkeit (Chunks/Tick):** Anzahl der Chunk-Koordinaten, die pro Client-Tick abgefragt werden. Der Cursor laeuft zyklisch durch den Bereich und scannt ihn wiederholt
- **Max. gespeicherte Chunks:** Obergrenze fuer den LRU-Cache; aelteste Eintraege werden zuerst entfernt
- **Hintergrund-Freecam als Scan-Zentrum:** Nutzt die unabhaengige automatische Kamera (standardmaessig an)\n- **Manuelle Freecam als Ersatz-Zentrum:** Wird nur verwendet, wenn die Hintergrundkamera nicht als Scan-Zentrum genutzt wird\n\n## BackgroundFreecam-Einstellungen\n\n- **Flugradius:** Horizontaler Orbit-Abstand zum Spieler\n- **Flugtempo:** Geschwindigkeit entlang der Flugbahn\n- **Flughoehe:** Mittlere Hoehe oberhalb der Spieler-Augenposition\n- **Vertikaler Scanbereich:** Staerke der Hoehenbewegung waehrend des Orbits

## Verbraucher

- **BaseFinder:** Kann fuer den eigenen Scan auf gespeicherte Chunks zurueckgreifen. Die interne Fake-Pattern-Erkennung von BaseFinder bleibt getrennt von den manuellen FakeBases-Markierungen.
- **BlockESP und OreESP:** Koennen Blockzustaende aus gespeicherten Chunk-Sektionen lesen, wenn der Chunk nicht mehr aktiv geladen ist.
- **Block-Entity-ESP-Unterbau:** StorageESP und andere davon abgeleitete Module koennen gespeicherte Block-Entity-Informationen weiter darstellen.

## Grenzen / offene Punkte

Die Hintergrundkamera ist ein separater, nicht gerenderter Client-Kamera-Proxy. Sie bewegt sich autonom auf einer Orbit-/Sweep-Bahn, waehrend die normale Sicht unveraendert bleibt. Sie loest selbst keine Chunk-Anfragen aus. Gespeichert und ausgewertet werden ausschliesslich Daten von Chunks, die dem Client bereits vorliegen. Ein echter Build- und In-Game-Test mit Minecraft 1.21.11 / Fabric / Java 21 steht noch aus.
