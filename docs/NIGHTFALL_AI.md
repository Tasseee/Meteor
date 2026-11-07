# Nightfall – Offline-Agent und lokale Aktionen

**Offline-Modus auf dem Branch `nightfall/offline-agent-core`:** Chat-Anfragen werden nicht mehr an OpenAI oder andere Anbieter gesendet. Alte Provider-URLs und API-Schlüssel werden beim Laden der Nightfall-AI-Konfiguration durch lokale Offline-Defaults ersetzt. Cloud-Chat, Cloud-Transkription und Cloud-Sprachausgabe sind damit deaktiviert. Es gibt aktuell **keinen Sprachparser und kein lokales Sprachmodell**; die Bedienung des Offline-Agenten erfolgt vorerst über die unten aufgeführten Minecraft-Befehle. Stimme und natürlichsprachlicher Chat bleiben bewusst spätere Ausbaustufen.

## Offline-Agent im Spiel

Alle Aufträge laufen über den deterministischen `OfflineAgentCore` und bestehende lokale Minecraft-Controller. Es werden keine KI-Modelle oder externen KI-Endpunkte benötigt. Der Agent kann mehrere Aufträge in eine begrenzte Warteschlange aufnehmen, führt aber nur eine Minecraft-Aktion gleichzeitig aus. Abhängige Schritte werden erst freigeschaltet, wenn ihr Vorgänger als erfolgreich geprüft wurde.

**Jeder verändernde Schritt benötigt eine Vorschau und Bestätigung.** Nach dem Einreihen zeigt Nightfall die Vorschau im Chat. Bestätige sie mit `/nf agent confirm`, lehne mit `/nf agent reject` ab. Nach der Ausführung ist das Ergebnis zunächst ungeprüft; prüfe die Welt und Inventaränderung und melde danach `/nf agent verify success` oder `/nf agent verify failed`. `/nf agent cancel` stoppt die aktive Offline-Aufgabe und verwirft wartende Schritte.

Wichtige Befehle:

- `/nf agent status` – Auftragswarteschlange, Vorschau und Prüfergebnis ansehen.
- `/nf agent gather wood minecraft:oak_log 16`, `/nf agent gather block minecraft:cobblestone 32`, `/nf agent gather ore minecraft:raw_iron 8` – lokale Sammelaufträge.
- `/nf agent craft minecraft:oak_planks 32`, `/nf agent smelt minecraft:raw_iron 8`, `/nf agent eat`, `/nf agent sort` – Crafting, Schmelzen, Essen und Inventarsortierung.
- `/nf agent wood 32` – mehrstufig: Eichenstämme sammeln, danach Eichenbretter herstellen.
- `/nf agent iron 8` – mehrstufig: rohes Eisen sammeln, danach in einem erreichbaren Ofen schmelzen.
- `/nf agent build starter`, `/nf agent build large_house`, `/nf agent build watchtower` – vorhandene Bauvorlagen vorbereiten und bauen.
- `/nf agent build resume` – einen vom vorhandenen Builder gespeicherten und server-/dimensionsgebundenen Bau-Checkpoint fortsetzen.
- `/nf agent schematic datei.schem` – ersten Abschnitt einer lokalen Schematic vorbereiten.
- `/nf agent navigate X Y Z`, `/nf agent mine X Y Z`, `/nf agent walk 20` – begrenzte Navigation, Abbau und Bewegung.
- `/nf agent search ancient_city`, `/nf agent attack`, `/nf agent guard` – Strukturhinweise suchen oder nahe feindliche Mobs abwehren.

Die Sammel-, Crafting-, Schmelz-, Bau- und Kampfaktionen verwenden bestehende Client-Controller und deren lokale Validierung. Kampfziele sind ausschließlich feindliche Minecraft-Mobs; Spieler und neutrale/passive Tiere werden nicht ausgewählt. Die Grenzen der vorhandenen Controller gelten weiterhin (z. B. geladene Chunks, sichere Wege, begrenzte Laufzeiten und Aktionslimits). Die manuelle Ergebnisprüfung ist absichtlich konservativ: Ein zurückgegebener Starttext ist kein Beweis, dass ein mehrtaktiger Vorgang bereits fertig ist.

**Noch nicht enthalten:** freie Spracheingabe/NLP, lokale Sprachmodelle, echte automatische Beobachtungs-Auswertung, dauerhaft gespeicherte Agenten-Auftragswarteschlange, vollautomatisches Farming und selbstständiges Lernen. Der Aufgabenstatus liegt derzeit im Arbeitsspeicher des laufenden Clients. Bei einem Serverwechsel werden offene Agentenaufträge verworfen; die bestehenden Controller können ihre eigenen unterstützten Checkpoints (z. B. Baufortschritt) separat sichern. Diese Funktionen werden nicht als fertig ausgegeben.

Die folgenden älteren Abschnitte dokumentieren die vorhandenen Minecraft-Aktionen und deren lokale Grenzen. Hinweise auf einen konfigurierten KI-Anbieter beschreiben den früheren Provider-Betrieb; dieser ist auf diesem Branch deaktiviert.

---

# Nightfall AI – Extensible Skills and Actions

Nightfall AI is an in-game Minecraft companion with an extensible, allow-listed tool system. The AI model may choose from the tools exposed by the client, but **the client validates every argument and controls every execution**.

## Struktur finden

Nightfall bietet die bestätigungspflichtige Aktion `locate_structure`. Sie sendet eine Struktur-ID wie
`minecraft:village`, `minecraft:fortress`, `minecraft:bastion_remnant`, `minecraft:ancient_city`,
`minecraft:stronghold` oder `minecraft:trial_chambers` an den nativen Serverbefehl
`/locate structure`. Der Server muss den Befehl unterstützen und erlauben; Koordinaten werden in dessen
Chatantwort angezeigt. Sobald eine passende Koordinatenantwort im Format `[x, y, z]` erkannt wird,
startet Nightfall automatisch die begrenzte lokale A*-Navigation zum gemeldeten Fundort. Es teleportiert
niemals. Die Wegsuche nutzt ausschließlich geladene Chunks, sichere Standflächen und abschnittsweise
Routenplanung. Wenn kein sicherer Wegabschnitt gefunden wird, stoppt Nightfall und fordert den Spieler
auf, näher heranzugehen oder die Umgebung laden zu lassen. Der Spieler kann die Bewegung jederzeit stoppen.
Eine Serverantwort muss abgewartet werden; das Senden eines Befehls ist kein Beweis, dass eine Struktur
gefunden wurde.

## Setup

1. Start Nightfall once. It creates `config/nightfall/ai.json`.
2. Set `apiKey` to an API key from your chosen provider.
3. Keep `baseUrl` and `model` aligned with that provider. The default points to an OpenAI-compatible Chat Completions endpoint.
4. The model/provider must support Chat Completions function/tool calling.
5. Restart Minecraft or reopen the chat and send a message.

Example:

```json
{
  "baseUrl": "https://api.openai.com/v1/chat/completions",
  "model": "gpt-4o-mini",
  "apiKey": "YOUR_API_KEY",
  "shareWorldContext": true,
  "audioBaseUrl": "https://api.openai.com/v1",
  "transcriptionModel": "gpt-4o-mini-transcribe",
  "speechModel": "gpt-4o-mini-tts",
  "speechVoice": "alloy",
  "voiceInputEnabled": false,
  "voiceOutputEnabled": false
}
```

