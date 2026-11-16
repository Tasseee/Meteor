# CustomCrosshair

- **Status:** Code geprüft; In-Game-/Build-Test offen
- **Java-Klasse / Anzeigename:** `CustomCrosshair`
- **Kategorie:** `VISUALS`
- **Quellpfad:** `src/main/java/dev/nightfall/modules/CustomCrosshair.java`
- **Quellcode-SHA:** `4c75c07bec5e545ade6923918347e63f9e0a758d`
- **Registrierung:** `ModuleManager.init()`

## Zweck und Verhalten

Zeichnet ein benutzerdefiniertes Fadenkreuz im HUD, wenn Spieler und Welt vorhanden sind, kein Bildschirm geöffnet ist und die Minecraft-HUD-Anzeige nicht verborgen ist.

## Einstellungen

| Einstellung | Standard | Grenzen / Optionen |
|---|---|---|
| Stil | Kreuz | Kreuz, Punkt, X |
| Größe | 5 | 2–12; Schritt 1 |
| Abstand | 3 | 0–8; Schritt 1 |
| Farbe | RGB 5636010 | RGB-Wert; Alpha wird beim Zeichnen auf 255 gesetzt |

## Technische Prüfung

- **HUD-Bedingungen:** prüft HUD-Sichtbarkeit, Spieler, Welt und offenen Screen.
- **Rendering:** zeichnet nur einfache gefüllte Rechtecke; X-Stil zeichnet zwei diagonale Pixelspuren.
- **Einstellungstypen:** ModeSetting begrenzt auf bekannte Stile; NumberSetting begrenzt Größe/Abstand; ColorSetting maskiert auf RGB.
- **Nullfälle:** Spieler und Welt werden geprüft; Minecraft-Optionsobjekt wird ohne Nullcheck gelesen und wird als regulär initialisierter Client vorausgesetzt.
- **Lebenszyklus:** keine Listener, Threads oder eigenen Ressourcen.
- **Fehlerbehandlung:** keine lokale Fehlerbehandlung; zentraler Render-Handler greift.
- **Laufzeit/Version:** nicht im Spiel getestet; Build nicht ausgeführt.

## Befunde und Verbesserungen

### Verbesserungskandidat CC-01 – Abstand 0 im Kreuz-Stil

Bei Stil `Kreuz` wird selbst mit Abstand 0 das zentrale Pixel ausgespart: horizontale und vertikale Teilstücke beginnen bzw. enden neben dem Mittelpunkt. Das kann als beabsichtigte Mittelöffnung gelten, entspricht aber möglicherweise nicht der naheliegenden Erwartung an einen Abstand von 0.

### Einschränkung CC-02 – Abstand in anderen Stilen

Die Einstellung `Abstand` wird nur für den Stil `Kreuz` verwendet. Im Stil `Punkt` und `X` hat sie keine Wirkung. Die GUI sollte dies ggf. kontextabhängig kenntlich machen.

## Nutzer- und In-Game-Tests

Offen: alle drei Stile, minimale/maximale Größe, Abstand 0 und 8, Farbwechsel, HUD verborgen, Menü geöffnet und verschiedene GUI-Skalierungen.

## Änderungsverlauf

| Datum | Änderung |
|---|---|
| 2026-11-16 | Erste Codeprüfung; keine Java-Änderung in diesem Audit-Eintrag. |
