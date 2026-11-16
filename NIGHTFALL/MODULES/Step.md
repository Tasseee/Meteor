# Step

- **Status:** Code geprüft; Movement-/In-Game-Test offen
- **Java-Klasse / Anzeigename:** `Step`
- **Kategorie:** `MOVEMENT`
- **Quellcode-SHA:** `0b0f49b664cd1c18909cf6294d407e5bba82f244`

## Zweck und Verhalten

Setzt das Spielerattribut `step_height` auf einen konfigurierten Wert. Der vorherige Attributwert wird beim Deaktivieren oder Weltwechsel wiederhergestellt.

## Einstellungen

| Einstellung | Standard | Grenzen |
|---|---:|---|
| Höhe | 1,0 | 0,6–2,5; Schritt 0,1 |

## Technische Prüfung

- **Attributzugriff:** prüft, ob ein Spieler und das Attribut verfügbar sind.
- **Baseline:** ursprünglicher Wert wird beim ersten Tick mit verfügbarem Attribut einmalig gespeichert.
- **Änderung:** bei jeder Tick-Abweichung wird der konfigurierte Wert erneut gesetzt.
- **Cleanup:** `onDisable` und `onWorldLeave` rufen `restore()` auf; bei verfügbarem Attribut wird der vorherige Wert wiederhergestellt.
- **Grenzfall:** wenn beim Cleanup kein Spieler/Attribut verfügbar ist, kann kein Restore mehr erfolgen; der alte Spieler ist dann normalerweise nicht mehr aktiv.
- **Threads/Ressourcen:** keine eigenen Listener oder Threads.
- **In-Game/Build:** nicht ausgeführt.

## Befunde und Verbesserungen

- Kein eindeutiger Fehler bei regulärem Lifecycle bestätigt.
- Das ständige Setzen bei jeder Abweichung überschreibt Änderungen anderer Mods, solange Step aktiv ist. Das ist ein möglicher Kompatibilitätskonflikt; kein automatischer Konflikt-Resolver vorhanden.
- Keine Bewegungsgeschwindigkeit oder Anti-Cheat-Umgehung angepasst.

## Offene Tests

- [ ] Höhe 0,6 / 2,5
- [ ] Aktivieren, Höhe während Aktivierung ändern, deaktivieren
- [ ] Weltwechsel bei aktivem Modul
- [ ] Zusammenspiel mit einem zweiten Mod, der step_height anpasst
- [ ] Respawn und Spielerwechsel

## Änderungsverlauf

| Datum | Änderung |
|---|---|
| 2026-11-16 | Step codebasiert geprüft; keine Java-Änderung. |
