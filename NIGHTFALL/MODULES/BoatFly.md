# BoatFly

- **Status:** Code geprüft; Fahrzeug-/Weltwechseltest offen
- **Quellcode-SHA:** `58f7a33c51a9ea8640186ff97d9b3f1f3f3fd0c7`
- **Kategorie:** `MOVEMENT`

## Verhalten

Ändert die lokale Steuerung eines bestiegenen Boot-Entities. Speichert den vorherigen No-Gravity-Zustand des Boots und setzt diesen während des Moduls. Die Kameraausrichtung und Bewegungstasten bestimmen die Richtung.

## Einstellungen

| Einstellung | Standard | Grenzen |
|---|---:|---|
| Geschwindigkeit | 1,3 | 0,1–4,0 |
| Vertikal | 0,9 | 0,1–3,0 |

## Technische Prüfung

- Fahrzeug wird nur erfasst, wenn der Spieler tatsächlich sitzt und die Entity-ID den Text `boat` enthält.
- Beim Fahrzeugwechsel wird der vorherige Zustand wiederhergestellt.
- Bei Aussteigen und Deaktivierung wird `restoreBoat()` aufgerufen.
- Es gibt keinen `onWorldLeave()`-Override; die gespeicherte Entity-Referenz bleibt bis zum nächsten Tick oder Deaktivieren erhalten.
- Keine eigenen Threads oder Listener.

## Befunde

- **Lifecycle-Risiko:** Weltwechsel setzt das kontrollierte Boot nicht unmittelbar zurück. Die Referenz wird spätestens beim nächsten Tick ohne Spieler/Boot oder beim Deaktivieren freigegeben. Ein explizites WorldLeave-Cleanup wäre sauberer.
- Keine Bewegungs- oder Anti-Cheat-Optimierung vorgenommen.

## Tests offen

- [ ] Boot besteigen/aussteigen
- [ ] Bootwechsel
- [ ] Weltwechsel während aktiv
- [ ] deaktivieren in/außerhalb des Boots
- [ ] No-Gravity-Originalzustand bleibt erhalten

| Datum | Änderung |
|---|---|
| 2026-11-16 | Codeprüfung; keine Verhaltensänderung. |
