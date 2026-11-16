# HitParticles

- **Status:** Code geprüft; In-Game-Test offen
- **Java-Klasse / Anzeigename:** `HitParticles`
- **Kategorie:** `VISUALS`
- **Quellcode-SHA:** `e11d55a96bf056571773352c32fda60ef6715c80`

## Zweck und Verhalten

Erzeugt lokal einen Partikeleffekt, wenn eine nahe lebende Entity gegenüber dem zuvor gespeicherten Lebenswert Lebenspunkte verliert. Es verändert weder Treffer noch Angriffe.

## Einstellungen

| Einstellung | Standard | Grenzen / Optionen |
|---|---:|---|
| Effekt | Crit | Crit, Herzen, Flamme, Witch, Soul, Enchant |
| Partikelmenge | 8 | 2–20 |
| Reichweite | 8 | 3–16 |

## Technische Prüfung

- **Erkennung:** fragt LivingEntities in einer um die Spielerbox erweiterten Region ab; der eigene Spieler wird ausgeschlossen.
- **Auslöser:** jeder beobachtete Rückgang der Lebenspunkte, unabhängig von der Schadensquelle.
- **Baseline:** erste Beobachtung einer UUID löst keinen Partikelburst aus.
- **Lifecycle:** Map wird bei Aktivierung, Deaktivierung und Weltwechsel geleert.
- **Speichergrenze:** bei mehr als 512 gespeicherten UUIDs wird die gesamte Map geleert.
- **Performance:** höchstens 20 lokale Partikel pro erkanntem Rückgang; Entity-Abfrage jede Clienttick.
- **In-Game/Build:** nicht ausgeführt.

## Befunde und Verbesserungen

- Die Beschreibung „wenn nahe Entities Schaden bekommen“ entspricht dem Code. Es wird nicht zwischen Angriffsschaden, Fallschaden oder Umweltschaden unterschieden.
- Das vollständige Leeren der Map ab 513 Einträgen begrenzt Speicher, verwirft aber auch gültige Vergleichswerte. Eine gezielte Bereinigung nicht mehr geladener Entity-UUIDs wäre eine mögliche, noch nicht getestete Verbesserung.
- Kein bestätigter Fehler.

## Offene Tests

- [ ] Schaden durch Angriff und Umwelteinfluss
- [ ] Reichweite 3/16
- [ ] jede Partikelart
- [ ] 20 Partikel und viele Entities
- [ ] Weltwechsel und Re-Enable

## Änderungsverlauf

| Datum | Änderung |
|---|---|
| 2026-11-16 | Erste Codeprüfung; keine Java-Änderung. |
