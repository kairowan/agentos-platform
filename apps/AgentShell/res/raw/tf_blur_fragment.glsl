// Dual-Kawase filter. uDown selects the 5-tap downsample; otherwise the
// 8-tap upsample runs. uTexel is the texel size of the SOURCE texture.

uniform sampler2D uTex;
uniform vec2 uTexel;
uniform float uDown;
in vec2 vUv;
out vec4 fragColor;

void main() {
    vec3 color;
    if (uDown > 0.5) {
        color = texture(uTex, vUv).rgb * 4.0;
        color += texture(uTex, vUv + uTexel * vec2(1.0, 1.0)).rgb;
        color += texture(uTex, vUv - uTexel * vec2(1.0, 1.0)).rgb;
        color += texture(uTex, vUv + uTexel * vec2(1.0, -1.0)).rgb;
        color += texture(uTex, vUv - uTexel * vec2(1.0, -1.0)).rgb;
        color /= 8.0;
    } else {
        color = texture(uTex, vUv + uTexel * vec2(-2.0, 0.0)).rgb
            + texture(uTex, vUv + uTexel * vec2(-1.0, 1.0)).rgb
            + texture(uTex, vUv + uTexel * vec2(0.0, 2.0)).rgb
            + texture(uTex, vUv + uTexel * vec2(1.0, 1.0)).rgb
            + texture(uTex, vUv + uTexel * vec2(2.0, 0.0)).rgb
            + texture(uTex, vUv + uTexel * vec2(1.0, -1.0)).rgb
            + texture(uTex, vUv + uTexel * vec2(0.0, -2.0)).rgb
            + texture(uTex, vUv + uTexel * vec2(-1.0, -1.0)).rgb;
        color /= 8.0;
    }
    fragColor = vec4(color, 1.0);
}
