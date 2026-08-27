in vec3 vColor;
in float vStar;
out vec4 fragColor;

void main() {
    if (vStar > 0.5) {
        vec2 q = gl_PointCoord * 2.0 - 1.0;
        float core = exp(-dot(q, q) * 6.0);
        float star = exp(-abs(q.x) * 9.0) * exp(-abs(q.y) * 2.6)
            + exp(-abs(q.y) * 9.0) * exp(-abs(q.x) * 2.6);
        float energy = core * 1.15 + star * 0.75;
        fragColor = vec4(vColor * energy, 1.0);
    } else {
        vec2 q = gl_PointCoord * 2.0 - 1.0;
        float r2 = dot(q, q);
        if (r2 > 1.0) discard;
        fragColor = vec4(vColor * (exp(-r2 * 3.2) - 0.04), 1.0);
    }
}
