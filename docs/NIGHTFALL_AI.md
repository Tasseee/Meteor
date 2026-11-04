# Nightfall AI – Phase 1

Nightfall AI adds an in-game chat companion. The first phase is **chat-only**: it can answer Minecraft questions, but it cannot place blocks, attack entities, run commands, or otherwise change the world.

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
- No in-game action tools are connected yet. The assistant is explicitly instructed not to claim that it has changed the world.
- A future action system should expose narrow, permission-checked tools (for example, read inventory, preview a build plan, then request approval before placing blocks). It must not execute arbitrary model-generated code.
