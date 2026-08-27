// Final composite: HDR scene + bloom, warm ambient halo, vignette, filmic
// exposure, gamma lift, and blue-noise-ish grain to keep dark gradients clean.

uniform sampler2D uScene;
uniform sampler2D uBloom;
uniform float uBloomStrength;
uniform float uExposure;
uniform vec2 uResolution;
in vec2 vUv;
out vec4 fragColor;

void main() {
    vec3 color = texture(uScene, vUv).rgb + texture(uBloom, vUv).rgb * uBloomStrength;
    vec2 centered = (vUv - 0.5) * vec2(uResolution.x / max(uResolution.y, 1.0), 1.0);
    color += vec3(0.050, 0.034, 0.020) * exp(-dot(centered, centered) * 2.6) * 0.40;
    float vignette = 1.0 - smoothstep(0.55, 1.52, length(centered) * 1.38);
    color *= 0.60 + 0.40 * vignette;
    color = vec3(1.0) - exp(-color * uExposure);
    color = pow(max(color, vec3(0.0)), vec3(0.90));
    vec2 grainSeed = gl_FragCoord.xy + vec2(fract(uTime * 13.71) * 91.0, fract(uTime * 7.31) * 57.0);
    color += (hash21(grainSeed) - 0.5) * 0.007;
    fragColor = vec4(max(color, vec3(0.0)), 1.0);
}
