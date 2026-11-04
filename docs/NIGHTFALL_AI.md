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
  "shareWorldContext": true
}
```

The key is stored locally as plain text in the Minecraft config folder. Do not upload or share this file, include it in a bug report, or commit it to a repository. Nightfall does not log the key.

## In-game use

- Press **J** to open Nightfall AI (default; configurable as `aiKey` in `config/nightfall.json`).
- Press **Enter** or click **SEND** to send a message.
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
| `stop_build` | Stops the active builder queue; already placed blocks remain | Required |

After a tool call, Nightfall displays a preview. The tool is executed only after the player clicks **AKTION AUSFÜHREN**. Cancelling returns a cancellation result to the model and does not execute the action.

## Safety and current limitations

- Tool calls are not arbitrary code. Nightfall never exposes shell execution or arbitrary Java.
- The client validates the tool name and arguments against a fixed allow-list. The model cannot invent a new action.
- The ordinary command tool accepts a single command with no command chaining. It blocks server administration roots (for example `op`, `ban`, `stop`, `whitelist`, common permission-management aliases) and high-impact world-edit roots/aliases (for example `execute`, `fill`, `clone`, `setblock`, `worldedit`, `we`, `fawe`, `//set`, `//paste`, `//undo`). Every command is shown exactly as it will be sent and needs confirmation. The server still decides whether the user has permission.
- Server permissions remain authoritative. The biome search command can fail on servers that do not permit `/locate`; Nightfall does not bypass permissions or teleport the player.
- For ordinary commands, Nightfall watches game/system chat and player chat for up to 30 seconds. It can surface a likely related response or explicit permission/unknown-command rejection, but generic plugin messages cannot always be correlated reliably. A sent command is never reported as successfully executed without a clear server response.
- Tool-capable OpenAI-compatible models are required. Some endpoints may reject function calling.
- A short follow-up tool loop is supported, with one tool call at a time and a bounded number of consecutive actions.
- The conversation is kept in memory for the current chat screen; it is not persisted.
- The builder offers three deterministic templates: a 4x4 starter shelter (54 blocks), a 6x6 house with three-block-high walls (130 blocks), and a 5x5 watchtower with four six-block-high corner pillars (74 blocks). For other user-requested structures, `build_custom_structure` accepts an AI-generated plan of up to 200 blocks in a 16x16x11 grid.
- The builder chooses full-cube block items already in the hotbar, or honors exact material item IDs/localized names supplied globally or for individual custom-plan blocks. It verifies the required count for each material and all safe target positions, then sends genuine block-placement interactions one at a time. Ambiguous localized material names are rejected instead of selecting an arbitrary matching item. Custom plans reject duplicates, out-of-range coordinates, occupied targets, and disconnected/floating components; every component must anchor to an existing solid block. It waits for the corresponding world block state before counting a placement, retries up to two times after a timeout, and stops safely when the server rejects a placement. If a block is out of reach, the queue pauses until the player moves closer. The originally selected hotbar slot is restored when the build finishes or stops. The custom-plan confirmation also renders a top-down ASCII footprint for the y=0 layer, counts blocks per vertical layer, and lists the material-symbol legend; the chat renderer preserves these line breaks.
- Combat targeting, autonomous navigation, voice input/output, and arbitrary code execution remain out of scope. More structures and other reviewed skills can be added to the same registry.
- When the inventory tool is approved, its result is sent back to the configured AI provider. It includes exact item IDs and distinguishes the hotbar from the rest of the inventory because the current builder places only materials already in the 9-slot hotbar. Do not approve it if you do not want to share that inventory snapshot.

Future skills should stay narrowly scoped, validated, previewed, and explicitly confirmed before world changes or data disclosure.
