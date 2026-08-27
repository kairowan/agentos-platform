// Silk ribbon shading: gaussian cross-section with a bright satin core line,
// so overlapping ribbons read as glowing fabric rather than wireframe.

in vec3 vColor;
in float vSide;
out vec4 fragColor;

void main() {
    float side2 = vSide * vSide;
    float body = exp(-side2 * 3.2) - 0.075;
    float core = exp(-side2 * 22.0) * 1.45;
    fragColor = vec4(vColor * max(body, 0.0) + vColor * core, 1.0);
}
