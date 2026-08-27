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
lobes form a living consciousness knot around one warm amber core. A GPU particle
pipeline generates flowing silk strands bound to the body surface, an
attribute-less spark field with body dust, ambient motes, and bright fireflies,
real-projected memory constellation links and star hubs, and out-of-focus bokeh
discs behind two procedurally generated, deforming glass surfaces. There is no
permanent head, torso, clothing, or pair of limbs. Eyes, a voice mark, and one
flowing gesture limb condense only when communication needs them, then dissolve
back into the field. The voice mark follows TTS rhythm while gaze, color, field
speed, and deformation carry listening, thinking, concern, and delight.

Human, anime, fantasy, and semi-realistic families are optional user-created
identities, never the product's default or brand anchor.

## Runtime

- offline JavaScript/WebGL 2 particle pipeline for `SYSTEM`: an HDR scene target
  (RGBA16F when float-renderable extensions exist, RGBA8 fallback), additive
  line/point/quad geometry, and a bloom chain (soft-knee bright extract, five-level
  dual-Kawase blur, screen composite with exposure, vignette, and grain);
- geometry programs: silk-strand lines bound to the shared body spline
  (`tf_strand_vertex.glsl`), an attribute-less spark field driven by `gl_VertexID`
  (`tf_spark_vertex.glsl`), a particle hand flowing along the arm/finger béziers
  (`tf_hand_vertex.glsl`), 3D constellation links/star nodes, and screen-aligned
  bokeh quads for depth of field;
- the amber core and optical eyes are a screen-space flare pass anchored to
  CPU-projected body points, so they keep their anchor while the field deforms;
- shared spline math lives in `tf_shared.glsl` and is concatenated after the
  `#version`/precision prefix by `runtime.js`; the ported glass-shell pair
  (`tf_glass_vertex/fragment.glsl`) keeps the native GLES silhouette byte-for-byte;
- native `GLSurfaceView`/OpenGL ES 2.0 fallback plus the existing lit mesh pass for
  user-created character families (unchanged single-pass shaders);
- one reusable 64×28 parameter mesh whose vertex shader produces the asymmetric
  head, neck, shoulders, torso, waist, and open tail at runtime; drag rotation now
  changes real surface depth, normals, Fresnel highlights, and occlusion;
- no image texture, generated portrait, downloaded model, or per-frame CPU geometry
  is used by the default identity: strands, sparks, constellation, bokeh, core
  flare, optical face, layered surface refraction, and temporary gesture geometry
  are evaluated from bundled shaders every frame;
- smoothed state: mood accents, hand pose, intensity, speaking, glow, and gaze are
  exponentially blended so render commands glide instead of popping;
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
compiles the production particle-pipeline shaders (`tf_*.glsl`) through WebGL 2. It
builds the same 64×28 parameter surface used by the Android WebView. Run
`scripts/capture-thought-field-preview.sh` to render the fixed speaking/waving frame
stored locally as `artifacts/renderer-check/thought-field.png`. That PNG is test
evidence, not a runtime input or a public showcase asset. Runtime or shader changes
should regenerate it locally; do not publish it to GitHub. Public 3D illustrations
use only the two maintainer-selected design concepts, labeled as design targets.
AI-generated images cannot prove a visual feature exists in code. Final validation
still needs a representative Android System WebView and GPU because providers,
drivers, precision, and sustained performance can differ.
