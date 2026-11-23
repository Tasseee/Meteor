# BackgroundFreecam

- **Status:** Code angelegt und registriert; Build-/In-Game-Test offen
- **Java-Klasse / Anzeigename:** `BackgroundFreecam`
- **Quellpfad:** `src/main/java/dev/nightfall/modules/BackgroundFreecam.java`
- **Kategorie:** `WORLD`
- **Lebenszyklus:** Wird beim Aktivieren von `ChunkMemory` automatisch gestartet und beim Ausschalten wieder gestoppt

## Verhalten

BackgroundFreecam besitzt eine eigene clientseitige Scan-Position und eine Positions-/Flugsteuerung. Sie erstellt dafuer keine Minecraft-Entity mehr; die unveraenderliche Positionsdatenstruktur wird pro Tick einmal aktualisiert und von Scan-Modulen gemeinsam genutzt. Sie fliegt automatisch in einer dreidimensionalen Orbit-/Sweep-Bahn um die aktuelle Spielerposition. Dabei wird weder die Renderkamera ersetzt noch die echte Spielerposition veraendert. Es wird keine Entity in die Welt eingefuegt und nichts fuer andere Spieler sichtbar gemacht.

ChunkMemory verwendet diese Hintergrund-Scan-Position als Scan-Zentrum. BaseFinder verwendet sie ebenfalls als Zentrum seiner Chunk-Auswertung. Die manuelle Freecam kann parallel weiter fuer die normale Ansicht verwendet werden.

## Einstellungen

- **Flugradius:** Abstand der Kreisbahn zum Spieler
- **Flugtempo:** Bewegung entlang der Bahn in Bloecken pro Tick
- **Flughoehe:** Mittlere Hoehe ueber der Augenposition des Spielers
- **Vertikaler Scanbereich:** Hoehenbewegung der Kamera entlang der Bahn

## Grenzen

Die Hintergrundkamera erstellt keine Entity, erzeugt keine Serveranfragen und laedt keine Chunks selbst. Sie bewegt nur ihren eigenen clientseitigen Scan-Kamerapunkt. ChunkMemory kann weiterhin nur Chunk-Daten speichern, die der Client bereits erhalten hat. Build- und In-Game-Test mit Minecraft 1.21.11, Fabric und Java 21 sind noch offen.
