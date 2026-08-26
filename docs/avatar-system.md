# 3D virtual character and model-designed styles

AgentOS renders its system identity as a native procedural 3D character. The home
voice surface and character studio share one `AgentAvatar` profile and one OpenGL ES
renderer; this is not a WebView, prerecorded video, or flat sticker.

## Runtime

- native `GLSurfaceView`/OpenGL ES 2.0 pipeline with depth testing and lit materials;
- reusable UV-sphere mesh, transformed into the head, body, eyes, hair, clothing,
  accessories, and expression geometry;
- orbital inspection camera: drag to rotate and pinch to zoom;
- visibility-bound 30 fps rendering for natural motion without an unrestricted loop;
- four material responses: matte, gloss, metal, and hologram;
- soft, anime, cyber, fantasy, and semi-realistic style families;
- minimal, suit, armor, robe, and streetwear silhouettes;
- visor, headset, halo, horn, or no accessory;
- eight runtime expressions linked to listening, thinking, speaking, and attention;
- layered breathing, body sway, blink, gaze, TTS mouth motion, nod, wave, point,
  celebrate, comfort, and explanation gestures.

The remote planner may select one emotion and gesture plus bounded intensity, tempo,
and gaze values in `generated-ui.schema.json`. Unknown motion names, extra fields,
non-finite values, and out-of-range parameters are rejected. Device state always
wins: listening, thinking, interruption, and TTS playback override a conflicting
model direction, while the native idle layer keeps every accepted pose from looking
mechanically frozen.

The editor also exposes face shape, hair, eye style, skin/hair/outfit colors, face
width, eye size and spacing, mouth width, head scale, body height, shoulder width,
and glow. The avatar profile remains in AgentShell's private local storage.

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