The key is stored locally as plain text in the Minecraft config folder. Do not upload or share this file, include it in a bug report, or commit it to a repository. Nightfall does not log the key.

## In-game use

- Press **J** to open Nightfall AI (default; configurable as `aiKey` in `config/nightfall.json`).
- Press **Enter** or click **SEND** to send a message.
- Press **F8** or click **CALL** to start the persistent voice call; press F8 or **STOP** again to end it. Press **F9** to mute/unmute the microphone without ending the call. While muted, microphone audio is not sent to the provider; the audio input device remains open until the call is stopped with F8.
- Spoken messages are handled directly by the live voice agent; typed messages sent while the call is active join the same conversation.
- The call remains active when the chat screen closes and is shown by a small HUD indicator that also shows whether the microphone is muted or Nightfall is speaking. F9 works outside the chat screen too. Disconnecting or exiting Minecraft stops the microphone and speaker.
- Press **Escape** to close the chat.
- Use the mouse wheel to scroll through the current conversation.

When `shareWorldContext` is enabled, each request can include a bounded client-side snapshot: dimension, biome, position, time/weather, local light level, health/hunger, movement state, armor and hand items with remaining durability, a targeted-block tool comparison, armor and utility-tool enchantments, compatible enchanted-book candidates, up to twelve inventory item summaries, nearby client-visible mobs and dropped items, plus local risk and survival-readiness assessments. The readiness check covers equipped armor coverage/durability, the main-hand item's durability, food and torch counts, shield presence, and limited Nether/End preparation hints. The equipment advisor is limited to player armor plus pickaxes, shovels and hoes in the normal inventory/hotbar. It compares same-slot armor using additive base attributes, reports enchantments separately, and checks individual book candidates for item applicability and known incompatibilities. It does not estimate anvil costs, calculate complete damage reduction, inspect every item type, or apply anything. This is a suggestion based on currently available data, not a complete assessment of the world or a guarantee of safety. Chat text and enabled context are sent to the configured provider.

## Lokale Survival-Bereitschaft

Mit aktiviertem Weltkontext ergänzt Nightfall eine rein lesende Bereitschaftsprüfung. Sie bewertet die vier ausgerüsteten Rüstungsslots und deren Resthaltbarkeit, die Resthaltbarkeit des Gegenstands in der Haupthand, essbare Gegenstände und Fackeln in den 36 normalen Inventar-/Hotbar-Slots sowie einen Schild in Inventar oder Nebenhand. Für den Nether wird geprüft, ob ein goldenes Rüstungsteil getragen wird; für den End-Hinweis wird geprüft, ob Wassereimer oder Enderperlen im normalen Inventar erkannt wurden. Bei niedriger lokaler Lichtstufe ohne Fackeln kann Nightfall Licht als Vorbereitung empfehlen.

Die Hinweise sind bewusst begrenzt: Ein fehlender Schild ist nur ein optionaler Verbesserungsvorschlag. Goldrüstung bedeutet nicht, dass Piglins in der Nähe sind. Wassereimer und Enderperlen sind keine zwingenden Voraussetzungen für den End-Besuch. Der Check untersucht keine nicht geladenen Bereiche, keine geplanten Routen und keine Verzauberungs-Kombinationen. Er verändert weder Inventar noch Welt. Ohne einen aktuell freigegebenen Weltkontext darf Nightfall keine Bereitschaftsprüfung behaupten und fordert stattdessen dazu auf, den Schalter **WELTKONTEXT: AN** zu aktivieren.

## Lokaler Equipment- und Verzauberungsberater

Mit aktiviertem Weltkontext liest Nightfall die vier getragenen Rüstungsteile und passende Rüstungsalternativen aus den 36 normalen Inventar-/Hotbar-Slots. Zusätzlich werden Spitzhacken, Schaufeln und Hacken sowie deren Haltbarkeit und tatsächliche gespeicherte Verzauberungen erfasst. Die Liste ist begrenzt; es werden höchstens zwölf Einträge in der KI-Zusammenfassung angezeigt.

Rüstung wird nur innerhalb desselben Slots verglichen. Die angezeigten Basis-Rüstungs- und Zähigkeitswerte stammen aus den additiven Attributmodifikatoren der Item-Komponenten. Verzauberungen und Haltbarkeit werden separat ausgewiesen und nicht in den Basiswert hineingerechnet. Die Werkzeugauswahl für einen gerade anvisierten Block bleibt die konkrete Vergleichsgrundlage für Abbau-Eignung; daraus folgt keine allgemeine Rangliste für beliebige Blöcke.

Nightfall untersucht bis zu 24 gefundene verzauberte Bücher und zeigt höchstens zehn einzelne Kandidaten. Für jede vorgeschlagene Buch-Verzauberung prüft es, ob sie laut Minecraft auf den Gegenstand anwendbar und mit den aktuell vorhandenen Verzauberungen kombinierbar ist. Das ist kein garantierter Amboss-Ausgang: XP-Kosten, Materialkosten, Reihenfolge, endgültige Amboss-Ausgabe und zufällige Verzauberungstisch-Angebote werden nicht berechnet. Bücher werden weder angewendet noch bewegt; es werden keine Slots, Ausrüstungsstücke oder Weltblöcke geändert. Ohne aktuellen, aktiv geteilten Weltkontext behauptet Nightfall keine Ausrüstungsanalyse.

## Available tools

The AI model can select from a fixed client-side allow-list:

| Tool | What it does | Confirmation |
|---|---|---|
| `locate_biome` | Sends the standard `/locate biome <id>` command for a supported biome | Required |
| `add_waypoint` | Saves a waypoint at the current player position | Required |
| `set_module_state` | Enables or disables one existing Nightfall module by exact name | Required |
| `get_inventory` | Reads item names, exact Minecraft item IDs, and counts; separates the 9-slot hotbar from the remaining inventory | Required; inventory data is sent to the AI provider |
| `sort_inventory` | Sorts ordinary carried stacks in the 36 normal inventory/hotbar slots; preserves stack counts and item components while keeping protected combat-related items in place | Required |
| `get_player_status` | Reads current dimension, coordinates, and health | Required; player state is sent to the AI provider |
| `random_teleport` | Sends the common `/rtp` request to the server | Required; server must support it |
| `run_server_command` | Sends one ordinary player-requested Minecraft or plugin command and watches briefly for a related server chat response | Required; dangerous server administration and world-edit command roots are blocked |
| `build_structure` | Builds one of three validated templates and accepts an optional material ID/name | Required |
| `build_custom_structure` | Builds an AI-generated connected plan of up to 512 blocks in a bounded 32x32x32 grid; supports an optional per-block material palette | Required; validates connectivity, per-material inventory counts, height, and replaceable targets |
| `build_starter_shelter` | Backward-compatible alias for the 4x4 starter shelter | Required |
| `get_build_status` | Reads confirmed builder progress, current phase, pause reason, blueprint and material palette | Read-only; no confirmation |
| `pause_build` | Pauses after any in-flight placement has been confirmed | No; safe control action |
| `resume_build` | Resumes a manually paused build | Required |
| `stop_build` | Stops the active builder queue; already placed blocks remain | No; safe control action |

