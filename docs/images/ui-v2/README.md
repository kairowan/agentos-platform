# AgentOS UI visual baseline

This directory contains one renderer-derived character capture plus generated
high-fidelity layout mockups. The mockups are not screenshots from an emulator or
physical device. The shader capture is generated from executable renderer code and
is never used as an app texture.

## Screens

- `thought-field-runtime-v1.png`: deterministic output from the production GLES/WebGL
  fragment shader at the fixed speaking/waving preview state; this is the only current
  character image generated directly by executable renderer code;
- `home-v7.png`: full-screen Compose layout mockup containing the Thought Field concept;
- `home-v6.png`: previous split-seed native-form home;
- `home-v4.png`: previous card-based 3D avatar home;
- `character-studio-v6.png`: Character Studio layout mockup with thought-field controls;
- `character-studio-v5.png`: previous split-seed native-form studio;
- `character-studio-v4.png`: previous human character studio baseline;
- `home-v3.png` and `character-studio-v3.png`: previous 2D character baseline;
- `home-v2.png`: previous orb-based home retained as the v2 historical reference;
- `app-bridge-v2.png`: installed-app capability providers and confirmation boundary;
- `camera-v2.png`: native Camera2 photo/video surface and privacy indicators;
- `knowledge-v2.png`: complete, editable, pan-and-zoom semantic knowledge graph.

## Runtime shader capture

The character baseline is not made with an image generator. From the repository root:

```bash
scripts/capture-thought-field-preview.sh
```

The capture page loads `apps/AgentShell/res/raw/thought_field_fragment.glsl`, which is
the exact fragment source compiled by AgentShell. It supplies a fixed resolution,
time, speaking expression, wave gesture, gaze, and normalized shaping values, then
captures WebGL 1 output. The app never reads the resulting PNG.

## Layout-mockup generation mode and prompt contract

Layout mockups were generated with the built-in image generator. New screens use
`ui-mockup`; versioned updates that preserve an existing layout use
`precise-object-edit`.
The shared prompt requires a straight-on 9:20 Android screen, no hardware frame,
native Material 3 components, background `#061014`, panels `#122026`/`#1A2D34`,
trusted mint `#68F5CE`, information blue `#84AFFF`, confirmation amber `#FFC66D`,
off-white Chinese text, large touch targets, no WebView chrome, and no watermark.

Screen-specific prompts require:

- Home: one full-screen speaking 3D character, natural gesture, tiny status/settings
  controls, a transient subtitle, one voice pill, and a collapsed keyboard fallback;
  no permanent capability cards or result dashboard. The default character is an
  asymmetric deforming black-glass consciousness knot with a warm amber core,
  flowing particles, memory-constellation links, and only transient facial light;
  it must not read as a human, anime mascot, seed robot, or another assistant brand.
- Character Studio: native 3D avatar viewport, orbit gestures, expression previews,
  thought-field shaping for the `SYSTEM` family, human-family controls when relevant,
  a model style prompt, randomize, reset, and save.
- App Bridge: provider/semantic tabs, app/package search, domain chips, provider
  capabilities, and explicit confirmation before opening or sensitive actions.
- Camera: native Camera2 preview, tap-to-focus, zoom, photo/video selection,
  shutter/gallery/flip controls, device-local storage, and persistent privacy state.
- Knowledge: full history/entity/relation counts, search and filters, a dense graph
  centered on the user, labeled relationships, zoom controls, editable long-term
  facts, and recent memory.

## Update contract

When the thought-field shader changes, regenerate its runtime capture. When Compose
layout changes, update the affected mockup or replace it with an emulator screenshot.
Then review the current screen set and refresh `ui-preview.sha256`; `tests/check.sh`
rejects stale or corrupted tracked sources and images.

Actual emulator/device screenshots should use the same filenames in a new versioned
directory, with this README updated to state the device, build target, density, and
capture date.
