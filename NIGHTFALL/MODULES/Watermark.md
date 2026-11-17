# Watermark

- **Registrierter Modulname:** `Watermark`
- **Auditstatus:** Vollständig geprüft (bestehender Auditstand; aktueller Quelltext erneut abgeglichen)
- **Quellpfad:** `src/main/java/dev/nightfall/modules/Watermark.java`
- **Aktueller Modul-Blob-SHA:** `b8e9cf2233c39490b79bc99f576ee6d00572cee2`
- **Registrierungsquelle:** `src/main/java/dev/nightfall/module/ModuleManager.java`
- **Registrierungsquellen-SHA:** `7329859ce075c9708f749f30b00e904fdf523d6b`

## Zweck und belegtes Verhalten

Das HUD zeigt `Nightfall | <Profilname>`. Der Profilname wird über `mc.field_1724.method_7334().name()` gelesen. Das ist der Game-Profile-Name des eigenen lokalen Spielers, nicht der Komponenten-/Anzeigename eines anderen Spielers.

Wenn `mc.field_1724` null ist, wird `Nicht verbunden` angezeigt. Die Breite wird anhand des gerenderten Textes berechnet; die Standardposition ist `(4, 4)`, die Höhe 12 Pixel. Das Modul wird im Konstruktor aktiviert.

## Befund und Korrektur

Die bisherige Modulbeschreibung lautete „Zeigt Name und Version“, obwohl der gezeichnete Text keine Versionsnummer enthielt. Das war ein klar belegter Beschreibungsmismatch. Die Beschreibung wurde auf „Zeigt den eigenen Minecraft-Profilnamen.“ korrigiert.

- **Commit:** [`819ea2d`](https://github.com/Tasseee/Meteor/commit/819ea2d17cf76c547e2bbbf55e3d404f4d63f797)
- **Geänderte Java-Zeile:** ausschließlich der Beschreibungstext im Konstruktor.
- **Keine Änderung:** Namensquelle, Darstellung, HUD-Position oder sonstige Laufzeitlogik.

## Weitere Prüfpunkte

- **Nullfall:** Spielerreferenz wird vor dem Lesen des Profils geprüft.
- **Eigene Listener/Ressourcen:** in dieser Klasse keine registriert; keine eigene Ressourcenbereinigung erforderlich.
- **Performance:** kurze Textmessung und HUD-Zeichnung; keine auffällige zusätzliche Sammlung/Allokationsschleife im Modulcode.
- **In-Game:** nicht ausgeführt; tatsächliche Darstellung bleibt als visueller Laufzeittest offen.

## Status

Die Account-/Profilnamenquelle ist im Code bestätigt. Ein Screenshot aus dem laufenden Client liegt nicht vor.
