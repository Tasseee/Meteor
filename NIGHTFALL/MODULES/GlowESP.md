# GlowESP

- **Status:** Code geprüft; Render-/In-Game-Test offen
- **Java-Klasse / Anzeigename:** `GlowESP`
- **Kategorie:** `VISUALS`
- **Quellcode-SHA:** `312ed85bbc55c6f70fab075ea7d8bef21b96db73`

## Zweck und Verhalten

Lässt ausgewählte lebende Entities über den Minecraft-Outline-Shader lokal hervorheben. Die Entscheidung erfolgt über eine statische Abfrage, die der Render-Mixin nutzen kann.

## Einstellungen

| Einstellung | Standard | Grenzen / Bedeutung |
|---|---:|---|
| Spieler | an | Spieler hervorheben |
| Feindliche Mobs | an | hostile Entity-Gruppe |
| Passive Mobs | aus | übrige Nicht-Spieler-Gruppen |
| Durch Wände | an | Sichtlinienprüfung überspringen |
| Reichweite | 96 | 8–256; Schritt 8 |
| Glow-Farbe | RGB 5631709 | Outline-Farbe |

## Technische Prüfung

- **Nullfälle:** Modul prüft Aktivierung, Spieler, Welt, Entity und Entity-Typ; Kamera-Fallback nutzt Spielerposition.
- **Reichweite:** quadrierter Distanzvergleich zur Kamera-/Spielerposition.
- **Fake-Chunk-Abhängigkeit:** Entities in von BaseFinder als Fake eingestuften Chunks werden ausgeschlossen.
- **Sichtlinie:** wenn `Durch Wände` aus ist, wird `Projection.canSeeFromCamera` verwendet.
- **Fehlerbehandlung:** statische Wrapper fangen `Throwable` und geben false/Fallback-Farbe zurück; Fehler werden nicht protokolliert.
- **Threads/Ressourcen:** keine eigenen Threads oder Listener in der Modulklasse.
- **In-Game/Build:** nicht ausgeführt.

## Befunde und Verbesserungen

- Kein eindeutiger Fehler im Modulcode bestätigt.
- Die Catch-Blöcke der statischen Wrapper verschlucken Render-/Integrationsfehler still. Ein gedrosselter Debug-Logeintrag könnte Diagnose erleichtern.
- Die Gruppe „passive“ umfasst im aktuellen Code jeden Nicht-Spieler, der nicht zur hostile-Gruppe gehört. Ob neutrale Entities damit bewusst eingeschlossen sind, sollte getestet werden.
- Keine Änderung an Entity-Erkennung, Reichweite oder Durch-Wände-Verhalten vorgenommen.

## Offene Tests

- [ ] Spieler/hostile/passive getrennt
- [ ] Durch Wände an/aus
- [ ] Reichweite 8/256
- [ ] Fake-Chunk-Filter
- [ ] Shader- und Mixin-Fehlerpfad

## Änderungsverlauf

| Datum | Änderung |
|---|---|
| 2026-11-16 | GlowESP codebasiert geprüft; keine Java-Änderung. |
