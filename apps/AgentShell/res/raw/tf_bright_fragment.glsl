// Soft-knee bright extraction feeding the bloom pyramid.

uniform sampler2D uTex;
uniform float uThreshold;
in vec2 vUv;
out vec4 fragColor;

void main() {
    vec3 color = texture(uTex, vUv).rgb;
    float brightness = max(color.r, max(color.g, color.b));
    float knee = 0.55;
    float soft = clamp(brightness - uThreshold + knee, 0.0, 2.0 * knee);
    soft = soft * soft / (4.0 * knee);
    float contribution = max(soft, brightness - uThreshold) / max(brightness, 1e-4);
    fragColor = vec4(color * contribution, 1.0);
}
