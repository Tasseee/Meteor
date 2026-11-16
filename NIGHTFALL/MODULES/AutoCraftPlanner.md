# AutoCraftPlanner

- **Status:** Code geprüft; In-Game-/Build-Test offen
- **Java-Klasse:** `AutoCraftPlanner`
- **Anzeigename:** `AutoCraft Planner`
- **Kategorie:** `WORLD`
- **Quellpfad:** `src/main/java/dev/nightfall/modules/AutoCraftPlanner.java`
- **Quellcode-SHA:** `97a382b73626404afe06668b40f72a9d804dd970`
- **Registrierung:** `ModuleManager.init()`

## Zweck und Verhalten

Das Modul prüft eine manuell eingegebene Zutatenliste gegen das Spielerinventar und zeigt den Fortschritt im HUD. Die Beschreibung im Konstruktor stellt ausdrücklich klar, dass es **nicht automatisch craftet**.

Das Format jeder Eingabe ist `item-id:menge`; mehrere Einträge werden mit Kommas getrennt. Mengen außerhalb von 1 bis 64 sowie nicht parsebare Mengen werden übersprungen. Nicht vorhandene Registry-Einträge bzw. AIR werden ignoriert. Die Inventarmenge wird über alle Inventarslots summiert.

## Einstellungen

| Einstellung | Standard | Verhalten |
|---|---|---|
| Zutaten (ID:Menge, Komma) | `minecraft:oak_planks:4,minecraft:stick:2` | Zutatenliste; maximal 256 Zeichen |
| HUD anzeigen | `true` | Schaltet die Anzeige ein/aus |

Die verwendete `StringSetting` entfernt Zeilenumbrüche/Tabs, trimmt und begrenzt die Eingabe auf die konfigurierte Länge.

## Technische Prüfung

- **Registrierung/Kategorie:** Code bestätigt, `WORLD`.
- **HUD:** fester Startpunkt (8, 96); zeigt erfüllte/gesamte Anforderungen und pro Eintrag Besitz-/Sollmenge.
- **Nullfall:** ohne Spieler liefert die Parser-Methode eine leere Liste; Rendering prüft den Spieler zusätzlich.
- **Lebenszyklus:** keine eigenen Listener, Ressourcen oder Hintergrundthreads.
- **Fehlerbehandlung:** ungültige Mengen werden still übersprungen; kein eigener Logeintrag oder Hinweis zur fehlerhaften Eingabe.
- **Performance:** Inventar wird einmal pro Render-Aufruf pro gültigem Eintrag durchlaufen; bei sehr langen Listen kann das unnötige Wiederholungen erzeugen.
- **Laufzeit/Version:** nicht in Minecraft getestet; Build nicht ausgeführt.

## Befunde und Verbesserungen

### Verbesserungskandidat AC-01 – doppelte Zutaten zusammenfassen

Mehrere Einträge derselben Item-ID werden aktuell unabhängig behandelt. Beispiel: zweimal `minecraft:iron_ingot:4` erzeugt zwei getrennte Zeilen mit Sollmenge 4 statt einer zusammengefassten Sollmenge 8. Dadurch kann die Anzeige den Gesamtbedarf unterschätzen. Das Verhalten folgt direkt aus der Listenerzeugung; ein In-Game-Test steht noch aus.

**Empfehlung:** identische normalisierte Item-IDs vor dem Inventarvergleich aggregieren. Die Eingabe soll weiterhin bis 64 je Eintrag zulassen; die aufsummierte Gesamtmenge darf entsprechend größer sein.

### Verbesserungskandidat AC-02 – Parser-Rückmeldung

Fehlerhafte Einträge werden still ignoriert. Eine spätere UI-Rückmeldung mit Anzahl ungültiger Einträge würde Konfigurationsfehler leichter erkennbar machen.

## Nutzer- und In-Game-Tests

Noch keine In-Game-Ergebnisse vorhanden. Insbesondere wurden Duplikat-Eingaben, unbekannte Item-IDs, Leerzeichen und volle 256-Zeichen-Eingaben nicht im Spiel getestet.

## Änderungsverlauf

| Datum | Änderung |
|---|---|
| 2026-11-16 | Erste Codeprüfung; keine Java-Änderung in diesem Audit-Eintrag. |
