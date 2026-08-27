in vec3 vColor;
out vec4 fragColor;

void main() {
    vec2 point = gl_PointCoord * 2.0 - 1.0;
    float r2 = dot(point, point);
    if (r2 > 1.0) discard;
    float falloff = exp(-r2 * 4.5) - 0.011;
    fragColor = vec4(vColor * max(falloff, 0.0), 1.0);
}
