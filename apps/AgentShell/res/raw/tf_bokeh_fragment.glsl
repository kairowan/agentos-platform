in vec3 vColor;
in vec2 vQuad;
out vec4 fragColor;

void main() {
    float r = length(vQuad);
    if (r > 1.0) discard;
    float falloff = smoothstep(1.0, 0.0, r);
    falloff *= falloff * falloff;
    fragColor = vec4(vColor * falloff, 1.0);
}
