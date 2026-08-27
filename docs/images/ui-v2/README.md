# AgentOS UI visual baseline

This directory contains the two approved 3D design targets and three non-avatar
layout references. Design references are not screenshots from the current code,
an emulator, or a physical device. None of these PNGs is used as an app texture.

The two selected concepts were supplied by the maintainer and are preserved without
resizing or repainting. They are the README showcase and the intended visual
direction; the current renderer does not yet achieve their appearance. All public
3D illustrations must reuse these two originals until the maintainer approves a
replacement. Old home/studio mockups and renderer captures have been moved to local
backups under ignored `artifacts/`, not retained as public image alternatives.

## Screens

- `thought-field-home-concept.png`: selected full-screen companion design target;
  **设计效果图，非当前代码运行截图**;
- `thought-field-studio-concept.png`: selected 3D Character Studio design target;
  **设计效果图，非当前代码运行截图**;
- `app-bridge-v2.png`: installed-app capability providers and confirmation boundary;
- `camera-v2.png`: native Camera2 photo/video surface and privacy indicators;
- `knowledge-v2.png`: complete, editable, pan-and-zoom semantic knowledge graph.

## Local-only runtime shader capture

The separate runtime capture is not made with an image generator. From the repository root:

```bash
scripts/capture-thought-field-preview.sh
```

The default output is `artifacts/renderer-check/thought-field.png`, which is ignored
by Git. The capture page loads the bundled `runtime.js` and production shaders from
`apps/AgentShell/res/raw`, using a fixed preview state. The app never reads the
resulting PNG. Keep this capture and emulator screenshots/recordings local; do not
attach them to GitHub documentation, issues, pull requests, releases or Actions
artifacts without new approval. The CI preview uploads written results, not media.

## Layout-mockup generation mode and prompt contract

The retained non-avatar layout mockups were generated with the built-in image
generator. The following historical prompt contract documents those references;
it does not authorize replacing the two approved 3D designs.
The shared prompt requires a straight-on 9:20 Android screen, no hardware frame,
native Material 3 components, background `#061014`, panels `#122026`/`#1A2D34`,
trusted mint `#68F5CE`, information blue `#84AFFF`, confirmation amber `#FFC66D`,
off-white Chinese text, large touch targets, no WebView chrome, and no watermark.

Screen-specific prompts require:

- App Bridge: provider/semantic tabs, app/package search, domain chips, provider
  capabilities, and explicit confirmation before opening or sensitive actions.
- Camera: native Camera2 preview, tap-to-focus, zoom, photo/video selection,
  shutter/gallery/flip controls, device-local storage, and persistent privacy state.
- Knowledge: full history/entity/relation counts, search and filters, a dense graph
  centered on the user, labeled relationships, zoom controls, editable long-term
  facts, and recent memory.

## Update contract

When shaders or Compose layouts change, validate the result locally and report the
device/build/density and remaining limits in text. Do not copy runtime frames into
this directory or overwrite a concept image with a screenshot. Keep the original
design files unchanged and labeled as concepts, not proof of rendering fidelity.

`tests/check.sh` verifies `ui-preview.sha256`. `tests/check_preview_tools.py` checks
the public image allowlist, documentation image references and the preview workflow's
upload allowlist. A new public visual needs explicit approval and an updated policy.
