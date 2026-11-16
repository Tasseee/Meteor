# FirstPersonStyle

- **Status:** Code geprüft; Render-/In-Game-Test offen
- **Java-Klasse:** `FirstPersonStyle`
- **Anzeigename im Client:** `ViewModel`
- **Kategorie:** `VISUALS`
- **Modul-Quellcode-SHA:** `de40fcf58e96be09ec6b4f114519a1c3003acde8`
- **Mixin:** `src/main/java/dev/nightfall/mixin/HeldItemRendererMixin.java`
- **Mixin-SHA:** `0623b8f8e9a00dfcf4537bd3eb8aa4a4e2872d66`

## Zweck und Verhalten

Steuert die Darstellung der gehaltenen Items und Arme in der Ich-Perspektive. Der Mixin tauscht bei Bedarf die gerenderte Hand, kann leere Hände ausblenden und wendet Transformationen an. Das Modul wird in `ModuleManager.init()` standardmäßig aktiviert.

## Einstellungen

| Einstellung | Standard | Grenzen / Optionen |
|---|---|---|
| Items linke/rechte Hand tauschen | aus | Boolesch |
| Leere Hand ausblenden | aus | Boolesch |
| Handhaltung | Anubis | Vanilla, Katana, Heavy, Floating, Compact, Anubis |
| X-Position | 0,00 | −0,8 bis 0,8; Schritt 0,01 |
| Y-Position | −0,08 | −0,8 bis 0,8; Schritt 0,01 |
| Z-Position | −0,12 | −0,8 bis 0,8; Schritt 0,01 |
| X-Rotation | 0° | −45° bis 45° |
| Y-Rotation | 0° | −45° bis 45° |
| Z-Rotation | 0° | −90° bis 90° |
| 3D-Skalierung | 1,00 | 0,65 bis 1,35 |

## Technische Prüfung

- **Nullfälle:** Handtausch gibt den Original-Stack zurück, wenn Spieler oder Stack fehlen.
- **Rendering:** Matrix-Push bei aktivem ViewModel oder WeaponAnimations; Pop erfolgt in einem RETURN-Mixin.
- **Fehlerbehandlung:** Mixin-Zugriffe und Transformationen fangen `Throwable` teilweise still ab, ohne Logeintrag.
- **Ressourcen/Threads:** keine eigenen Listener oder Hintergrundthreads; der Mixin ist am Renderpfad beteiligt.
- **Kompatibilität:** direkt an den Minecraft-Methodenaufruf des gehaltenen Item-Renderers gekoppelt; Versionsänderungen müssen gesondert geprüft werden.
- **In-Game/Build:** noch nicht ausgeführt.

## Befunde und Verbesserungen

### FPS-01 – stille Fehler im Render-Mixin

Mehrere `catch (Throwable)`-Blöcke verwerfen Fehler kommentarlos. Das schützt teilweise vor einem zusätzlichen Crash, erschwert aber die Fehlersuche. Verbesserung: kontrolliertes Logging mit Drosselung, damit Renderfehler nicht jede Frame-Ausgabe überfluten.

### FPS-02 – Matrix-Bereinigung bei Render-Ausnahme

Die Matrix wird am RETURN-Punkt zurückgesetzt. Falls der ursprüngliche Render-Aufruf vorher mit einer Exception abbricht, ist nicht belegt, dass der RETURN-Injector noch ausgeführt wird. Das ist ein zu testender Robustheitspunkt, kein bestätigter Matrix-Leak.

## Offene Tests

- [ ] Alle sechs Handhaltungen
- [ ] Beide Hände und Handtausch
- [ ] Leere Hand ein-/ausblenden
- [ ] Grenzwerte für Position, Rotation und Skalierung
- [ ] Item-Renderfehler und Matrixzustand danach
- [ ] Zusammenspiel mit WeaponAnimations

## Änderungsverlauf

| Datum | Änderung |
|---|---|
| 2026-11-16 | Modul und zugehörigen HeldItemRenderer-Mixin codebasiert geprüft. |