After a tool call, Nightfall displays a preview. The tool is executed only after the player clicks **AKTION AUSFÜHREN**. Cancelling returns a cancellation result to the model and does not execute the action.

## Safety and current limitations

- Tool calls are not arbitrary code. Nightfall never exposes shell execution or arbitrary Java.
- The client validates the tool name and arguments against a fixed allow-list. The model cannot invent a new action.
- Server-command feedback is matched at word boundaries to the actual command root, so short commands such as `tp` do not accidentally match unrelated words. The ordinary command tool accepts a single command with no command chaining. It blocks server administration roots (for example `op`, `ban`, `stop`, `whitelist`, common permission-management aliases) and high-impact world-edit roots/aliases (for example `execute`, `fill`, `clone`, `setblock`, `worldedit`, `we`, `fawe`, `//set`, `//paste`, `//undo`). Every command is shown exactly as it will be sent and needs confirmation. The server still decides whether the user has permission.
- Server permissions remain authoritative. The biome search command can fail on servers that do not permit `/locate`; Nightfall does not bypass permissions or teleport the player.
- For ordinary commands, Nightfall watches game/system chat and player chat for up to 30 seconds, including while the AI screen is closed; only one command response may be pending at a time to avoid mixing replies. It can surface a likely related response or explicit permission/unknown-command rejection, then passes that server message back to the AI for explanation without repeating the command. If no unique response arrives or the connection drops, it reports execution as unconfirmed. Generic plugin messages cannot always be correlated reliably. A sent command is never reported as successfully executed without a clear server response.
- Tool-capable OpenAI-compatible models are required. Some endpoints may reject function calling.
- A short follow-up tool loop is supported, with one tool call at a time and a bounded number of consecutive actions.
- The in-memory chat session is reused when the AI screen is closed and reopened. The visible transcript remains available during the client session; on server disconnect, pending actions are cancelled and the AI context is cleared so old world/inventory data cannot leak into a different server.
- The builder offers three deterministic templates: a 4x4 starter shelter (54 blocks), a 6x6 house with three-block-high walls (130 blocks), and a 5x5 watchtower with four six-block-high corner pillars (74 blocks). For other user-requested structures, `build_custom_structure` accepts an AI-generated plan of up to 512 blocks in a 32x32x32 grid.
- The fixed templates require a level, continuously solid ground surface. Within their planned block positions, Nightfall can now excavate ordinary removable obstacles after preview; it refuses block entities, fluids, falling blocks and unbreakable blocks. The preview reports the excavation count and up to eight obstacle block IDs (plus any remaining block-type count); each mined block is checked against its previously observed block ID.
- The read-only `get_build_status` tool lets the player ask for confirmed progress, current building phase, pause reason and active materials without approving another action.
- The builder chooses block items from all 36 main inventory slots (not only the hotbar), or honors exact material item IDs/localized names supplied globally or for individual custom-plan blocks. Before placement, it swaps a needed stack from the main inventory into an empty hotbar slot; if the hotbar is full it uses the selected slot and moves the displaced stack back into the source inventory slot. In Survival it checks the full required count per material across the entire inventory; in Creative it only requires one matching inventory stack because placements do not consume blocks. Ambiguous partial names are rejected consistently for both preset and custom builds. It verifies the required count for each material and all safe target positions, then sends genuine block-placement interactions one at a time. Ambiguous localized material names are rejected instead of selecting an arbitrary matching item. Custom plans reject duplicates, out-of-range coordinates, occupied targets, and disconnected/floating components; every component must anchor to an existing solid block. It waits for the corresponding world block state before counting a placement, retries up to two times after a timeout, and stops safely when the server rejects a placement. If a block is out of reach, the queue pauses until the player moves closer. The originally selected hotbar slot is restored when the build finishes or stops. Every builder confirmation shows a top-down ASCII footprint for the y=0 layer, counts blocks per vertical layer, and lists the material-symbol legend; the chat renderer preserves these line breaks.
- Combat against nearby hostile vanilla mobs and bounded A* navigation through already-loaded chunks are implemented. Navigation uses safe stand positions and re-plans in segments; camera rotation is smoothed, but this is not perfect obstacle handling and may stop at unloaded terrain, fluids or unavailable paths. Building places approved blocks automatically, but the player must stay within reach and confirm each plan. Complex farms requiring full entity, fluid, container or redstone state reconstruction are not guaranteed.
- Voice input/output is available as an opt-in feature (see below). Arbitrary code execution remains out of scope.
- When the inventory tool is approved, its result is sent back to the configured AI provider. It includes exact item IDs and distinguishes the hotbar from the rest of the inventory. The builder can use both areas and brings a required stack into the hotbar with a normal SWAP interaction when needed. Do not approve it if you do not want to share that inventory snapshot.

Future skills should stay narrowly scoped, validated, previewed, and explicitly confirmed before world changes or data disclosure.


## Lokale Bauplan-Wissensdatenbank

Nightfall enthält eine lokale, erweiterbare JSON-Wissensdatenbank und das read-only Werkzeug
`search_blueprints`. Beim ersten Aufruf wird `src/main/resources/nightfall/blueprints.json` nach
`config/nightfall/blueprints.json` kopiert. Die lokale Datei kann ohne Mod-Neubau erweitert werden
(maximal 250 Einträge, Schema-Version 1, UTF-8). Jeder Eintrag enthält ID, Minecraft-Version,
Tags, Quelle, Validierungsstatus und Automatisierungsstatus. `SUPPORTED_TEMPLATE` verweist auf
ein bestehendes Nightfall-Bauwerkzeug; `GUIDE_ONLY` ist ausschließlich eine Anleitung.

Enthaltene Einträge: Starter-Schutzhaus, großes Haus, Wachturm, Java-Eisenfarm, große Tropfsteinfarm
und manueller Zuckerrohr-Anbau. Suche mit `Eisenfarm`, `Tropfstein`, `Zuckerrohr`, `Haus` oder
`alle`. Die Datenbank darf Anleitungen nicht als getestete Schematics ausgeben.

### Große Tropfsteinfarm

Der erste Farmdatensatz ist `farm.dripstone.megafarm.01` für Java 1.21.11:
ein Ausbaukonzept mit 16 Wachstumszellen in einem 4×4-Raster, getrennten Sammelrinnen,
Wartungszugang und Hinweisen zu Wasserführung, Wachstum und Ernte. Es ist derzeit ein
Technik-/Bauleitfaden, kein getestetes Schematic und keine behauptete vollautomatische Farm.
Die Ernteschaltung muss auf der konkreten Welt/Serverversion separat validiert werden.

