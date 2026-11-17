# FakeLag

- **Registrierter Modulname:** `FakeLag`
- **Auditstatus:** Teilweise geprüft – ausschließlich Ghost-Renderer/Anzeige
- **Modul-Quellpfad:** `src/main/java/dev/nightfall/modules/FakeLag.java`
- **Renderer:** `src/main/java/dev/nightfall/render/FakeLagGhostRenderer.java`
- **Renderer-Blob-SHA:** `cfe9be494599c1e20c007df05a360d440bb37029`

## Ghost-Skin: Codebefund

Der separate Renderer wird über `WorldRenderEvents.AFTER_ENTITIES` registriert. Vor dem Rendern prüft er, ob lokaler Spieler und Welt vorhanden sind, eine ausstehende Serverposition existiert und Matrix, Command-Queue sowie World-State verfügbar sind.

Danach wird der normale Player-Renderer des lokalen Spielers verwendet. Der Renderzustand wird auf die zuletzt bekannte Serverposition und Blickrichtung gesetzt; zusätzliche Namensplaketten werden entfernt. Die Matrix wird mit Push/Pop umschlossen und im `finally`-Block wiederhergestellt.

Renderfehler werden im Hilfsrenderer abgefangen, damit der normale World-Renderpfad nicht unterbrochen wird. Der Catch ist still; ein Diagnoseeintrag ist hier nicht vorhanden. Das ist ein Wartbarkeitshinweis, kein bestätigter Renderfehler.

## Prüfgrenze

Diese Prüfung betrifft nur den visuellen Ghost-Renderer. Die Netzwerk-/Paketlogik von FakeLag wurde in diesem Lauf weder funktional auditiert noch geändert. Es wurden keine Maßnahmen zur Umgehung von Server-Schutz, Anti-Cheat oder Kicks vorgenommen.

## Offene Verifikation

<badge color="warning">In-Game-Test offen</badge>

- [ ] Ghost-Kopie erscheint an der erwarteten Position im Third-Person-/World-Render.
- [ ] Skin-Textur und Modellteile stimmen im laufenden Spiel.
- [ ] Bei fehlender Welt/Spielerreferenz bleibt der normale Renderpfad unbeeinträchtigt.
- [ ] Kein doppeltes Namensschild.

Es wurde kein In-Game-Test ausgeführt. Die Ghost-Skin-Darstellung ist daher ausdrücklich nicht als bestätigt markiert.
