# Freecam

- **Status:** Steuerungsänderung gespeichert; Build-/In-Game-Test offen
- **Java-Klasse / Anzeigename:** `FreecamController` / `Freecam`
- **Kategorie:** `MOVEMENT`
- **Mixin:** `CameraMixin`
- **Code-Commit:** `e7fbdc392a0f44b1e91be434ef2ddf1c7b9ef5b6`

## Steuerung

- **W/A/S/D:** Horizontale Bewegung relativ zur Kamera-Yaw-Richtung.
- **Space:** Kamera nach oben.
- **Shift:** Kamera nach unten.
- **Sprint:** Temporärer Geschwindigkeitsschub.
- Mausbewegung: Kamera drehen; horizontale Bewegung folgt jetzt der natürlichen Rechts-/Links-Richtung.

W/A/S/D ist unabhängig von der vertikalen Blickneigung. Nach oben oder unten schauen und W drücken bewegt die Kamera daher weiter horizontal. Vertikale Bewegung erfolgt nur über Space/Shift.

## Änderungen

- Beschleunigungs- und Bremsglättung wurde entfernt, damit die Kamera unmittelbar auf Tastendruck reagiert.
- Loslassen der Bewegungstaste stoppt die Kamera direkt; es gibt kein Nachgleiten.
- Diagonale Bewegungen werden normalisiert, damit sie nicht schneller sind als gerade Bewegung.
- Horizontale Maus-Yaw-Änderung wurde umgedreht, damit eine Mausbewegung nach rechts die Kamera auch nach rechts dreht.
- Blickrichtung, FreeLook-Modus, Schadens-Stopp, HUD und Wiederherstellung der Spielerposition bleiben erhalten.
- Kamerabewegung bleibt clientseitig; die Spielerposition wird weiterhin auf den gespeicherten Ausgangspunkt zurückgesetzt.

## Einstellungen

| Einstellung | Standard | Grenzen |
|---|---:|---|
| Geschwindigkeit | 0,6 | 0,1–3,0; Schritt 0,05 |
| FreeLook | aus | Ein/Aus |
| FreeLook-Reichweite | 4,0 | 0–50; Schritt 0,25 |
| Stopp bei Schaden | an | Ein/Aus |
| Freecam-Anzeige | an | Ein/Aus |
| Kamera-Sensitivität | 0,15 | 0,03–0,5; Schritt 0,01 |

Die frühere Einstellung `Gleiten` wurde entfernt, weil die Bewegung jetzt bewusst ohne Verzögerung reagiert.

## Offene Tests

- [ ] Mausbewegung links/rechts
- [ ] W/A/S/D bei waagerechtem und steilem Blickwinkel
- [ ] Space/Shift nach oben und unten
- [ ] Diagonale Bewegung und Sprint-Boost
- [ ] Sofortiger Stillstand beim Loslassen
- [ ] FreeLook, Schaden-Stopp und Weltwechsel
- [ ] Build und In-Game-Test