**Read-only Baumaterial- und Rezeptplanung:** Das bestätigungspflichtige Tool `plan_build_materials` analysiert die drei festen Bauvorlagen (54, 130 oder 74 Blöcke) anhand der 36 normalen Inventar-/Hotbar-Slots. Neben vorhandenen Full-Block-Materialien zeigt es bis zu fünf herstellbare Vanilla-Blockalternativen aus dem synchronisierten Rezeptbuch. Die Suche ist auf 256 Rezepteinträge und 16 mögliche Blockmaterialien begrenzt. Für jede vollständige Rezeptkette werden Bestand, Fehlmenge, Rezeptschritte, Chargen, 2×2- oder 3×3-Crafting-Bedarf und die konkret ausgewählten Zutaten mit ihrem Gesamtverbrauch je Rezeptstufe angezeigt. Die Rangfolge bevorzugt weniger Chargen und Schritte, danach eine kleinere Fehlmenge und bei ansonsten gleichen Werten Rezepte ohne Crafting-Table. Für nicht vollständig auflösbare Ketten zeigt Nightfall zusätzlich bis zu fünf Diagnosekandidaten mit konkreten fehlenden Zutaten und Mengen sowie der untersuchten Rezeptvariante. Die Diagnose untersucht höchstens sechs Varianten je Rezeptstufe und verwendet insgesamt höchstens 128 rekursive Prüfschritte. Sie folgt damit nur begrenzt vielen Rezeptzweigen und darf nicht als Beweis verstanden werden, dass keine andere Rezeptvariante existiert; sie kennzeichnet die geprüfte Variante ausdrücklich als unvollständig. Die Prüfung ist lesend: Sie sammelt und craftet nichts, verändert keine Slots oder Weltblöcke und legt keinen Bau-Checkpoint an. Die Inventardaten werden erst nach Bestätigung an den konfigurierten KI-Anbieter übermittelt. `craft_item` und der eigentliche Bau besitzen weiterhin jeweils eine eigene Vorschau und Bestätigung.

**Materialversorgung:** Der Builder zählt Materialien aus der gesamten Hotbar und dem 27-Slot-Hauptinventar. Fehlt der benötigte Block in der Hotbar, verschiebt Nightfall den Stack mit einem normalen Inventar-SWAP; vorhandene Items bleiben erhalten. Beim Ausgraben wählt Nightfall ein geeignetes Werkzeug aus dem gesamten Inventar, bevorzugt korrekte Drop-Werkzeuge und stellt die vorherige Hotbar-Belegung nach dem Abbau wieder her, wenn der verdrängte Stack unverändert ist. Für fehlende Baumaterialien kann die KI unterstützte Ressourcen mit eigenen, jeweils bestätigungspflichtigen Sammelschritten beschaffen und danach eine frische Bauvorschau anfordern. `gather_wood` sammelt ausgewählte Vanilla-Stämme/Holzblöcke in geladenen Chunks nahe dem Spieler; `gather_block` unterstützt eine begrenzte Allowlist selbst-droppender Vanilla-Blöcke wie Erde, Bruchstein, ausgewählte Gesteine, Tuff, Sandstein und Nether-/Steinziegelblöcke. Das Ziel im Inventar kann 1–1.024 Stück für Holz/Blöcke und 1–256 Stück für Erze betragen. Langaufträge können bis zu 20 Minuten laufen, maximal 2.048 Zielblöcke abbauen und bis zu 64 sichere Erkundungswege nutzen; sie enden früher bei erreichter Zielmenge, vollem Inventar oder fehlenden sicheren Zielen. Ein optionales `target_y` bei Erz führt nur zu einer bereits sicher begehbaren Fußhöhe in geladenen Chunks. `gather_ore` unterstützt ausgewählte Erz-Drops (Kohle, Roh-Eisen/-Kupfer/-Gold, Redstone, Lapislazuli, Diamant, Smaragd, Quarz, Nethergold-Nuggets und Ancient Debris) im Nahbereich geladener Chunks. Stein wird ohne Silk Touch nicht als Stein gesammelt; Sand, Kies und nicht unterstützte Materialien müssen manuell beschafft werden. `craft_item` plant begrenzte mehrstufige Rezeptketten aus dem freigeschalteten Server-Rezeptbuch, inklusive 2×2- und 3×3-Rezepten. Wenn ein 3×3-Rezept benötigt wird, muss eine Crafting-Table geöffnet sein. Zwischenprodukte werden einzeln hergestellt und serverbestätigt; Grenzen: 32 Rezeptschritte, Tiefe 8, 256 Chargen, fünf Minuten. `smelt_item` kann begrenzte Chargen Roh-Eisen, Roh-Gold, Roh-Kupfer oder Ancient Debris in einem leeren Ofen/Hochofen unter dem Fadenkreuz schmelzen, sofern Kohle/Holzkohle bereits im Inventar liegt. Jede Sammel-, Crafting-, Schmelz-, Sortier- und Bauaktion erfordert eine eigene Spielerbestätigung.
Er kann noch keine Wasserquellen, spitzen Tropfsteine, Dorfbewohner, Hopper, Kolben, Beobachter
oder Redstone-Schaltungen als funktionierende Farmmechanik zuverlässig aufbauen. Farm-Einträge
mit `GUIDE_ONLY` dürfen deshalb nicht automatisch in Blockpläne umgewandelt werden.

## Voice-Call: mit Nightfall sprechen

Nightfall hat einen dauerhaften, ausdrücklich gestarteten Sprach-Call. Trage zuerst deinen API-Key
in `config/nightfall/ai.json` ein. Drücke **F8** oder öffne mit **J** den KI-Chat und klicke auf
**CALL**. Nightfall öffnet das Mikrofon und verbindet sich über die Realtime-WebSocket-Schnittstelle
mit dem unter `audioBaseUrl` konfigurierten Anbieter. Die Sprachaufnahme wird live übertragen;
sie wird nicht als Audiodatei auf der Festplatte gespeichert. Die automatische Spracherkennung
erkennt Gesprächsenden und sendet den erkannten Text in derselben Realtime-Konversation an Nightfall.

Nightfall antwortet mit gestreamter Stimme. Die kleine Anzeige **NIGHTFALL CALL · F8 STOP** bleibt
auch sichtbar, wenn du das Chatfenster schließt und wieder Minecraft spielst. **F8** oder **STOP**
beendet den Call; Trennen vom Server und Schließen von Minecraft beenden ihn ebenfalls. Während
Nightfall spricht, pausiert der Mikrofon-Uplink lokal, damit die Lautsprecherausgabe nicht als neue
Spracheingabe zurückkommt. Deshalb ist diese erste Call-Version halbduplex: Sprich erst weiter,
wenn Nightfall fertig gesprochen hat; Unterbrechen während der Ausgabe ist noch nicht aktiviert.

Wenn Nightfall eine Weltaktion vorschlägt, zeigt es wie im Textchat eine Vorschau an. Du kannst sie
im Chatfenster bestätigen/abbrechen oder im Call klar **„Ja“** bzw. **„Nein“** sagen. Ein mehrdeutiges
Wort löst keine Aktion aus. Der Call verwendet dieselbe freigegebene Werkzeugliste wie Nightfalls
normaler Chat; Weltänderungen werden nicht allein durch das Hören eines Sprachbefehls ausgeführt.

Die Stimmeingabe/-ausgabe wird beim Start des Calls in `ai.json` aktiviert. **TTS ON/OFF** im
Chatfenster schaltet die gesprochene Ausgabe während des Calls um. Der Call verwendet das Realtime-
Modell `gpt-realtime-2.1-mini` mit deutscher Transkription und der eingestellten Stimme
`speechVoice`. Die Verbindung benötigt einen kompatiblen Realtime-WebSocket-Endpunkt; der
Standard ist OpenAI API v1. Andere Anbieter müssen das Realtime-WebSocket-Protokoll und die
konfigurierten Modelle unterstützen.

