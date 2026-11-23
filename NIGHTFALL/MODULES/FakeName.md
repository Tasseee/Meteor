# FakeName

- **Anzeigename:** `FakeName`
- **Implementierung:** `src/main/java/dev/nightfall/modules/Privat.java` (interner Klassenname bleibt fuer Kompatibilitaet bestehen)
- **Kategorie:** HUD

## Verhalten

FakeName ersetzt ausschliesslich die lokal gerenderte Identitaet des eigenen Spielers. Die echte Minecraft-Konto-ID, der serverseitige Name und die vom Server empfangenen Daten werden nicht veraendert. Der Server und andere Spieler koennen weiterhin die echte Identitaet sehen.

Rang- und Clan-Tags direkt vor dem eigenen Namen werden in den unterstuetzten lokalen Textanzeigen entfernt. Es wird nur der eingestellte Fake-Name dargestellt. Die Anzeigen sind unabhaengig schaltbar:

- **Im Chat anzeigen**
- **In Tab-Liste anzeigen**
- **In F5 anzeigen** (eigenes Namensschild in Third Person / FreeLook)

Ist eine Anzeige ausgeschaltet, wird dort der eigene Kontoname ausgeblendet statt als echter Name stehen gelassen. Wird kein Fake-Name eingetragen, wird der Name in den aktivierten Anzeigen ebenfalls leer gelassen.

Die lokalen Sidebar-Zeilen mit den Bezeichnungen Rang, Rank, Clan oder Name werden ausgeblendet. Die uebrigen unterstuetzten Statistikwerte koennen weiterhin lokal ersetzt werden.

## Grenzen

Diese Funktion ist eine lokale Darstellungs-/Privatsphaerenhilfe. Sie ist kein serverseitiger Namenswechsel und verhindert nicht, dass Server, Plugins oder andere Spieler die echte UUID bzw. den echten Kontonamen erkennen koennen. Ein Build- und In-Game-Test fuer Minecraft 1.21.11 / Fabric / Java 21 steht noch aus.
