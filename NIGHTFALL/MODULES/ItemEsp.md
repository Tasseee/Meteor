# ItemEsp

- **Status:** Code geprüft; In-Game-Test offen
- **Java-Klasse:** `ItemEsp`
- **Anzeigename:** `ItemESP`
- **Kategorie:** `VISUALS`
- **Modul-SHA:** `bdc4c46d4eed423c51639db69a32dadb459daaa2`
- **Gemeinsame Renderlogik:** `EntityScanEsp` / `EspBase`

## Zweck und Verhalten

Markiert geladene Item-Entities und zeigt den Item-Anzeigenamen sowie die Stack-Anzahl. Das Modul übernimmt die gemeinsame Entity-ESP-Renderlogik.

## Einstellungen

Keine modulspezifischen Einstellungen. Geerbte Einstellungen:

| Einstellung | Standard |
|---|---|
| Reichweite | 512 |
| Max Objekte | 512 |
| Linien-Antialiasing | an |
| Box | 3D |
| Name | an |
| Distanz | an |
| Health | an |
| Nur sichtbare | aus |
| Tracer | aus |
| Transparenz | 1,0 |

Die gemeinsame Range-Korrektur in `EspBase` ändert den Default hier nicht, da ItemEsp mit `defRange=512` und `maxRange=512` konstruiert wird.

## Technische Prüfung

- **Erkennung:** akzeptiert alle geladenen Item-Entities.
- **Label:** Stack-Anzeigename plus Anzahl.
- **Renderpfad:** scannt geladene Entities bei jedem HUD-Render-Aufruf; speichert pro Treffer eine interpolierte Box und Distanz.
- **Allokationen:** pro Frame entstehen RenderTarget-Datensätze für passende Entities; Buffer-Liste selbst wird wiederverwendet.
- **Geerbte Health-Einstellung:** Item-Entities sind keine LivingEntity; die Health-Zeile hat bei diesem Modul keine sichtbare Wirkung.
- **Build/In-Game:** nicht ausgeführt.

## Befunde und Verbesserungen

### IE-01 – geerbte Health-Option ohne Wirkung

Die gemeinsame Option `Health` wird von EntityScanEsp nur für LivingEntity angezeigt. ItemESP akzeptiert Item-Entities, daher bleibt die Einstellung wirkungslos und kann in der GUI verwirren. Verbesserung wäre, die Option für ItemESP auszublenden oder die gemeinsame Setting-Struktur um konfigurierbare Features zu ergänzen.

## Offene Tests

- [ ] Ein Item, mehrere gestapelte Items und weit entfernte Items
- [ ] Range-/Max-Objekte-Grenzen
- [ ] sichtbare-only und Box-Stile
- [ ] Itemnamen mit Sonderzeichen / langen Anzeigenamen
- [ ] Health-Option in GUI

## Änderungsverlauf

| Datum | Änderung |
|---|---|
| 2026-11-16 | Modul und geerbte Renderlogik geprüft. |