Mikrofon-Audio, erkannter Text, Gesprächsinhalte und gegebenenfalls freigegebene Werkzeugdaten
werden an den eingestellten KI-Anbieter übertragen. Es gelten dessen Nutzungs- und Abrechnungsregeln;
Realtime-Audio kann zusätzliche API-Kosten verursachen. Aktiviere den Call nur, wenn du mit dieser
Übertragung einverstanden bist. Der API-Key liegt lokal in `config/nightfall/ai.json` und gehört
nicht in Screenshots, Bugreports oder Commits.


## Core companion actions · navigation, combat, mining, wood gathering, crafting and eating

The AI action registry includes the following approved actions:

- `navigate_to`: requests a feet-level target coordinate. Nightfall plans an A* route in 32-block segments using only loaded client chunks and safe full-height support blocks. It avoids fluids, solid collisions and common hazards, replans after each segment as chunks load, eases yaw/pitch toward movement targets instead of snapping instantly, and stops after at most 20 minutes, prolonged lack of progress, or when no safe next segment exists. This is not a guarantee of a route through unloaded terrain, caves, parkour, water or server-specific obstacles.
- `attack_nearest_hostile`: chooses only a nearby vanilla `HostileEntity`, approaches by local navigation and uses normal melee interactions. It never targets players, passive mobs or neutral mobs. It stops after 12 attacks or 45 seconds.
- `mine_looked_block`: mines the block currently under the crosshair. `mine_block` accepts explicit x/y/z coordinates. Both choose the most suitable tool from all 36 player inventory slots, preferring tools that can harvest drops. If the tool is outside the hotbar, Nightfall temporarily moves it with a normal SWAP click and restores the displaced stack when it is unchanged. Both use normal vanilla mining, require reach and direct line of sight, reject unbreakable blocks and block entities/containers, and wait for a world update.
- `gather_wood`: collects toward a requested total inventory count (1–1,024) of a vanilla log/wood/stem/hyphae item such as `minecraft:oak_log`. An optional `duration_seconds` runs for up to 1,200 seconds (20 minutes); when the local scan is empty, Nightfall can take bounded safe A* exploration legs to load new nearby chunks and scan again. A count target ends the task earlier if reached.
- `gather_block`: collects toward a requested total inventory count (1–1,024) for a conservative allowlist of vanilla blocks with verified drops. It includes same-item drops such as dirt, coarse dirt, cobblestone, mossy cobblestone, granite, diorite, andesite, tuff, calcite, dripstone block, moss block, sandstone/red sandstone, netherrack, end stone, blackstone, basalt, smooth basalt, cobbled deepslate and selected brick/terracotta/quartz blocks. It also maps stone or cobblestone to a cobblestone drop, and deepslate or cobbled deepslate to a cobbled-deepslate drop. It does not claim that stone itself drops as `stone`; that requires Silk Touch and is not automated. It scans already-loaded chunks up to 32 horizontal blocks around the player, uses safe adjacent A* stand positions and suitable tools, rejects falling blocks/fluids/block entities/unbreakable targets, waits for block updates and confirmed item pickup, pre-checks available inventory capacity and stops when the inventory is full. A task is limited to 2,048 source blocks, 64 safe exploration legs, and an optional 20-minute duration; the inventory count target ends it earlier. Tool-required blocks are ignored unless an appropriate tool is in the player's inventory; unlisted IDs are rejected. Ores, sand/gravel, glass and other Silk Touch-dependent or differently-dropping blocks are not gathered.
- `gather_ore`: gathers a requested total inventory count (1–256) of supported ore drops, not ore blocks. It scans only already-loaded chunks within 32 horizontal blocks, 24 below and 12 above the player. After local targets are exhausted, it follows a bounded clockwise square-spiral route from the task's starting area, using safe A* legs to load nearby chunks and rescan. Optional `target_y` asks Nightfall to reach a safe walkable player-feet position at that Y before scanning; it does not dig a shaft or guarantee that a route to that exact level exists. Ancient Debris searches require the Nether and a suitable mining tool; short checked horizontal tunnels may be used to reach a buried target, but the routine does not dig downward or through fluids, bedrock or unknown rock. Each task is capped at 2,048 ore-source blocks, 64 successful spiral legs and an optional 20-minute duration; it stops earlier at the count goal, full inventory, lack of safe routes or exhausted loaded targets. This is a systematic local survey, not a scan of the entire world or of unloaded chunks. Only confirmed block and item updates count as success.
- `smelt_item`: smelts 1–16 raw iron, raw gold, raw copper or ancient debris in the empty furnace/blast furnace directly under the crosshair. It requires enough matching input and coal/charcoal already in the player's inventory, computes the fuel amount for the chosen furnace type, inserts only the requested batch, waits for server-confirmed output and takes it one output stack at a time. Non-empty machines are never touched; it stops when the GUI closes, the inventory lacks room or output is not confirmed. Each batch needs confirmation.
- `craft_item`: plans a bounded dependency chain from recipes currently unlocked in the connected server's recipe book. It supports shaped 2×2/3×3 recipes and shapeless recipes with up to nine ingredient slots. If any step needs 3×3, the player must open a crafting table before requesting the action; Nightfall does not place or open one. Intermediate outputs are crafted one recipe step at a time and must appear in the inventory before the next step begins. Limits: 32 recipe steps, dependency depth 8, 256 batches and five minutes. The requested final count is 1–256 additional items. It stops on missing ingredients, full inventory, closed crafting screen, unsupported recipe displays, locked recipes or timeout. Weapon/explosive output categories are deliberately excluded from AI crafting automation.
- `eat_food`: selects the best food item from the hotbar when the hunger bar is not full, consumes it using vanilla item interaction and restores the previous hotbar slot.
- `stop_current_action`: releases Nightfall-controlled movement/use/attack keys and stops active navigation, mining, wood/material gathering, crafting, eating and combat. This safety action does not require confirmation.

World-changing actions show a preview and require the player's explicit confirmation. The Minecraft server remains authoritative: client approval does not bypass server protections, reach checks or permissions.

- `sort_inventory`: sorts ordinary carried stacks in the 36 normal inventory/hotbar slots by category and item ID. It preserves exact stack sizes, durability, enchantments and item components; it does not merge, split or delete stacks. Protected combat-oriented items stay in their original slots; armor, offhand, crafting fields and cursor stay untouched. Each normal pickup swap is checked against the server's inventory update before the next swap.

## First researched knowledge database

The editable database is bundled at `src/main/resources/nightfall/blueprints.json` and copied to `config/nightfall/blueprints.json` the first time it is used. Schema version 1 currently contains 13 entries:

- Supported Nightfall templates: 4x4 starter shelter, 6x6 large house and watchtower.
- Guide-only farm research: Java iron farm, 16-cell dripstone farm, sugar cane, cobblestone generator, crop farmland, villager breeder, hostile mob drops, wool, manual tree farm and pumpkin/melon.

Each record carries a Minecraft version, tags, source URL, validation note and automation status. `SUPPORTED_TEMPLATE` entries link to an existing Nightfall build action; `GUIDE_ONLY` entries are knowledge and must not be converted into invented block layouts. The farm guides cite Minecraft Wiki pages and are not advertised as tested schematics or guaranteed farm designs. Server settings, tick/simulation distance and version-specific mechanics can change the result.

The JSON file can be edited locally without rebuilding Nightfall. Keep IDs unique and use the existing schema. For new sources, record the page URL and distinguish verified mechanics from untested layout suggestions.


