# ItemGlow

- **Registrierter Modulname:** `ItemGlow`
- **Anzeigename:** `Item Glow`
- **Auditstatus:** Vollständiger statischer Code-Audit; In-Game-Test offen
- **Modul-Blob-SHA:** `b7c2a6753b33d4e55f3f45bedc98ddcc87766a45`
- **Renderer:** `src/main/java/dev/nightfall/render/ItemGlowRenderer.java`
- **Renderer-Blob-SHA:** `23604fb603d5d729e87786585dfb2698264155dc`
- **Mixin-Accessor:** `src/main/java/dev/nightfall/mixin/HandledScreenAccessor.java`
- **Accessor-Blob-SHA:** `67d0dae859cf6ff537ddf2f195f8181de52151dd`

## Einstellungen

| Einstellung | Standard | Bereich |
|---|---:|---:|
| Farbe | `0x39FF14` | RGB |
| Glow-Radius | 6 | 1–12 |
| Intensität | 0,85 | 0,10–1,00 |
| Pulsieren | AN | Boolean |
| Hotbar | AN | Boolean |
| Inventar | AN | Boolean |

Die `Hotbar`-Option steuert im aktuellen Renderer sowohl die neun Hotbar-Slots als auch den Offhand-Slot. Es gibt keine separate Offhand-Option. Das Modul-Description nennt Hotbar, Offhand und Inventar bereits gemeinsam.

## Codebefunde

- `ModuleManager.init()` registriert `ItemGlow` genau einmal in der Kategorie `VISUALS`.
- `Nightfall.onInitializeClient()` ruft `ItemGlowRenderer.init()` einmal auf.
- Der Renderer hängt einen HUD-Callback nach der Hotbar ein und einen `ScreenEvents.AFTER_INIT`-Callback für `HandledScreen`-Instanzen.
- Der Hotbar-Pfad prüft Modulaktivierung, Option, Spieler und HUD-Sichtbarkeit; leere Itemstacks werden übersprungen.
- Der Inventarpfad prüft Modulaktivierung und Option, liest Position/Handler über `HandledScreenAccessor` und überspringt leere Slots.
- `HandledScreenAccessor` ist in `nightfall.mixins.json` als erforderlicher Client-Mixin eingetragen.
- Radius und Alpha werden im Renderer begrenzt; der Inventarpfad begrenzt den Radius zusätzlich auf 4. Die Schleifen sind durch Slotanzahl und Radius-Einstellung beschränkt.
- Pulsieren wird renderseitig über `System.nanoTime()` berechnet. Das Modul besitzt keine eigenen Threads, Timer oder Ressourcen.
- Der Module-Lookup fällt bei fehlender Registrierung defensiv auf `null` zurück.
- Es gibt keine modul-eigenen Enable-/Disable- oder World-Leave-Hooks; die global registrierten Rendercallbacks prüfen den Aktivierungsstatus.
- Konfigurationswerte werden anhand des Einstellungsnamens gespeichert. `Hotbar` nicht ohne Migration umbenennen, da bestehende Konfigurationen sonst den alten Wert nicht mehr zuordnen könnten.

## Befunde und offene Prüfung

- Kein eindeutiger nicht-angriffsbezogener Codefehler wurde bestätigt; daher keine Java-Änderung.
- Das tatsächliche Aussehen und die korrekte Position des Offhand-Glows müssen im Spiel geprüft werden.
- [ ] Hotbar und Offhand mit leerem/belegtem Slot prüfen.
- [ ] Inventar in mehreren `HandledScreen`-Typen prüfen.
- [ ] Radius-Minimum/-Maximum, Intensität und Pulsieren prüfen.
- [ ] Wechsel zwischen Inventarbildschirmen und Schließen des Screens auf doppelte Callbacks prüfen.
- [ ] In-Game-Test bleibt offen.

## Build und Tests

Der GitHub-Actions-Build `44039685234` auf Commit `5f7e942922c40f80c9534c93e151f3f16d226d98` hat `gradle build --no-daemon` erfolgreich ausgeführt. Dieser Commit enthält den hier geprüften Java-Stand; spätere Commits `e93fe8b` und `997db22` betreffen nur Dokumentation. Eine eigene `src/test`-Testsuite ist nicht vorhanden. Kein In-Game-Test wurde ausgeführt.

## Inventurhinweis

Die frühere Auditliste führte `ItemGlint`, aber `ModuleManager.init()` registriert aktuell `ItemGlow`. Ein aktuelles `ItemGlint`-Modul ist in der Java-Inventur nicht vorhanden. Die alte `ItemGlint.md`-Akte bleibt ein historischer Eintrag.
