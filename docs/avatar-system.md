# Virtual character and expression system

AgentOS uses a native, code-rendered 2D character as the visible identity of the
system agent. It is not a WebView, a downloaded sticker, or a remote video stream.
The same `AgentAvatar` profile renders on the home voice surface and inside the
character studio.

## Current implementation

- four face shapes, six hair styles, four eye styles;
- four skin tones, five hair colors, and five outfit colors;
- continuous face width, eye size, eye spacing, and mouth width controls;
- neutral, happy, listening, thinking, surprised, concerned, speaking, and sleepy
  expressions;
- random presets, reset, editable name, preview, save, and cancel flow;
- automatic home expression selection from voice/runtime state;
- device-local persistence through private `SharedPreferences`.

The character studio opens by tapping the avatar or “角色” on the voice card. The
same path is available by voice with “创建角色”, “编辑角色”, “打开角色工作室”, or
“捏人”. Unsaved edits remain local to the editor and are discarded when leaving.

## Privacy and trust boundary

The current editor does not request the camera, import a face, upload biometric
data, or contact a model. Appearance parameters stay in AgentShell's private app
storage. A future photo-to-avatar feature must use an explicit capture/import step,
show whether processing is local or remote, obtain separate consent before upload,
and make the original image deletable.

## Deliberate boundary

This is a functional 2D character system, not yet a 3D mesh editor. It does not yet
include body-proportion rigging, clothing asset packs, lip-sync phonemes, skeletal
animation, or a user-authored asset marketplace. Those features require a versioned
avatar asset format and GPU/runtime budget work; they should extend this profile
instead of bypassing its local state and privacy rules.