## Community schematics and local imports

Nightfall does not scrape schematic sites or bypass download/account restrictions. The bundled
`src/main/resources/nightfall/community_schematics.json` is a source index, not a redistributed
collection of other creators' files. It currently links to the Donut.build listings **Small Fast
Kelp Farm** (TheCommand / credited design BasicRedstone) and **Beginner Bonemeal Farm**
(kekw kekw), plus the Donut Schematics community farm library. The source page's own version,
dimensions, material list and comments are retained as provenance; missing metadata is marked
as unknown and negative user reports are not hidden.

Download the original file using the source site's normal download button and place it in:

`config/nightfall/schematics/`

Supported local formats:

- Sponge Schematic v2 (`.schem`)
- Litematica v5–v7 (`.litematic`; signed/mirrored region axes are normalized)

The importer limits files to 64 MB, compressed NBT tracking to 128 MB, dimensions to 512 blocks per axis, and volume to 8,000,000 cells. Litematica imports accept up to 64 regions; automatically supported blocks are capped at 1,000,000. Large structures are split into 32×32×32 build sections (up to 32,768 block positions per section), with at most 16 section indices per axis. Invalid/truncated palettes, unknown block states and oversized input are rejected. These are parser limits, not a guarantee that every such design is practical to build or that its farm mechanics work.

AI actions:

- `list_schematics`: lists local files and the curated community source index. Read-only.
- `inspect_schematic`: parses one local file and reports dimensions, regions, material counts, exact block-state automation coverage, unsupported block examples, block entities and entities. Read-only; inspect the complete summary before planning a build.
- `repair_schematic`: creates a new local Sponge v2 `.schem` file containing only the block states and still-water sources that Nightfall can import automatically. The original is never modified or overwritten. Unsupported positions become air; entities, custom block-entity NBT and inventories are omitted. The generated file must be inspected again before building. This is a supported-subset export, not a repair of farm logic or a guarantee that a farm works.
- `verify_schematic_section`: read-only comparison of one already anchored section against the loaded client world. Reports checked/unloaded positions, block-type mismatches, blockstate differences, missing default block entities, missing still-water sources and occupied clear cells. It does not inspect custom inventories or entities, simulate fluids/redstone, or prove farm output.
- `build_schematic`: prepares one 32³ section of the automatically supported subset and displays a normal Nightfall placement preview. It requires player confirmation before the first placement; each section requires its own preview and confirmation.

**Aushub und Aufbau:** Bei lokalen Schematics liest Nightfall alle Luftzellen innerhalb des
Vorlagen-Volumens. Vor dem Setzen räumt es dort vorhandenes Gelände ab und entfernt Hindernisse an
Baupositionen. Dafür nutzt es den normalen clientseitigen Abbau, wartet auf das Server-Weltupdate und
versucht, sich über geladene, begehbare Wege sowohl an Aushubziele als auch an weiter entfernte
Platzierungsziele zu bewegen. Der Aushub ist auf 32.768 Blöcke je Abschnitt begrenzt. Block-Entities/Container, nicht unterstützte Flüssigkeiten und unzerstörbare Blöcke werden nicht
angerührt. Stille Wasserquellen der Schematic können nach den festen Blöcken mit einem Wassereimer
im Kreativmodus gesetzt werden, sofern ein Wassereimer im Inventar liegt. Fließendes Wasser wird
nicht direkt gesetzt, sondern muss durch die Vanilla-Flusssimulation entstehen; das ist im Spiel
zu prüfen. Im Survival-Modus bleiben Wasser und Lava manuell. Wenn Nightfall keine sichere Route oder Sichtlinie findet, pausiert es und fordert dich
auf, näher heranzugehen.

**Blockausrichtung:** Die Import-Pipeline übernimmt jetzt die gespeicherten BlockStates statt nur
Material-IDs. Vor jeder Platzierung prüft Nightfall mit Minecrafts eigener BlockItem-Platzierlogik
verschiedene Ansetzflächen und Blickrichtungen. Die Schematic-Koordinaten bleiben in ihrer
ursprünglichen Himmelsrichtung; die Vorschau setzt den Bauplatz vor dem Spieler. Ein Block zählt
erst, wenn der Server exakt den gewünschten Blockzustand bestätigt. Ist die gewünschte Kombination
durch normale Platzierung nicht reproduzierbar, stoppt Nightfall mit einer konkreten Fehlermeldung,
statt eine falsch ausgerichtete Farm als fertig zu melden. Materialien können aus allen 36 Slots des Spielerinventars verwendet werden; benötigte Stacks werden per normalem SWAP-Klick in die Hotbar geholt und bei unverändertem verdrängtem Stack zurückgetauscht.

**Materialmangel und unterstützte Nachbeschaffung:** Wenn ein Material vollständig fehlt, löst Nightfall eine exakte registrierte Block-Item-ID auch ohne vorhandenen Stack auf und meldet alle fehlenden Materialien gesammelt mit benötigter, vorhandener und fehlender Menge. Für unterstützte Vanilla-Holzarten kann die KI `gather_wood` mit der passenden Log-ID verwenden und fehlende Bretter mit `craft_item` über ein bereits freigeschaltetes 2×2-Rezept herstellen. Ist die fehlende Block-Item-ID selbst in der sicheren Selbst-Drop-Liste enthalten, kann `gather_block` sie direkt sammeln. Danach muss `build_schematic` erneut aufgerufen werden, damit Nightfall eine aktuelle Vorschau mit dem aktualisierten Inventar erzeugt. Jeder Sammel-, Crafting- und Bauauftrag benötigt weiterhin eine eigene Spielerbestätigung. Erze, Rohstoffe mit abweichendem Drop, Sand/Kies, Glas und andere nicht freigegebene Blöcke werden nicht automatisch beschafft.

**Redstone-Unterstützung:** Redstone-Staub wird mit dem normalen Redstone-Item über die
Vanilla-Interaktion auf dem Block darunter platziert. Er wird nach den strukturellen Stützblöcken
und den Redstone-Geräten gesetzt, damit seine Verbindungen aus dem bereits vorhandenen Aufbau
entstehen können. Unterstützte BlockItems wie Repeater, Comparator, Observer, Kolben, Hebel,
Knöpfe, Druckplatten, Redstone-Lampen, Dispenser, Dropper und Hopper behalten ihre statischen
BlockState-Eigenschaften (z. B. Ausrichtung, Repeater-Verzögerung und Comparator-Modus).
Minecraft berechnet dynamische Werte wie Draht-Verbindungen, Signalstärke, powered, locked,
lit und extended anhand der tatsächlichen Nachbarschaft und der laufenden Ticks; diese Werte
werden daher nicht als starre Schematic-Vorgabe erzwungen. Das bestätigt weder korrekte
Signalwege noch Timing, Inventarinhalte, Farm-Ausgabe oder vollständige Funktion. Hopper und
andere Block-Entities können weiterhin nur mit frischen Standarddaten platziert werden; ihr
NBT und Inventar werden nicht übernommen.

