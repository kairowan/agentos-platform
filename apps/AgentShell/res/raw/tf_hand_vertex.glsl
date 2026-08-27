// Particle hand: strands flow along the arm chain and along each finger's
// quadratic bezier. uMode selects line strands (0) or spark points (1).

in float aStrand;
in float aU;
in float aSide;
uniform mat4 uViewProjection;
uniform vec3 uCameraPosition;
uniform float uMode;
uniform float uFlowSpeed;
uniform float uHandGain;
uniform float uTurbulence;
uniform vec3 uArmChain[4];
uniform vec3 uFingerRoot[5];
uniform vec3 uFingerJoint[5];
uniform vec3 uFingerTip[5];
uniform vec3 uAccent;
uniform vec3 uAccentWarm;
uniform float uPixelScale;
uniform vec2 uNdcScale;
out vec3 vColor;
out float vSide;

vec3 bezier(vec3 a, vec3 b, vec3 c, float t) {
    float s = 1.0 - t;
    return s * s * a + 2.0 * s * t * b + t * t * c;
}

vec3 armPoint(float u, float salt) {
    float scaled = clamp(u, 0.0, 0.9999);
    float segment = scaled * 3.0;
    int index = int(floor(segment));
    float local = segment - float(index);
    vec3 a = uArmChain[index];
    vec3 b = uArmChain[index + 1];
    vec3 p = mix(a, b, local);
    p += (vec3(hash11(salt + 3.1), hash11(salt + 5.7), hash11(salt + 7.9)) - 0.5) * 0.052;
    return p;
}

vec3 fingerPoint(float finger, float u, float salt) {
    int index = int(clamp(floor(finger), 0.0, 4.0));
    vec3 p = bezier(uFingerRoot[index], uFingerJoint[index], uFingerTip[index], clamp(u, 0.0, 1.0));
    p += (vec3(hash11(salt + 11.3), hash11(salt + 13.7), hash11(salt + 17.1)) - 0.5) * 0.030;
    return p;
}

void main() {
    if (uMode < 0.5) {
        float k = aStrand;
        float arm = k < 10.0 ? 1.0 : 0.0;
        float h1 = hash11(k * 0.731 + 1.7);
        float h2 = hash11(k * 0.523 + 9.2);
        float h3 = hash11(k * 0.917 + 3.7);
        float u = fract(aU + uTime * mix(0.02, 0.07, h2) * uFlowSpeed);
        float fade = smoothstep(0.0, 0.10, u) * (1.0 - smoothstep(0.88, 1.0, u));
        vec3 pos = arm > 0.5
            ? armPoint(u, k * 7.0 + h1 * 3.0)
            : fingerPoint(floor((k - 10.0) / 8.0), u, k * 7.0 + h1 * 3.0);
        pos.x += sin(uTime * 6.2 + h3 * TAU) * 0.006 * (1.0 + uTurbulence);
        vec3 posNext = arm > 0.5
            ? armPoint(fract(u + 0.02), k * 7.0 + h1 * 3.0)
            : fingerPoint(floor((k - 10.0) / 8.0), fract(u + 0.02), k * 7.0 + h1 * 3.0);
        vec4 clip = uViewProjection * vec4(pos, 1.0);
        vec4 clipNext = uViewProjection * vec4(posNext, 1.0);
        vec2 screen = clip.xy / max(clip.w, 0.05);
        vec2 tangent = clipNext.xy / max(clipNext.w, 0.05) - screen;
        float tl = max(length(tangent), 1e-4);
        vec2 normalDir = vec2(-tangent.y, tangent.x) / tl;
        float widthNdc = 0.010 * uPixelScale * 0.5 * uNdcScale.y / max(clip.w, 0.05);
        gl_Position = vec4((screen + normalDir * aSide * widthNdc) * clip.w, clip.z, clip.w);
        vec3 color = mix(uAccent, uAccentWarm, 0.35 + 0.4 * h2);
        vColor = color * (0.55 + 0.85 * h1) * fade * uHandGain;
        vSide = aSide;
    } else {
        float id = float(gl_VertexID);
        float arm = hash11(id * 0.0113 + 0.7) < 0.30 ? 1.0 : 0.0;
        float h1 = hash11(id * 0.0523 + 1.3);
        float h2 = hash11(id * 0.0917 + 2.1);
        float h3 = hash11(id * 0.0379 + 3.3);
        float h4 = hash11(id * 0.0761 + 4.7);
        float h5 = hash11(id * 0.0127 + 5.9);
        float u = fract(h1 + uTime * mix(0.05, 0.16, h2) * uFlowSpeed);
        float fade = smoothstep(0.0, 0.08, u) * (1.0 - smoothstep(0.90, 1.0, u));
        vec3 pos = arm > 0.5
            ? armPoint(u, id * 1.37 + 2.0)
            : fingerPoint(floor(h3 * 4.999), u, id * 1.37 + 5.0);
        pos += (vec3(h4, h5, hash11(id * 0.0619 + 6.2)) - 0.5) * 0.055 * (0.6 + 0.8 * u);
        float twinkle = 0.5 + 0.5 * sin(uTime * mix(1.2, 3.2, h2) + h3 * TAU);
        float alpha = mix(0.10, 0.85, pow(h4, 1.8)) * (0.30 + 0.70 * twinkle);
        vec3 color = mix(uAccentWarm, vec3(1.0), h5 * 0.5);
        vColor = color * alpha * fade * uHandGain * 1.35;
        vec4 clip = uViewProjection * vec4(pos, 1.0);
        gl_PointSize = clamp(mix(1.0, 2.8, h5) * uPixelScale / max(clip.w, 0.30), 1.0, 8.0);
        gl_Position = clip;
    }
}
