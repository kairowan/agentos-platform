# AgentOS UI visual baseline

This directory contains generated high-fidelity product mockups aligned with the
native Kotlin/Compose UI. They are not screenshots from an emulator or physical
device. Their purpose is to keep implementation, product review, and documentation
focused on one visible target while the full AOSP image is being built.

## Screens

- `home-v4.png`: current voice-first home with the native 3D avatar;
- `character-studio-v4.png`: OpenGL viewport, model prompt, 3D style and material controls;
- `home-v3.png` and `character-studio-v3.png`: previous 2D character baseline;
- `home-v2.png`: previous orb-based home retained as the v2 historical reference;
- `app-bridge-v2.png`: installed-app capability providers and confirmation boundary;
- `camera-v2.png`: native Camera2 photo/video surface and privacy indicators;
- `knowledge-v2.png`: complete, editable, pan-and-zoom semantic knowledge graph.

## Generation mode and prompt contract

All images were generated with the built-in image generator in `ui-mockup` mode.
The shared prompt requires a straight-on 9:20 Android screen, no hardware frame,
native Material 3 components, background `#061014`, panels `#122026`/`#1A2D34`,
trusted mint `#68F5CE`, information blue `#84AFFF`, confirmation amber `#FFC66D`,
off-white Chinese text, large touch targets, no WebView chrome, and no watermark.

Screen-specific prompts require:

- Home: wake-word hero, camera/gallery/apps/memory actions, collapsed local model
  connection, result card, fixed natural-language composer, and editable avatar.
- Character Studio: native 3D avatar viewport, orbit gestures, expression previews,
  face/body shaping, hair/outfit/accessory/material controls, a model style prompt,
  randomize, reset, and save.
- App Bridge: provider/semantic tabs, app/package search, domain chips, provider
  capabilities, and explicit confirmation before opening or sensitive actions.
- Camera: native Camera2 preview, tap-to-focus, zoom, photo/video selection,
  shutter/gallery/flip controls, device-local storage, and persistent privacy state.
- Knowledge: full history/entity/relation counts, search and filters, a dense graph
  centered on the user, labeled relationships, zoom controls, editable long-term
  facts, and recent memory.

## Update contract

When any tracked UI source changes, update the affected mockup (or replace it with
an emulator screenshot), review the four-screen set for consistency, then refresh
`ui-preview.sha256`. `tests/check.sh` rejects a stale or corrupted visual baseline.

Actual emulator/device screenshots should use the same filenames in a new versioned
directory, with this README updated to state the device, build target, density, and
capture date.