**Wichtige verbleibende Grenzen:** Entities, Lavaquellen, fallende Blöcke und nicht unterstützte
Block-Entity-Daten werden nicht rekonstruiert. Stille Wasserquellen werden nur im Kreativmodus
versuchsweise mit einem Wassereimer gesetzt; das Ergebnis und die Flusssimulation müssen im Spiel
geprüft werden. Auch korrekt ausgerichtete Redstone-Blöcke können in einer realen Farm abhängig
von Server-Ticks, Nachbarschaft und weiteren nicht übernommenen Daten anders arbeiten.
Der Aushub und das Setzen der Blöcke allein machen eine Farm nicht funktionsfähig.

**Wiederaufnahme nach Neustart oder Verbindungsabbruch:** Der Builder speichert einen lokalen Checkpoint unter
`config/nightfall/builder_resume.json`. Darin stehen Server- und Dimensionskennung, Baupositionen,
Materialien, vollständige BlockStates sowie die bestätigten Bau- und Aushubstände. Die Datei wird,
soweit vom Dateisystem unterstützt, atomar ersetzt. Fortschrittssicherungen erfolgen mindestens alle 25 und bei großen Plänen zusätzlich in
abschnittsabhängigen Intervallen, am Phasenende und beim Disconnect. Wird der Client zwischen zwei Sicherungen
beendet, gleicht Nightfall den gespeicherten Stand mit der tatsächlichen Welt ab und erkennt bereits
gesetzte beziehungsweise entfernte Blöcke, bevor er weiterarbeitet. Server und Dimension müssen
übereinstimmen; `resume_saved_build` verlangt weiterhin eine ausdrückliche Spielerbestätigung.
Manuelles Stoppen/Abbrechen und ein normal abgeschlossener Bau löschen den Checkpoint.
Prüfe die Vorschau und teste Community-Schematics in einer Weltkopie. Keine heruntergeladene
Creator-Datei wird durch Nightfall mitgeliefert.


**Große Farm-Schematics:** Der Import verarbeitet auch negative Litematica-Regionsgrößen und
normalisiert deren Koordinaten. `build_schematic` akzeptiert die optionalen Parameter
`part_x`, `part_y` und `part_z` (jeweils nullbasiert); jeder Abschnitt umfasst maximal
32×32×32 Zellen. Die KI muss bei großen Vorlagen alle benötigten Abschnitte einzeln planen.
Der erste Abschnitt (0/0/0) speichert einen lokalen, an Server/Dimension/Datei gebundenen Farm-Anker
in `config/nightfall/schematic_build_session.json`. Alle weiteren Bauzentren werden aus diesem
Anker berechnet; `inspect_schematic` zeigt zusätzlich eine Zugangsposition auf Höhe des Startankers.
Navigiere mit `navigate_to` zur Zugangsposition (nicht zum eventuell in der Luft liegenden Bauzentrum),
bevor du den Abschnitt vorbereitest, damit seine Ziel-Chunks geladen sind. Baue die Abschnitte in
aufsteigender `part_y`-Reihenfolge (unterste Ebene zuerst), danach über X/Z, damit obere Teile an
bereits bestätigte untere Blöcke anschließen können. Zum bewussten Neusetzen des Startpunkts muss
`config/nightfall/schematic_build_session.json` gelöscht werden. Auch Abschnitte, die ausschließlich Aushubzellen oder Wasserquellen enthalten, können
als eigene Bauaufträge vorbereitet und nach einem Neustart wiederhergestellt werden. Der Builderstatus
zeigt Aushub, feste Blöcke und Wasserquellen getrennt an.

Der Farm-Anker ist an Datei, Server und Dimension gebunden. Beim normalen Fortsetzen wird er nicht
verschoben. Für eine neue Kopie an einem anderen Ort kann `inspect_schematic` oder
`build_schematic` nur beim ersten Abschnitt X0/Y0/Z0 mit `reset_anchor=true` aufgerufen werden.
Alle weiteren Aufrufe für diese Kopie müssen `reset_anchor=false` verwenden.

**Dauerhafte Abschnittsübersicht:** get_schematic_progress liest die lokale Datei
config/nightfall/schematic_build_session.json und zeigt für die aktuelle Datei, den aktuellen Server
und die aktuelle Dimension an, welche 32³-Abschnitte vollständig abgeschlossen sind und welcher
Abschnitt als Nächstes offen ist. Die KI soll diese Übersicht vor der Auswahl und nach jedem fertigen
Teil abfragen. Ein Abschnitt wird erst nach bestätigtem Aushub, Festblock-Placement und der gesamten
Wasserphase als abgeschlossen eingetragen. Pausierte, abgebrochene oder fehlgeschlagene Teilpläne
werden nicht als fertig markiert. Der laufende Bau-Checkpoint bleibt separat in
config/nightfall/builder_resume.json; so kann ein Teilabschnitt nach Client-Neustart oder
Verbindungsabbruch fortgesetzt werden, ohne dass er schon in der Gesamtübersicht als erledigt gilt.
Die Übersicht bestätigt nur den automatisierten Abschnitt, nicht manuelle Restblöcke oder die
tatsächliche Farmfunktion.

Die Abschnitte sind keine Garantie für einen funktionierenden Farmaufbau. Stille Wasserquellen können
im Kreativmodus nach dem Festblock-Aufbau mit einem Wassereimer gesetzt werden; im Survival-Modus
bleiben sie manuell. Fließendes Wasser wird nicht direkt rekonstruiert, sondern muss durch die
Vanilla-Flusssimulation entstehen. Dynamische Redstone-Zustände und Block-Entity-Sonderdaten
bleiben Einschränkungen.


## Wegpunkte mit Nightfall abfragen

Das read-only Werkzeug `list_waypoints` zeigt die lokal gespeicherten Wegpunkte des aktuellen Servers
mit Name, Kategorie, Dimension und Blockkoordinaten an (maximal 30 Einträge pro Antwort).
Die Wegpunkte bleiben lokal im serverbezogenen Dateisatz gespeichert. Beim Abfragen werden Namen,
Dimensionen und Koordinaten an den konfigurierten KI-Anbieter übermittelt; deshalb zeigt Nightfall
vorher eine Vorschau und verlangt eine Bestätigung. Die Abfrage verändert keine Wegpunkte.
Ein angezeigter Wegpunkt startet keine Bewegung: Für eine Navigation muss der Spieler diese
ausdrücklich anfordern; erst dann wird `navigate_to` mit den gespeicherten Koordinaten verwendet.
Nightfall übernimmt Dimension und Koordinaten unverändert und behauptet nicht, dass ein Wegpunkt
in einer anderen Dimension erreichbar ist.

## Gespeicherte Aufgaben fortsetzen oder löschen

Nightfall speichert beim Verbindungsabbruch höchstens einen lokalen Aufgaben-Checkpoint für einen ausdrücklich gestarteten Sammelauftrag oder ein ausdrücklich gesetztes Navigationsziel. Gespeichert werden nur die Absicht und Server-/Dimensionskennung, keine alten Blockziele, Pfade, Inventar-Snapshots oder Zugangsdaten. Der Checkpoint läuft nach 24 Stunden ab und wird bei Fortsetzung neu validiert. `resume_saved_task` zeigt eine Vorschau und startet die Aufgabe erst nach ausdrücklicher Bestätigung. `clear_saved_task` löscht nach separater Vorschau und Bestätigung ausschließlich den passenden Aufgaben-Checkpoint der aktuellen Server-/Dimensionskombination. Checkpoints anderer Welten bleiben erhalten; laufende Aktionen und Baufortschritt bleiben unverändert.

