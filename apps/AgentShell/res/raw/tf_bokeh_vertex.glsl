// Out-of-focus bokeh discs: screen-aligned quads with big soft radii placed in
// a background shell and a sparse foreground shell. Gives the depth-of-field
// feel the flat particle field could not.

uniform mat4 uViewProjection;
uniform float uPixelScale;
uniform vec2 uNdcScale;
uniform float uFlowSpeed;
uniform float uIntensity;
out vec3 vColor;
out vec2 vQuad;

const vec2 CORNERS[6] = vec2[6](
    vec2(-1.0, -1.0), vec2(1.0, -1.0), vec2(-1.0, 1.0),
    vec2(-1.0, 1.0), vec2(1.0, -1.0), vec2(1.0, 1.0)
);

void main() {
    int vid = gl_VertexID;
    int qid = vid / 6;
    int cid = vid - qid * 6;
    float f = float(qid);
    float h1 = hash11(f * 0.731 + 1.7);
    float h2 = hash11(f * 0.523 + 9.2);
    float h3 = hash11(f * 0.917 + 3.7);
    float h4 = hash11(f * 0.379 + 6.1);
    float h5 = hash11(f * 0.661 + 4.8);
    float h6 = hash11(f * 0.293 + 7.9);
    float h7 = hash11(f * 0.847 + 2.3);
    float h8 = hash11(f * 0.449 + 8.4);

    bool foreground = h1 > 0.86;
    vec3 center;
    float radius;
    float alpha;
    if (foreground) {
        center = vec3((h2 - 0.5) * 3.2, (h3 - 0.5) * 4.2, 0.85 + 0.65 * h4);
        radius = 0.024 + 0.060 * pow(h5, 2.0);
        alpha = mix(0.030, 0.075, h6);
    } else {
        center = vec3((h2 - 0.5) * 3.6, (h3 - 0.5) * 4.6, -(0.75 + 2.05 * h4));
        radius = 0.045 + 0.150 * pow(h5, 2.2);
        float depthFade = clamp(1.7 / (0.75 - center.z), 0.35, 1.15);
        alpha = mix(0.022, 0.085, h6) * depthFade;
    }
    center.x += sin(uTime * 0.05 + h7 * 9.0) * 0.06;
    center.y += sin(uTime * 0.04 * uFlowSpeed + h8 * 7.0) * 0.07;
    float twinkle = 0.75 + 0.25 * sin(uTime * mix(0.15, 0.5, h6) + h7 * TAU);

    vec3 color = h8 < 0.14
        ? vec3(0.72, 0.82, 0.98)
        : mix(vec3(1.0, 0.55, 0.20), vec3(1.0, 0.90, 0.68), h2 * 0.8);
    vColor = color * alpha * twinkle * (0.20 + 0.32 * uIntensity);
    vQuad = CORNERS[cid];

    vec4 clip = uViewProjection * vec4(center, 1.0);
    vec2 offsetPx = CORNERS[cid] * radius * uPixelScale / max(clip.w, 0.30);
    gl_Position = vec4(clip.xy + offsetPx * uNdcScale, clip.zw);
}
