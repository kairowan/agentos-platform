// Flowing silk ribbons bound to the body surface. Each strand expands into a
// camera-facing band whose half-width follows the projected path tangent, so
// the projection reads as smooth glowing silk instead of hair-thin lines.

in float aStrand;
in float aU;
in float aSide;
uniform mat4 uViewProjection;
uniform vec3 uCameraPosition;
uniform float uFlowSpeed;
uniform float uIntensity;
uniform float uTurbulence;
uniform float uHeightScale;
uniform float uHeadStart;
uniform float uHeadScale;
uniform float uPixelScale;
uniform vec2 uNdcScale;
uniform vec3 uAccent;
uniform vec3 uAccentWarm;
out vec3 vColor;
out float vSide;

float weaveTheta(float t, float h3, float h4, float h5, float h6, float h7, float h8, float h9) {
    float weave = sin(t * TAU * mix(2.4, 5.2, h4) + uTime * (0.18 + 0.14 * h7) + h6 * TAU)
        * mix(0.10, 0.30, h9);
    return weave + sin(uTime * 0.07 + h8 * TAU) * 0.12;
}

vec3 strandPoint(float k, float t, out float bright, out float facingGain) {
    float h1 = hash11(k * 0.731 + 1.7);
    float h2 = hash11(k * 0.523 + 9.2);
    float h3 = hash11(k * 0.917 + 3.7);
    float h4 = hash11(k * 0.379 + 6.1);
    float h5 = hash11(k * 0.661 + 4.8);
    float h6 = hash11(k * 0.293 + 7.9);
    float h7 = hash11(k * 0.847 + 2.3);
    float h8 = hash11(k * 0.449 + 8.4);
    float h9 = hash11(k * 0.587 + 5.5);
    float h10 = hash11(k * 0.751 + 0.9);
    float h11 = hash11(k * 0.977 + 1.1);
    float h12 = hash11(k * 0.311 + 6.7);

    vec3 pos;
    facingGain = 1.0;
    if (k < uHeadStart) {
        float y = mix(1.66, -1.86, t) * uHeightScale;
        float band = (h6 - 0.5) * 2.0;
        bool front = abs(band) < 0.62;
        float bodyW = fieldWidth(y);
        float tailFlare = 1.0 + 0.85 * smoothstep(-0.75, -1.9, y);
        float xoff = band * bodyW * mix(0.85, 1.28, h9) * tailFlare;
        float theta = 1.5707 + weaveTheta(t, h3, h4, h5, h6, h7, h8, h9) * 0.4;
        pos = bodyPoint(y, theta, 1.0, 0.62);
        pos.x = fieldCenter(y) + xoff;
        // Screen-space S: world-x sway scaled by a width proxy that stays generous
        // through the torso so ribbons carve long visible bends, hugging the
        // silhouette near the waist like the reference.
        float sway = sin(y * TAU * mix(0.45, 0.75, h3) + h4 * TAU + k * 1.9)
            * mix(1.15, 2.05, h5)
            + sin(y * TAU * mix(1.15, 1.95, h5) + h3 * TAU + k * 2.6 + uTime * 0.05)
            * mix(0.50, 1.05, h4);
        float micro = sin(y * TAU * mix(2.6, 4.8, h4) + uTime * (0.18 + 0.14 * h7) + h6 * TAU + k)
            * 0.10;
        float envelope = 0.35 + 0.65 * smoothstep(-1.98, -0.4, y) * (1.0 - smoothstep(0.8, 1.8, y));
        pos.x += (sway + micro) * max(bodyW, 0.35) * envelope;
        pos.z += sin(y * 2.2 - uTime * 0.26 + h11 * TAU) * 0.030 * (1.0 + uTurbulence);
        vec3 toCamera = normalize(uCameraPosition - pos);
        vec3 normal = normalize(vec3(cos(theta) * 0.62, 0.0, sin(theta)));
        float facing = abs(dot(normal, toCamera));
        facing = mix(facing, max(facing, 0.55), front ? 1.0 : 0.0);
        facingGain = facing;
        bright = mix(0.26, 1.15, pow(h12, 2.2)) * (0.45 + 0.55 * facing);
    } else {
        float headCy = 1.22 * uHeightScale;
        float radius = 0.42 * (0.90 + uHeadScale * 0.22);
        float latitude = mix(-1.05, 1.02, t);
        float y = headCy + latitude * radius * 0.96;
        float ringRadius = radius * sqrt(max(0.003, 1.0 - pow(latitude, 2.0)));
        float swirl = mix(0.8, 1.8, h3) * (h5 > 0.5 ? 1.0 : -1.0);
        float bandHead = (h6 - 0.5) * 1.9;
        float theta = 1.5707 + bandHead + t * swirl * TAU * 0.55
            + uTime * (0.14 + 0.18 * h7) * (h5 > 0.5 ? 1.0 : -1.0)
            + sin(t * TAU + uTime * 0.30 + h8 * TAU) * 0.20;
        float radial = mix(0.94, 1.20, h9);
        pos = vec3(fieldCenter(y) + cos(theta) * ringRadius * radial, y, sin(theta) * ringRadius * radial * 0.88);
        vec3 toCamera = normalize(uCameraPosition - pos);
        vec3 normal = normalize(vec3(cos(theta) * 0.88, 0.0, sin(theta)));
        facingGain = abs(dot(normal, toCamera));
        bright = mix(0.35, 1.55, pow(h12, 2.2)) * (0.45 + 0.55 * facingGain);
    }
    return pos;
}

void main() {
    float k = aStrand;
    float h2 = hash11(k * 0.523 + 9.2);
    float h13 = hash11(k * 0.647 + 2.9);

    float speed = mix(0.010, 0.040, h2) * uFlowSpeed;
    float base = aU + uTime * speed;
    float u = fract(base);
    float fade = smoothstep(0.0, 0.04, u) * (1.0 - smoothstep(0.95, 1.0, u));

    float bright;
    float facingGain;
    vec3 pos = strandPoint(k, u, bright, facingGain);
    float scratch;
    vec3 posPrev = strandPoint(k, fract(base - 0.0045), scratch, facingGain);
    vec3 posNext = strandPoint(k, fract(base + 0.0045), scratch, facingGain);

    vec4 clip = uViewProjection * vec4(pos, 1.0);
    vec4 clipPrev = uViewProjection * vec4(posPrev, 1.0);
    vec4 clipNext = uViewProjection * vec4(posNext, 1.0);
    float wSafe = max(clip.w, 0.05);
    vec2 screen = clip.xy / wSafe;
    vec2 tangent = clipNext.xy / max(clipNext.w, 0.05) - clipPrev.xy / max(clipPrev.w, 0.05);
    float tangentLength = max(length(tangent), 1e-4);
    vec2 normalDir = vec2(-tangent.y, tangent.x) / tangentLength;

    float taper = smoothstep(0.0, 0.06, u) * (1.0 - smoothstep(0.92, 1.0, u));
    float widthWorld = mix(0.034, 0.125, h13 * h13) * (0.55 + 0.45 * taper);
    float widthNdc = widthWorld * uPixelScale * 0.5 * uNdcScale.y / wSafe;
    vec2 ndcOffset = normalDir * aSide * widthNdc;
    gl_Position = vec4((screen + ndcOffset) * clip.w, clip.z, clip.w);

    vec3 color = mix(uAccent, uAccentWarm, clamp(h13 * 0.16 + facingGain * 0.22, 0.0, 1.0));
    vColor = color * (0.70 + bright * 2.4) * fade * (0.45 + 0.55 * uIntensity);
    vSide = aSide;
}
