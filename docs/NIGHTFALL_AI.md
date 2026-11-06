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

When `shareWorldContext` is enabled, each request includes the current dimension, block position, and main-hand item. Chat text and enabled context are sent to the configured provider.

## Available tools

The AI model can select from a fixed client-side allow-list:

| Tool | What it does | Confirmation |
|---|---|---|
| `locate_biome` | Sends the standard `/locate biome <id>` command for a supported biome | Required |
| `add_waypoint` | Saves a waypoint at the current player position | Required |
| `set_module_state` | Enables or disables one existing Nightfall module by exact name | Required |
| `get_inventory` | Reads item names, exact Minecraft item IDs, and counts; separates the 9-slot hotbar from the remaining inventory | Required; inventory data is sent to the AI provider |
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
- Autonomous combat targeting and obstacle-aware navigation remain out of scope. The current movement tool only walks forward for a bounded number of ticks. Building places approved blocks automatically, but the player must position themselves, stay within reach, and confirm the plan before placements begin. Complex farms requiring entities, fluids, containers or redstone are still guide-only.
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

**Materialversorgung:** Der Builder zählt Materialien aus der gesamten Hotbar und dem 27-Slot-Hauptinventar. Fehlt der benötigte Block in der Hotbar, verschiebt Nightfall den Stack mit einem normalen Inventar-SWAP; vorhandene Items bleiben erhalten. Beim Ausgraben wählt Nightfall ein geeignetes Werkzeug aus dem gesamten Inventar, bevorzugt korrekte Drop-Werkzeuge und stellt die vorherige Hotbar-Belegung nach dem Abbau wieder her, wenn der verdrängte Stack unverändert ist. Für fehlende Baumaterialien kann die KI unterstützte Ressourcen mit eigenen, jeweils bestätigungspflichtigen Sammelschritten beschaffen und danach eine frische Bauvorschau anfordern. `gather_wood` sammelt ausgewählte Vanilla-Stämme/Holzblöcke in geladenen Chunks nahe dem Spieler; `gather_block` unterstützt eine begrenzte Allowlist selbst-droppender Vanilla-Blöcke wie Erde, Bruchstein, ausgewählte Gesteine, Tuff, Sandstein und Nether-/Steinziegelblöcke. Das Ziel im Inventar kann 1–256 Stück betragen; pro bestätigtem Auftrag werden höchstens 32 zusätzliche Blöcke abgebaut. `gather_ore` unterstützt ausgewählte Erz-Drops (Kohle, Roh-Eisen/-Kupfer/-Gold, Redstone, Lapislazuli, Diamant, Smaragd, Quarz, Nethergold-Nuggets und Ancient Debris) im Nahbereich geladener Chunks. Stein wird ohne Silk Touch nicht als Stein gesammelt; Sand, Kies und nicht unterstützte Materialien müssen manuell beschafft werden. `craft_item` nutzt freigeschaltete Server-Rezepte für das 2×2-Inventar-Crafting und vorhandene Zutaten; 3×3-Rezepte und mehrstufige Rezeptketten werden nicht automatisiert. `smelt_item` kann begrenzte Chargen Roh-Eisen, Roh-Gold, Roh-Kupfer oder Ancient Debris in einem leeren Ofen/Hochofen unter dem Fadenkreuz schmelzen, sofern Kohle/Holzkohle bereits im Inventar liegt. Jede Sammel-, Crafting-, Schmelz- und Bauaktion erfordert eine eigene Spielerbestätigung.
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

