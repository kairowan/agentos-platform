# 3D virtual character and model-designed styles

AgentOS renders its system identity as a native procedural 3D character. The home
voice surface and character studio share one `AgentAvatar` profile and one OpenGL ES
renderer; this is not a WebView, prerecorded video, or flat sticker.

## Original visual identity

The default `SYSTEM` family is the AgentOS Thought Field. It is intentionally
genderless, non-human, and not a robot mascot: overlapping deformable black-glass
lobes form a living consciousness knot around one warm amber core. A deterministic
particle stream and constellation links visualize thought and long-term memory.
There is no permanent head, torso, clothing, or pair of limbs. Eyes, a voice mark,
and one flowing gesture limb condense only when communication needs them, then
dissolve back into the field. The voice mark follows TTS rhythm while gaze, color,
field speed, and deformation carry listening, thinking, concern, and delight.

Human, anime, fantasy, and semi-realistic families are optional user-created
identities, never the product's default or brand anchor.

## Runtime

- native `GLSurfaceView`/OpenGL ES 2.0 pipeline with depth testing, alpha blending,
  lit materials, vertex deformation, point sprites, and constellation lines;
- reusable UV-sphere mesh, transformed into the head, body, eyes, hair, clothing,
  accessories, and expression geometry;
- fixed reusable buffers for 144 moving thought particles and 36 memory links, with
  no per-frame mesh allocation and only two extra field draw calls;
- orbital inspection camera: drag to rotate and pinch to zoom;
- visibility-bound 30 fps rendering for natural motion without an unrestricted loop;
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
model direction, while the native idle layer keeps every accepted pose from looking
mechanically frozen.

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