## Nightfall AI: Basissuche und Fake-Base-Werkzeuge

Die KI kann jetzt den vorhandenen BaseFinder direkt bedienen:

- `search_bases` aktiviert den inkrementellen Scanner. Er wertet ausschließlich bereits geladene Client-Chunks aus; er lädt keine entfernten Chunks, teleportiert nicht und verwendet keine Serverbefehle.
- `get_base_findings` liest SUS-, GONE- und DECOY-Markierungen inklusive Chunk-Koordinaten, Score, Hinweisen und Datendeckung. SUS ist eine Heuristik und kein Beweis für eine Spielerbasis.
- `mark_fake_base` markiert den aktuellen Chunk lokal als FakeBase/Decoy. Optionales `learn=true` speichert zusätzlich dessen geladene Block-Entity-Signatur als Fake-Base-Vorlage.
- `list_fake_base_templates` zeigt die lokal gespeicherten Vorlagennamen.

Fake-Base-Markierungen und Vorlagen werden lokal im bestehenden Waypoint-/FakeBase-System gespeichert. Sie verändern keine Weltblöcke. Erkennungsvorlagen können nur ähnliche geladene Block-Entity-Anordnungen erkennen; sie sind kein zuverlässiger Beweis und können Fehlalarme oder übersehene Treffer verursachen. Server, die Daten maskieren oder nicht an den Client senden, bleiben außerhalb der Erkennung.


## Nutzung vorhandener Nightfall-Module durch die KI

`get_module_observations` liest aktuelle Beobachtungen aus bereits aktivierten Modulen aus:
- `PlayerESP`: nahe Spieler, soweit sie dem Client bekannt sind, mit Position und Entfernung
- `EntityESP` / `ItemESP`: passende Entities bzw. gedroppte Items aus dem jeweiligen Modulfilter
- `StorageESP` und andere `BlockEntityEsp`-Module: aktuelle gecachte BlockEntity-Treffer mit Position
- `BaseFinder` / `ChunkESP`: SUS-/GONE-/DECOY-Einstufungen und Gründe
- `Intelligence`: Deep-Scan-Fortschritt, offene Aufträge und unterirdische Entity-Hinweise

Die KI soll je nach Frage das passende Modul abfragen und Ergebnisse nicht erfinden. Ist ein Modul deaktiviert, muss sie das offen sagen und vor dem Aktivieren um Bestätigung bitten. Modulbeobachtungen verwenden nur bereits clientseitig bekannte Entities und geladene Chunks. Sie umgehen keine Serverrechte und können keine verborgenen oder nicht übertragenen Daten sichtbar machen.


## StaffWatch- und AntiVanish-Beobachtungen für Nightfall AI

`get_module_observations` kann zusätzlich die Module `StaffWatch` und
`AntiVanishHints` auslesen. Es werden ausschließlich die vom Spieler selbst in
StaffWatch eingetragenen Namen und die bereits clientseitig erhobenen Tab-/Entity-Stichproben
ausgegeben (maximal zehn Einträge). Dazu gehören aktuelle Sichtbarkeit, Verlaufslücken und
gegebenenfalls der im Tab angezeigte Spielmodus. Die KI darf daraus keinen Vanish-, Staff- oder
Berechtigungsstatus ableiten: fehlende Tab-Einträge und nicht geladene Entities haben viele
mögliche Ursachen. `AntiVanishHints` verwendet dieselben StaffWatch-Stichproben und ist keine
unabhängige Erkennung. Es werden keine fremden Inventare oder nicht übertragene Serverdaten
ausgelesen.


## KI-Performance und Voice-Stabilität

Die KI-Anfragen und die Chat-Oberfläche begrenzen unnötige Arbeit und behalten trotzdem den für Aktionen nötigen Gesprächskontext:

- Der Werkzeug-Katalog wird beim ersten Zugriff aufgebaut und zwischengespeichert. Für jede Anfrage werden nur die tatsächlich ausgewählten Tool-Schemata kopiert; unpassende Definitionen werden weder geklont noch gesendet.
- Die Chat-Anfrage enthält höchstens die letzten sechs vollständigen Benutzerrunden und versucht, den Kontext auf etwa 24.000 Zeichen zu begrenzen. Vollständige neueste Aktionen und ihre Tool-Ergebnisse bleiben zusammen, damit keine ungültigen, verwaisten Tool-Aufrufe entstehen.
- Der interne API-Verlauf wird vor jeder neuen Anfrage ebenfalls direkt gekürzt, damit alte Gesprächsrunden nicht unbegrenzt im Speicher bleiben. Die neueste vollständige Runde wird auch dann behalten, wenn sie allein über dem Richtwert liegt.
- Die Werkzeugauswahl richtet sich nach den letzten drei Benutzernachrichten. Normale Unterhaltung sendet keine Funktionsschemata; Folgefragen behalten den ursprünglichen Aufgabenkontext. Unpassende Suchwerkzeuge werden nicht allein durch ein allgemeines Wort wie „suche“ hinzugefügt.
- Die Oberfläche hält höchstens 80 sichtbare Nachrichten, kürzt sehr lange Anzeigetexte und begrenzt eine einzelne Eingabe auf 8.000 Zeichen. Das Abschneiden der sichtbaren Eingabe wird mit einer binären Suche statt mit wiederholtem Entfernen einzelner Zeichen berechnet.
- Bereits umgebrochene Chat-Zeilen werden für die aktuelle Textbreite zwischengespeichert und nur bei einer Layoutbreitenänderung neu berechnet; der Cache ist ebenfalls begrenzt.
- Der System-Prompt wurde verkürzt, behält aber die Regeln für Tool-Auswahl, Vorschau und Bestätigung, Serverrechte, geladene Client-Daten, Abbruch, Build-Status und ehrliche Grenzen bei.
- Der Realtime-Sprachmodus hält höchstens etwa drei Sekunden Audio in der Wiedergabewarteschlange. Bei Überlast werden ältere Audiopakete verworfen, damit die Ausgabe nicht immer weiter hinterherläuft. Mikrofonpakete werden nicht unbegrenzt aufgestaut, wenn der WebSocket noch mit dem vorherigen Paket beschäftigt ist.
- Realtime-WebSocket-Sendungen sind an die jeweilige Anruf-Sitzung gebunden. Verspätete Pakete einer beendeten Sitzung werden nicht in einen neu gestarteten Anruf übertragen.
- Push-to-talk-TTS bricht eine ältere Syntheseanfrage und laufende Wiedergabe ab, sobald eine neue Antwort gesprochen werden soll. Leere, fehlgeschlagene oder übermäßig große TTS-Antworten werden ignoriert.
- `ai.json` wird vor einer neuen Chat-Anfrage nur einmal eingelesen; doppelte synchrone Dateizugriffe im HTTP-Client und beim Start der TTS-Wiedergabe entfallen.

Die Grenzen für Verlauf und Audio dienen der Reaktionsfähigkeit; bei sehr langen Gesprächen können ältere Chat-Details aus dem KI-Kontext herausfallen. Die sichtbare Unterhaltung wird davon getrennt verwaltet. Diese Änderungen wurden durch den CI-Build geprüft, sind aber nicht als Minecraft-Laufzeittest oder Mikrofon-/Lautsprechertest zu verstehen.