- `navigate_to`: requests a feet-level target coordinate. Nightfall plans an A* route in 32-block segments using only loaded client chunks and safe full-height support blocks. It avoids fluids, solid collisions and common hazards, replans after each segment as chunks load, and stops after five minutes, prolonged lack of progress, or when no safe next segment exists. This is not a guarantee of a route through unloaded terrain, caves, parkour, water or server-specific obstacles.
- `attack_nearest_hostile`: chooses only a nearby vanilla `HostileEntity`, approaches by local navigation and uses normal melee interactions. It never targets players, passive mobs or neutral mobs. It stops after 12 attacks or 45 seconds.
- `mine_looked_block`: mines the block currently under the crosshair. `mine_block` accepts explicit x/y/z coordinates. Both choose the most suitable tool from all 36 player inventory slots, preferring tools that can harvest drops. If the tool is outside the hotbar, Nightfall temporarily moves it with a normal SWAP click and restores the displaced stack when it is unchanged. Both use normal vanilla mining, require reach and direct line of sight, reject unbreakable blocks and block entities/containers, and wait for a world update.
- `gather_wood`: collects toward a requested total inventory count of a vanilla log/wood/stem/hyphae item such as `minecraft:oak_log`.
- `gather_block`: collects toward a requested total inventory count for a conservative allowlist of vanilla blocks with verified drops. It includes same-item drops such as dirt, coarse dirt, cobblestone, mossy cobblestone, granite, diorite, andesite, tuff, calcite, dripstone block, moss block, sandstone/red sandstone, netherrack, end stone, blackstone, basalt, smooth basalt, cobbled deepslate and selected brick/terracotta/quartz blocks. It also maps stone or cobblestone to a cobblestone drop, and deepslate or cobbled deepslate to a cobbled-deepslate drop. It does not claim that stone itself drops as `stone`; that requires Silk Touch and is not automated. All gathering actions scan only already-loaded chunks, use safe adjacent A* stand positions and suitable tools, reject falling blocks/fluids/block entities/unbreakable targets, wait for block updates and confirmed item pickup, pre-check available inventory capacity and stop when the inventory is full, and mine at most 32 additional source blocks per confirmed request. Tool-required blocks are ignored unless an appropriate tool is in the player's inventory; unlisted IDs are rejected. Ores, sand/gravel, glass and other Silk Touch-dependent or differently-dropping blocks are not gathered.
- `gather_ore`: gathers a requested total inventory count (1–64) of supported ore drops, not ore blocks: coal, raw iron, raw copper, raw gold, redstone, lapis lazuli, diamond, emerald, quartz, gold nuggets from nether gold ore, and ancient debris. It recognizes stone/deepslate ore variants and scans only already-loaded chunks within 16 horizontal blocks, 24 below and 12 above the player. For Ancient Debris, when the current target queue is exhausted, Nightfall refreshes the local scan up to six times around the player's updated position and skips targets already attempted; each refresh remains limited to loaded chunks and the same bounded radius, so this is not an exhaustive vein search. In the Nether, Nightfall can dig a short horizontal 1×2 tunnel (up to 8 steps and 16 tunnel blocks) through netherrack, basalt or blackstone to reach a buried ore block. Each cell must have a safe full-block floor; it rechecks tunnel blocks and adjacent cells while digging and refuses fluids/lava, falling blocks, block entities, hazards, unsupported blocks and unloaded cells. It never mines downward or through bedrock/unknown rock and does not use TNT, beds or other explosives. Mining eligibility is checked with Minecraft's `ItemStack.isSuitableFor`, which enforces correct drops; Ancient Debris requires a diamond or netherite pickaxe. Other ores remain direct-visible/reachable only. It waits for world updates and confirmed inventory drops, and limits each request to at most 32 ore blocks.
- `smelt_item`: smelts 1–16 raw iron, raw gold, raw copper or ancient debris in the empty furnace/blast furnace directly under the crosshair. It requires enough matching input and coal/charcoal already in the player's inventory, computes the fuel amount for the chosen furnace type, inserts only the requested batch, waits for server-confirmed output and takes it one output stack at a time. Non-empty machines are never touched; it stops when the GUI closes, the inventory lacks room or output is not confirmed. Each batch needs confirmation.
- `craft_item`: searches the recipe book synchronized by the current server and checks ingredients in the entire player inventory. It supports matching 2×2 shaped recipes and shapeless recipes with at most four ingredients. An optional `count` requests 1–256 additional items; Nightfall takes each output batch individually, stores it in an available inventory slot, and refills the recipe through the normal recipe-book interaction. It stops at the requested amount, when ingredients or inventory space run out, or on timeout, and reports the amount actually crafted. The final recipe batch can exceed the requested amount by at most one batch. 3×3/table recipes and locked recipes are rejected.
- `eat_food`: selects the best food item from the hotbar when the hunger bar is not full, consumes it using vanilla item interaction and restores the previous hotbar slot.
- `stop_current_action`: releases Nightfall-controlled movement/use/attack keys and stops active navigation, mining, wood/material gathering, crafting, eating and combat. This safety action does not require confirmation.

World-changing actions show a preview and require the player's explicit confirmation. The Minecraft server remains authoritative: client approval does not bypass server protections, reach checks or permissions.

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

The importer limits files to 32 MB, dimensions to 256 blocks per axis, and volume to 2,000,000
cells. Large structures are split into 32×32×32 build sections (up to 32,768 supported block
positions per section). Invalid/truncated palettes, unknown block states and oversized input
are rejected.

AI actions:

- `list_schematics`: lists local files and the curated community source index. Read-only.
- `inspect_schematic`: parses one local file and reports dimensions, materials, block counts,
  unsupported block examples, Block Entities and Entities. Read-only.
