# FakeName

- **Anzeigename:** `FakeName`
- **Implementierung:** `src/main/java/dev/nightfall/modules/Privat.java` (interner Klassenname bleibt fuer Kompatibilitaet bestehen)
- **Kategorie:** HUD

## Verhalten

FakeName ersetzt ausschliesslich die lokal gerenderte Identitaet des eigenen Spielers. Die echte Minecraft-Konto-ID, der serverseitige Name und die vom Server empfangenen Daten werden nicht veraendert. Der Server und andere Spieler koennen weiterhin die echte Identitaet sehen.

Fruehere Fake-Rang-/Clan-Einstellungen werden nicht uebernommen. Tab-Liste und F5-Namensschild werden komplett durch den eingestellten Fake-Namen ersetzt; dadurch bleiben keine Server-Praefixe oder Clan-Suffixe stehen. Im Chat wird der komplette Praefix vor dem eigenen Namen entfernt. Es wird nur der eingestellte Fake-Name dargestellt. Alle frueher gespeicherten Werte aus dem alten Modul `Privat` werden zurueckgesetzt und nicht in `FakeName` importiert. Die Anzeigen sind unabhaengig schaltbar:

- **Im Chat anzeigen**
- **In Tab-Liste anzeigen**
- **In F5 anzeigen** (eigenes Namensschild in Third Person / FreeLook)

Ist eine Anzeige ausgeschaltet, wird dort der eigene Kontoname ausgeblendet statt als echter Name stehen gelassen. Wird kein Fake-Name eingetragen, wird der Name in den aktivierten Anzeigen ebenfalls leer gelassen.

Die lokalen Sidebar-Zeilen mit den Bezeichnungen Rang, Rank, Clan oder Name werden ausgeblendet. Die uebrigen unterstuetzten Statistikwerte koennen weiterhin lokal ersetzt werden.

## Grenzen

Diese Funktion ist eine lokale Darstellungs-/Privatsphaerenhilfe. Sie ist kein serverseitiger Namenswechsel und verhindert nicht, dass Server, Plugins oder andere Spieler die echte UUID bzw. den echten Kontonamen erkennen koennen. Ein Build- und In-Game-Test fuer Minecraft 1.21.11 / Fabric / Java 21 steht noch aus.
