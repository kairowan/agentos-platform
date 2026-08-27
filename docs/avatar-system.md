# 3D virtual character and model-designed styles

AgentOS renders its system identity as a procedural 3D character. The home voice
surface and character studio share one `AgentAvatar` profile and one bounded render
protocol. The default `SYSTEM` identity uses a bundled offline JavaScript/WebGL
runtime inside an isolated WebView; native Compose remains responsible for the page,
voice, permissions, and trusted controls. Other avatar families and WebGL failures
use the retained native OpenGL ES renderer. Neither path uses prerecorded video or a
flat sticker.

## Original visual identity

The default `SYSTEM` family is the AgentOS Thought Field. It is intentionally
genderless, non-human, and not a robot mascot: overlapping deformable black-glass
lobes form a living consciousness knot around one warm amber core. A deterministic
volume shader generates dense moving filaments, two depths of particles, and a
memory constellation behind two procedurally generated, deforming glass surfaces.
There is no permanent head, torso, clothing, or pair of limbs. Eyes, a voice mark,
and one flowing gesture limb condense only when communication needs them, then
dissolve back into the field. The voice mark follows TTS rhythm while gaze, color,
field speed, and deformation carry listening, thinking, concern, and delight.

Human, anime, fantasy, and semi-realistic families are optional user-created
identities, never the product's default or brand anchor.

## Runtime

- offline JavaScript/WebGL 1 pipeline for `SYSTEM`, with a full-screen volume pass,
  two indexed glass-surface layers, a depth pre-pass, and a temporary joint rig;
- native `GLSurfaceView`/OpenGL ES 2.0 fallback plus the existing lit mesh pass for
  user-created character families;
- one reusable 64×28 parameter mesh whose vertex shader produces the asymmetric
  head, neck, shoulders, torso, waist, and open tail at runtime; drag rotation now
  changes real surface depth, normals, Fresnel highlights, and occlusion;
- reusable UV-sphere mesh, transformed into the head, body, eyes, hair, clothing,
  accessories, expression geometry, and the temporary system hand's arm, palm, and
  finger joints;
- no image texture, generated portrait, downloaded model, or per-frame CPU geometry
  is used by the default identity: black glass, sixteen internal filaments, moving
  motes, constellation links, core bloom, optical face, layered surface refraction,
  and temporary gesture geometry are evaluated from bundled shaders every frame;
- orbital inspection camera: drag to rotate and pinch to zoom;
- visibility-bound animation with device pixel ratio capped at 1.5; hidden WebViews
  stop requesting frames, while the native fallback retains its 30 fps ceiling;
- four material responses: matte, gloss, metal, and hologram;
- soft, anime, cyber, fantasy, and semi-realistic style families;
- minimal, suit, armor, robe, and streetwear silhouettes;
- visor, headset, halo, horn, or no accessory;
- eight runtime expressions linked to listening, thinking, speaking, and attention;
- layered breathing, body sway, blink, gaze, TTS voice-mark motion, nod, wave, point,
  celebrate, comfort, and explanation gestures. The `SYSTEM` family expresses
  gestures through temporary flowing geometry instead of a fixed skeletal arm.

The remote planner may select one emotion and gesture plus bounded intensity, tempo,
and gaze values in `generated-ui.schema.json`. Unknown motion names, extra fields,
non-finite values, and out-of-range parameters are rejected. Device state always
wins: listening, thinking, interruption, and TTS playback override a conflicting
model direction, while the bounded idle layer keeps every accepted pose from looking
mechanically frozen.

## Native-to-renderer middleware

`AvatarRenderCommand` is the only production input to the JavaScript renderer. It
projects native state into protocol version 1: numeric mood and gesture identifiers,
bounded intensity/tempo/gaze, speaking and face presence, and normalized shaping
values. Non-finite values receive safe defaults before JSON serialization. The JSON
is quoted as data and parsed by the page; model text never becomes executable
JavaScript.

The WebView loads only `https://avatar.agentos.local/`, an in-process virtual origin
served from the APK. File/content access, network loads, mixed content, popups,
multiple windows, and external navigation are disabled. The page has a restrictive
Content Security Policy and receives no `addJavascriptInterface`, Binder object,
Capability Broker, microphone, camera, storage, notification, or credential access.
Renderer readiness and failure are reported through the page title; a missing
System WebView, load error, SSL error, render-process loss, shader failure, or WebGL
context loss returns the character to native GLES without affecting the Compose UI.

The boundary is intentionally engine-neutral. `runtime.js` currently uses direct
WebGL to avoid a new dependency and keep the APK offline; it can later be replaced by
a bundled Three.js renderer without changing voice, Agent, permission, or Compose
code as long as protocol version 1 remains supported.

The editor adapts to the selected family. `SYSTEM` exposes consciousness-knot width,
optical expression, light spacing, voice-mark width, core scale, constellation
height/width, and glow. Human-derived families expose face, hair, clothing, material,
and accessory controls. The avatar profile remains in AgentShell's private storage.

## Large-model style generation

When the user enables an OpenAI-compatible model in AgentOS, the character studio
accepts requests such as “赛博仙侠风，银发、全息面罩、轻型机甲”. The request and
current avatar parameters are sent to that configured endpoint. Photos, biometric
data, conversation history, credentials, and installed-app data are not included.

The model must return `avatar-style.schema.json` version 1. The parser rejects:

- missing or unknown fields;
- values outside declared enums;
- geometry values outside 0..1;
- URLs, shader/source code, scripts, and downloaded asset references;
- unsupported schema versions.

Only a validated `AgentAvatar` draft reaches the renderer. The user can inspect,
rotate, edit, save, or discard it before it becomes the active system character.

## Asset-generation boundary

The current implementation gives the model control over the entire supported
procedural style, not arbitrary executable geometry. Truly free-form generated hair,
clothing meshes, textures, skeletal rigs, and lip-sync blendshapes require a separate
signed asset pipeline: generate glTF/GLB, scan and bound it off-device, remove scripts
and external URIs, verify triangle/texture/bone budgets, sign the bundle, preview it,
then import only after confirmation. That pipeline is intentionally not simulated by
accepting unsafe model output directly inside a privileged system app.

## Reproducible visual verification

`scripts/thought-field-preview.html` loads the exact bundled `runtime.js`, which then
compiles the production volume, surface, part, and shared glass shaders through
WebGL 1. It builds the same 64×28 parameter surface and 24×16 joint sphere used by
the Android WebView. Run `scripts/capture-thought-field-preview.sh` to render the
fixed speaking/waving frame stored as
`docs/images/ui-v2/thought-field-runtime-v1.png`. That PNG is test evidence, not a
runtime input. Runtime or shader changes must regenerate it; AI-generated images
cannot prove a visual feature exists in code. Final validation still needs a
representative Android System WebView and GPU because providers, drivers, precision,
and sustained performance can differ.