- `build_schematic`: prepares the automatically supported subset and displays a normal Nightfall
  placement preview. It requires player confirmation before the first placement.

**Aushub und Aufbau:** Bei lokalen Schematics liest Nightfall alle Luftzellen innerhalb des
Vorlagen-Volumens. Vor dem Setzen räumt es dort vorhandenes Gelände ab und entfernt Hindernisse an
Baupositionen. Dafür nutzt es den normalen clientseitigen Abbau, wartet auf das Server-Weltupdate und
versucht, sich über geladene, begehbare Wege sowohl an Aushubziele als auch an weiter entfernte
Platzierungsziele zu bewegen. Der Aushub ist auf 32.768 Blöcke je Abschnitt begrenzt. Block-Entities/Container, Flüssigkeiten und unzerstörbare Blöcke werden nicht
angerührt. Wenn Nightfall keine sichere Route oder Sichtlinie findet, pausiert es und fordert dich
auf, näher heranzugehen.

**Blockausrichtung:** Die Import-Pipeline übernimmt jetzt die gespeicherten BlockStates statt nur
Material-IDs. Vor jeder Platzierung prüft Nightfall mit Minecrafts eigener BlockItem-Platzierlogik
verschiedene Ansetzflächen und Blickrichtungen. Die Schematic-Koordinaten bleiben in ihrer
ursprünglichen Himmelsrichtung; die Vorschau setzt den Bauplatz vor dem Spieler. Ein Block zählt
erst, wenn der Server exakt den gewünschten Blockzustand bestätigt. Ist die gewünschte Kombination
durch normale Platzierung nicht reproduzierbar, stoppt Nightfall mit einer konkreten Fehlermeldung,
statt eine falsch ausgerichtete Farm als fertig zu melden. Materialien können aus allen 36 Slots des Spielerinventars verwendet werden; benötigte Stacks werden per normalem SWAP-Klick in die Hotbar geholt und bei unverändertem verdrängtem Stack zurückgetauscht.

**Materialmangel und unterstützte Nachbeschaffung:** Wenn ein Material vollständig fehlt, löst Nightfall eine exakte registrierte Block-Item-ID auch ohne vorhandenen Stack auf und meldet alle fehlenden Materialien gesammelt mit benötigter, vorhandener und fehlender Menge. Für unterstützte Vanilla-Holzarten kann die KI `gather_wood` mit der passenden Log-ID verwenden und fehlende Bretter mit `craft_item` über ein bereits freigeschaltetes 2×2-Rezept herstellen. Ist die fehlende Block-Item-ID selbst in der sicheren Selbst-Drop-Liste enthalten, kann `gather_block` sie direkt sammeln. Danach muss `build_schematic` erneut aufgerufen werden, damit Nightfall eine aktuelle Vorschau mit dem aktualisierten Inventar erzeugt. Jeder Sammel-, Crafting- und Bauauftrag benötigt weiterhin eine eigene Spielerbestätigung. Erze, Rohstoffe mit abweichendem Drop, Sand/Kies, Glas und andere nicht freigegebene Blöcke werden nicht automatisch beschafft.

**Wichtige verbleibende Grenzen:** Block-Entity-Blöcke wie Hopper und Truhen können mit frischen
Standarddaten platziert werden. Inventarinhalte und benutzerdefiniertes Block-Entity-NBT werden
aber nicht aus der Vorlage übernommen. Entities, Flüssigkeitsquellen, fallende Blöcke und komplexe,
dynamische Redstone-/Nachbarschaftszustände werden noch nicht vollständig rekonstruiert. Insbesondere kann eine Schematic, deren Block-States von Nachbarblöcken
oder Redstone-Signalen abhängen, trotz korrekter Grundausrichtung noch Abweichungen enthalten.
Der Aushub selbst macht eine Farm nicht funktionsfähig, wenn ihre Mechanik nicht unterstützt wird.

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
in `config/nightfall/schematic_build_session.json`. Alle weiteren Abschnittszentren werden aus diesem
Anker berechnet; `inspect_schematic` zeigt die exakten Weltkoordinaten. Navigiere mit `navigate_to`
zum angezeigten Zentrum, bevor du den Abschnitt vorbereitest, damit seine Ziel-Chunks geladen sind. Die Abschnitte sind keine Garantie für einen
funktionierenden Farmaufbau. Flüssigkeiten (auch Wasserquellen), dynamische Redstone-Zustände
und Block-Entity-Sonderdaten bleiben Einschränkungen; Wasser muss derzeit manuell ergänzt werden.
