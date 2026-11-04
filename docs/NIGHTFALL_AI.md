# Nightfall AI – Phase 2

Nightfall AI adds an in-game chat companion. It can answer Minecraft questions and now supports one explicitly confirmed first world action: searching for a biome through the server's normal `/locate biome` command.

## Setup

1. Start Nightfall once. It creates `config/nightfall/ai.json`.
2. Open that file and set `apiKey` to an API key from your chosen provider.
3. Keep `baseUrl` and `model` aligned with that provider. The defaults target an OpenAI-compatible Chat Completions endpoint.
4. Restart Minecraft or reopen the chat and send a message.

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

When `shareWorldContext` is enabled, each request includes the current dimension, block position, and main-hand item. The chat UI shows whether this context is enabled. Chat messages and enabled context are sent to the configured provider.

## Current limits

- This phase requires a configured, reachable AI provider.
- The conversation is kept in memory for the current chat screen; it is not persisted.
- The first world action is a local, deterministic intent: requests such as “such mir einen Jungle” offer a confirmation button for `/locate biome minecraft:jungle` (and a small supported set of other biomes). The command is sent only after the user clicks **JA, BIOM SUCHEN**.
- Server permissions remain authoritative. If the server rejects `/locate`, the command cannot make it work. Nightfall does not bypass permissions or teleport the player.
- The command response is shown in the chat if a matching server feedback message is received. Some servers may hide or reformat command feedback, in which case Nightfall times out without inventing a result.
- This is not yet general AI tool-calling, building, combat, or arbitrary command execution. Future actions should stay narrowly scoped, previewed, permission-checked, and explicitly confirmed; arbitrary model-generated code must never be executed.
