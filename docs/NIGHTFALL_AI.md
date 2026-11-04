# Nightfall AI – Extensible Skills and Actions

Nightfall AI is an in-game Minecraft companion with an extensible, allow-listed tool system. The AI model may choose from the tools exposed by the client, but **the client validates every argument and controls every execution**.

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
- Press **F8** or click **MIC** to start push-to-talk recording; press F8 again to stop. Recordings are limited to 20 seconds.
- The recognized text is inserted into the input field. Review it and press **Enter** to send it; voice recognition never sends a message by itself.
- Enable `voiceOutputEnabled` to speak final assistant replies. Closing the chat or disconnecting stops active recording/playback.
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
| `build_custom_structure` | Builds an AI-generated connected plan of up to 200 blocks in a bounded 16x16x11 grid; supports an optional per-block material palette | Required; validates connectivity, per-material inventory counts, height, and replaceable targets |
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
- The builder offers three deterministic templates: a 4x4 starter shelter (54 blocks), a 6x6 house with three-block-high walls (130 blocks), and a 5x5 watchtower with four six-block-high corner pillars (74 blocks). For other user-requested structures, `build_custom_structure` accepts an AI-generated plan of up to 200 blocks in a 16x16x11 grid.
- The read-only `get_build_status` tool lets the player ask for confirmed progress, current building phase, pause reason and active materials without approving another action.
- The builder chooses full-cube block items already in the hotbar, or honors exact material item IDs/localized names supplied globally or for individual custom-plan blocks. In Survival it checks the full required count per material; in Creative it only requires one matching hotbar stack because placements do not consume blocks. Ambiguous partial names are rejected consistently for both preset and custom builds. It verifies the required count for each material and all safe target positions, then sends genuine block-placement interactions one at a time. Ambiguous localized material names are rejected instead of selecting an arbitrary matching item. Custom plans reject duplicates, out-of-range coordinates, occupied targets, and disconnected/floating components; every component must anchor to an existing solid block. It waits for the corresponding world block state before counting a placement, retries up to two times after a timeout, and stops safely when the server rejects a placement. If a block is out of reach, the queue pauses until the player moves closer. The originally selected hotbar slot is restored when the build finishes or stops. Every builder confirmation shows a top-down ASCII footprint for the y=0 layer, counts blocks per vertical layer, and lists the material-symbol legend; the chat renderer preserves these line breaks.
- Autonomous combat targeting and obstacle-aware navigation remain out of scope. The current movement tool only walks forward for a bounded number of ticks. Building places approved blocks automatically, but the player must position themselves, stay within reach, and confirm the plan before placements begin. Complex farms requiring entities, fluids, containers or redstone are still guide-only.
- Voice input/output is available as an opt-in feature (see below). Arbitrary code execution remains out of scope.
- When the inventory tool is approved, its result is sent back to the configured AI provider. It includes exact item IDs and distinguishes the hotbar from the rest of the inventory because the current builder places only materials already in the 9-slot hotbar. Do not approve it if you do not want to share that inventory snapshot.

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

**Wichtige Grenze:** Der derzeitige Builder platziert nur Full-Cube-BlockItems aus der Hotbar.
Er kann noch keine Wasserquellen, spitzen Tropfsteine, Dorfbewohner, Hopper, Kolben, Beobachter
oder Redstone-Schaltungen als funktionierende Farmmechanik zuverlässig aufbauen. Farm-Einträge
mit `GUIDE_ONLY` dürfen deshalb nicht automatisch in Blockpläne umgewandelt werden.

## Voice: Push-to-talk und gesprochene Antworten

Sprache ist standardmäßig deaktiviert. Aktiviere `voiceInputEnabled` und/oder
`voiceOutputEnabled` in `config/nightfall/ai.json`. Das Mikrofon wird nur nach F8 oder einem
Klick auf MIC geöffnet. F8 beendet die Aufnahme; maximal 20 Sekunden werden erfasst. Nightfall
speichert keine Audiodatei dauerhaft. Die Aufnahme wird als WAV an `audioBaseUrl` unter
`/audio/transcriptions` übertragen. Erkannter Text wird in das Eingabefeld eingefügt und muss
vom Spieler geprüft und mit Enter gesendet werden. Der Chat spricht finale KI-Antworten über
`/audio/speech`, wenn `voiceOutputEnabled` aktiv ist.

Die Sprachschnittstelle verwendet `transcriptionModel`, `speechModel` und `speechVoice`.
Der Standard-Endpunkt ist OpenAI API v1. Andere Anbieter müssen beide Audio-Endpunkte und die
gewählten Modelle unterstützen. Audio und erkannter Text werden an diesen Anbieter übertragen;
aktiviere Sprache nur, wenn du damit einverstanden bist. Schließen des Chats und Trennen der
Serververbindung stoppen laufende Aufnahme/Wiedergabe.


## Core companion actions · navigation, combat, mining and eating

The AI action registry includes the following approved actions:

- `navigate_to`: requests a feet-level target coordinate. Nightfall plans an A* route in 32-block segments using only loaded client chunks and safe full-height support blocks. It avoids fluids, solid collisions and common hazards, replans after each segment as chunks load, and stops after five minutes, prolonged lack of progress, or when no safe next segment exists. This is not a guarantee of a route through unloaded terrain, caves, parkour, water or server-specific obstacles.
- `attack_nearest_hostile`: chooses only a nearby vanilla `HostileEntity`, approaches by local navigation and uses normal melee interactions. It never targets players, passive mobs or neutral mobs. It stops after 12 attacks or 45 seconds.
- `mine_looked_block`: mines the block currently under the crosshair. `mine_block` accepts explicit x/y/z coordinates. Both use the currently held tool, require reach and direct line of sight, reject unbreakable blocks and block entities/containers, and wait for a world update.
- `eat_food`: selects the best food item from the hotbar when the hunger bar is not full, consumes it using vanilla item interaction and restores the previous hotbar slot.
- `stop_current_action`: releases Nightfall-controlled movement/use/attack keys and stops active navigation, mining, eating and combat. This safety action does not require confirmation.

World-changing actions show a preview and require the player's explicit confirmation. The Minecraft server remains authoritative: client approval does not bypass server protections, reach checks or permissions.

## First researched knowledge database

The editable database is bundled at `src/main/resources/nightfall/blueprints.json` and copied to `config/nightfall/blueprints.json` the first time it is used. Schema version 1 currently contains 13 entries:

- Supported Nightfall templates: 4x4 starter shelter, 6x6 large house and watchtower.
- Guide-only farm research: Java iron farm, 16-cell dripstone farm, sugar cane, cobblestone generator, crop farmland, villager breeder, hostile mob drops, wool, manual tree farm and pumpkin/melon.

Each record carries a Minecraft version, tags, source URL, validation note and automation status. `SUPPORTED_TEMPLATE` entries link to an existing Nightfall build action; `GUIDE_ONLY` entries are knowledge and must not be converted into invented block layouts. The farm guides cite Minecraft Wiki pages and are not advertised as tested schematics or guaranteed farm designs. Server settings, tick/simulation distance and version-specific mechanics can change the result.

The JSON file can be edited locally without rebuilding Nightfall. Keep IDs unique and use the existing schema. For new sources, record the page URL and distinguish verified mechanics from untested layout suggestions.
