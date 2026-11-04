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
| `get_inventory` | Reads item names and counts from the player's 36 main inventory slots | Required; inventory data is sent to the AI provider |

After a tool call, Nightfall displays a preview. The tool is executed only after the player clicks **AKTION AUSFÜHREN**. Cancelling returns a cancellation result to the model and does not execute the action.

## Safety and current limitations

- Tool calls are not arbitrary code. Nightfall never exposes shell execution, arbitrary Java, or a generic server-command tool.
- The client validates the tool name and arguments against a fixed allow-list. The model cannot invent a new action.
- Server permissions remain authoritative. The biome search command can fail on servers that do not permit `/locate`; Nightfall does not bypass permissions or teleport the player.
- Tool-capable OpenAI-compatible models are required. Some endpoints may reject function calling.
- A short follow-up tool loop is supported, with one tool call at a time and a bounded number of consecutive actions.
- The conversation is kept in memory for the current chat screen; it is not persisted.
- This phase does not yet build structures, attack entities, navigate autonomously, use voice, or run arbitrary commands. Those capabilities can be added as separate, reviewed skills using the same interface.
- When the inventory tool is approved, its result is sent back to the configured AI provider. Do not approve it if you do not want to share that inventory snapshot.

Future skills should stay narrowly scoped, validated, previewed, and explicitly confirmed before world changes or data disclosure.
