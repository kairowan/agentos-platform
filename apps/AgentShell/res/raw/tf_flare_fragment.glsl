// The amber core, its anamorphic star flare, and the optical eyes are drawn
// additively into the HDR buffer as a screen-space pass. All positions arrive
// pre-projected in pixels; distances are normalized by the capture height so
// the flare reads the same at any resolution.

uniform vec2 uCorePx;
uniform vec2 uEyePx;
uniform float uEyeGapPx;
uniform float uEyeSigmaPx;
uniform float uBlink;
uniform float uFacePresence;
uniform float uFlareGain;
uniform float uSpeaking;
uniform float uFlareScale;
uniform vec3 uAccent;
out vec4 fragColor;

void main() {
    vec2 px = gl_FragCoord.xy;
    float s = uFlareScale;

    vec2 coreDelta = (px - uCorePx) / s;
    coreDelta.y *= 1.12;
    float coreRadius = length(coreDelta);
    float core = exp(-coreRadius * 0.030) * 0.42
        + exp(-coreRadius * 0.115) * 2.6
        + exp(-coreRadius * 0.46) * 3.8;
    float starH = exp(-abs(coreDelta.y) * 0.14) * exp(-abs(coreDelta.x) * 0.0092) * 0.72;
    float starV = exp(-abs(coreDelta.x) * 0.14) * exp(-abs(coreDelta.y) * 0.0115) * 0.30;
    float diagonal = exp(-abs(dot(coreDelta, vec2(0.7071, -0.7071))) * 0.020)
        * exp(-abs(dot(coreDelta, vec2(0.7071, 0.7071))) * 0.16) * 0.10;
    vec3 coreColor = mix(uAccent, vec3(1.0, 0.95, 0.82), 0.55);
    vec3 sum = coreColor * core + vec3(1.0, 0.96, 0.86) * (starH + starV + diagonal);
    float pulse = 1.0 + 0.22 * uSpeaking * (0.5 + 0.5 * sin(uTime * 9.0));
    sum *= uFlareGain * pulse;

    float blink = max(uBlink, 0.055);
    vec2 eyeDelta = (px - uEyePx) / s;
    float gap = uEyeGapPx / s;
    float sigma = uEyeSigmaPx / s;
    float left = length((eyeDelta - vec2(-gap, 0.0)) / vec2(1.0, blink));
    float right = length((eyeDelta - vec2(gap, 0.0)) / vec2(1.0, blink));
    float eyes = exp(-left * left / (sigma * sigma)) + exp(-right * right / (sigma * sigma));
    float rings = exp(-abs(left - sigma * 1.55) * 0.9) + exp(-abs(right - sigma * 1.55) * 0.9);
    sum += vec3(1.0, 0.95, 0.84) * (eyes * 1.15 + rings * 0.12) * uFacePresence;

    fragColor = vec4(sum, 1.0);
}
