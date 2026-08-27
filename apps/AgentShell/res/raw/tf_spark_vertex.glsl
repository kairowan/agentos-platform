// Attribute-less GPU spark field. gl_VertexID seeds three populations:
// body dust bound near the body surface, ambient drifting motes, and rare
// bright fireflies that spike into the bloom pass.

uniform mat4 uViewProjection;
uniform vec3 uCameraPosition;
uniform float uFlowSpeed;
uniform float uIntensity;
uniform float uTurbulence;
uniform float uHeightScale;
uniform vec3 uAccent;
uniform vec3 uAccentWarm;
uniform float uPixelScale;
out vec3 vColor;

void main() {
    float id = float(gl_VertexID);
    float group = hash11(id * 0.0113 + 0.7);
    float h1 = hash11(id * 0.0523 + 1.3);
    float h2 = hash11(id * 0.0917 + 2.1);
    float h3 = hash11(id * 0.0379 + 3.3);
    float h4 = hash11(id * 0.0761 + 4.7);
    float h5 = hash11(id * 0.0127 + 5.9);
    float h6 = hash11(id * 0.0619 + 6.2);
    float h7 = hash11(id * 0.0283 + 7.4);
    float h8 = hash11(id * 0.0853 + 8.6);
    float h9 = hash11(id * 0.0441 + 9.8);
    float h10 = hash11(id * 0.0673 + 0.5);
    float h11 = hash11(id * 0.0191 + 1.9);
    float h12 = hash11(id * 0.0931 + 3.1);
    float h13 = hash11(id * 0.0337 + 4.3);

    vec3 pos;
    float alpha;
    float size;
    vec3 color;

    if (group < 0.84) {
        float u = fract(h1 + uTime * mix(0.010, 0.055, h2) * uFlowSpeed);
        float y = mix(1.66, -1.86, u) * uHeightScale;
        float theta = h3 * TAU + uTime * mix(-0.25, 0.25, h4)
            + sin(u * TAU * mix(0.6, 1.8, h5) + uTime * 0.3) * 0.8;
        float radial = mix(0.60, 1.44, h6);
        pos = bodyPoint(y, theta, radial, 0.62);
        pos.x += sin(pos.y * 3.0 + uTime * (0.4 + h7 * 0.5)) * 0.020 * (1.0 + uTurbulence * 2.0);
        pos.z += cos(pos.y * 2.4 - uTime * 0.35 + h8 * TAU) * 0.024 * (1.0 + uTurbulence * 1.5);
        float twinkle = 0.5 + 0.5 * sin(uTime * mix(0.6, 2.6, h9) + h10 * TAU);
        alpha = mix(0.03, 0.26, pow(h11, 2.0)) * (0.25 + 0.75 * twinkle);
        size = mix(0.8, 1.9, h12);
        color = mix(uAccent * 0.9, uAccentWarm, h13 * 0.55);
        float edgeFade = smoothstep(0.0, 0.05, u) * (1.0 - smoothstep(0.93, 1.0, u));
        alpha *= edgeFade;
    } else if (group < 0.90) {
        float y = mix(-2.15, 2.05, fract(h1 - uTime * 0.008 * uFlowSpeed));
        float x = (h2 - 0.5) * mix(1.7, 3.5, h3);
        float z = (h4 - 0.5) * 2.4;
        pos = vec3(x + sin(uTime * 0.10 + h5 * 9.0) * 0.08, y, z + cos(uTime * 0.07 + h6 * 7.0) * 0.06);
        float twinkle = 0.5 + 0.5 * sin(uTime * mix(0.4, 1.8, h9) + h10 * TAU);
        alpha = mix(0.015, 0.09, pow(h11, 1.6)) * (0.35 + 0.65 * twinkle);
        size = mix(0.9, 2.6, h12);
        color = mix(uAccent * 0.75, uAccentWarm * 0.75, h13 * 0.6);
    } else {
        float u = fract(h1 * 7.31 + uTime * mix(0.014, 0.045, h2) * uFlowSpeed);
        float y = mix(1.60, -1.80, u) * uHeightScale;
        float theta = h3 * TAU + uTime * mix(-0.2, 0.2, h4);
        float radial = mix(0.85, 1.32, h6);
        pos = bodyPoint(y, theta, radial, 0.62);
        pos.x += sin(pos.y * 2.6 + uTime * 0.6 + h7 * TAU) * 0.030;
        float spike = pow(0.5 + 0.5 * sin(uTime * mix(0.9, 2.2, h9) + h10 * TAU), 8.0);
        alpha = (0.16 + 0.70 * spike) * (0.55 + 0.45 * uIntensity);
        size = mix(1.4, 2.6, h12) * (1.0 + 0.5 * spike);
        color = mix(uAccentWarm, vec3(1.0), 0.28);
        float edgeFade = smoothstep(0.0, 0.04, u) * (1.0 - smoothstep(0.95, 1.0, u));
        alpha *= edgeFade;
    }

    vec4 clip = uViewProjection * vec4(pos, 1.0);
    float distanceToCamera = max(distance(pos, uCameraPosition), 0.35);
    alpha *= clamp(2.1 / distanceToCamera, 0.40, 1.30);
    vColor = color * alpha;
    gl_PointSize = clamp(size * uPixelScale / max(clip.w, 0.30), 1.0, 9.0);
    gl_Position = clip;
}
