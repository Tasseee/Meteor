# WeaponAnimations

- **Status:** Code geprüft; Render-/In-Game-Test offen
- **Java-Klasse / Anzeigename:** `WeaponAnimations`
- **Kategorie:** `VISUALS`
- **Quellcode-SHA:** `1168925c6a7c2228e00d552ae5434a2cbc9e1720`

## Zweck und Verhalten

Verändert ausschließlich lokale First-Person-Matrizen für Swing-Animationen und eine optionale Waffeninspektion. Die Modulklasse enthält keine Zielauswahl, Angriffsentscheidung oder Interaktions-/Klickauslösung.

## Einstellungen

| Einstellung | Standard | Grenzen / Optionen |
|---|---:|---|
| Swing-Stil | Vanilla+ | Vanilla+, Punch, Sweep, Spin, Snap |
| Inspektion aktiv | aus | Boolesch |
| Inspektionsgeschwindigkeit | 1,0 | 0,25–2,5; Schritt 0,05 |
| Swing-Stärke | 1,0 | 0,25–2,0; Schritt 0,05 |

## Technische Prüfung

- **Eingabe:** Swing-Fortschritt wird auf 0–1 begrenzt; bei <=0 keine Swing-Transformation.
- **Inspektion:** nutzt Zeitbasis `System.currentTimeMillis()` und Inspektionsgeschwindigkeit.
- **Mathematik:** Quaternionen werden lokal pro Transform-Aufruf erstellt.
- **Threading/Ressourcen:** keine eigenen Threads, Listener oder gehaltenen Ressourcen.
- **Performance:** Quaternion-Allokationen pro Animationsaufruf; Messung steht aus.
- **In-Game/Build:** nicht ausgeführt.

## Befunde und Verbesserungen

- Kein klarer Funktionsfehler aus der Modulklasse ableitbar.
- Bei sehr häufigen Swing-Renderaufrufen könnten die Quaternion-Allokationen profiliert werden; erst bei messbarem Aufwand optimieren.
- Animationen dürfen nur die visuelle Darstellung betreffen. Angriffstiming, Treffer oder Combat-Automatisierung werden nicht verändert.

## Offene Tests

- [ ] Alle fünf Swing-Stile
- [ ] Stärke 0,25 / 2,0
- [ ] Inspektionsmodus an/aus
- [ ] Linke und rechte Hand
- [ ] Kombination mit FirstPersonStyle

## Änderungsverlauf

| Datum | Änderung |
|---|---|
| 2026-11-16 | WeaponAnimations codebasiert geprüft; keine Java-Änderung. |
