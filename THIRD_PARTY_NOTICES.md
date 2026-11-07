# Third-party notices

## LyricSync / MusicShow

Project: https://github.com/ShuangJinl/MusicShow

The project declares the Minecraft Fabric client as MIT-licensed in
`lyricsync-fabric-client/src/main/resources/fabric.mod.json`. Its 1.21.11
TextDisplayEntity approach was used as a compatibility reference for Nightfall's
own floating-text integration.

Nightfall does not bundle LyricSync source files or its Python middleware. The
Nightfall Java integration and local playback sidecar in this repository are
independent implementations.


## Local language model

Model: Qwen2.5-1.5B-Instruct, GGUF Q4_K_M

Source: https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF

The model file is downloaded by the player after clicking **MODELL LADEN**. It is not bundled in the Nightfall mod JAR. The upstream model is published under the Apache License 2.0. Nightfall pins the model revision and verifies the expected file size and SHA-256 before loading.

## Local speech recognition

Library: Vosk Java API 0.3.45

Source: https://github.com/alphacep/vosk-api

License: Apache License 2.0.

Model: `vosk-model-small-de-0.15`

Source: https://alphacephei.com/vosk/models

License: Apache License 2.0. The model archive is downloaded on first microphone activation, SHA-256 verified, and extracted into the user's local Nightfall configuration directory.

Dependency: JNA 5.7.0

Source: https://github.com/java-native-access/jna

License: dual-licensed under LGPL-2.1-or-later or Apache License 2.0. JNA is included as a nested runtime dependency for Vosk.

## Local speech output

No third-party speech model or cloud service is bundled. Nightfall invokes the operating-system speech API or locally installed `say`, `espeak-ng`, or `espeak` program. The user remains responsible for the license terms of any separately installed voice or executable.
