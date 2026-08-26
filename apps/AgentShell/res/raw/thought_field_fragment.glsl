precision mediump float;

uniform vec2 uResolution;
uniform float uTime;
uniform float uYaw;
uniform float uZoom;
uniform float uMood;
uniform float uGesture;
uniform float uIntensity;
uniform vec2 uGaze;
uniform vec4 uShape;
uniform vec2 uFieldShape;
uniform vec4 uExpression;
varying vec2 vUv;

const float PI = 3.14159265;
const vec3 AMBER = vec3(1.0, 0.55, 0.20);
const vec3 WARM_WHITE = vec3(1.0, 0.91, 0.72);

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float noise21(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash21(i), hash21(i + vec2(1.0, 0.0)), f.x),
        mix(hash21(i + vec2(0.0, 1.0)), hash21(i + vec2(1.0)), f.x), f.y);
}

float sdSegment(vec2 p, vec2 a, vec2 b) {
    vec2 pa = p - a;
    vec2 ba = b - a;
    float h = clamp(dot(pa, ba) / max(dot(ba, ba), 0.0001), 0.0, 1.0);
    return length(pa - ba * h);
}

vec2 bezierPoint(vec2 a, vec2 b, vec2 c, float t) {
    float s = 1.0 - t;
    return s * s * a + 2.0 * s * t * b + t * t * c;
}

float sdBezier(vec2 p, vec2 a, vec2 b, vec2 c) {
    float distanceToCurve = 10.0;
    vec2 previous = a;
    for (int index = 1; index <= 14; index++) {
        float progress = float(index) / 14.0;
        vec2 current = bezierPoint(a, b, c, progress);
        distanceToCurve = min(distanceToCurve, sdSegment(p, previous, current));
        previous = current;
    }
    return distanceToCurve;
}

float nodeGlow(vec2 p, vec2 node) {
    float radius = length(p - node);
    return exp(-radius * 65.0) * 1.8 + exp(-radius * 15.0) * 0.25;
}

vec3 moodColor(float mood) {
    vec3 result = AMBER;
    if (abs(mood - 2.0) < 0.5) result = vec3(0.32, 1.0, 0.78);
    if (abs(mood - 3.0) < 0.5) result = vec3(0.48, 0.67, 1.0);
    if (abs(mood - 5.0) < 0.5) result = vec3(1.0, 0.38, 0.30);
    if (abs(mood - 1.0) < 0.5) result = vec3(1.0, 0.73, 0.30);
    return result;
}

void main() {
    float aspect = uResolution.y / max(uResolution.x, 1.0);
    vec2 p = (vUv - 0.5) * vec2(2.0, 2.0 * aspect);
    p *= uZoom / 5.3;
    p /= vec2(0.86 + uFieldShape.x * 0.28, 0.90 + uFieldShape.y * 0.18);
    p.y += 0.04;

    float time = uTime;
    float yaw = sin(radians(uYaw));
    vec3 accent = moodColor(uMood);
    float verticalMask = smoothstep(-1.78, -1.52, p.y) * (1.0 - smoothstep(1.42, 1.62, p.y));
    float center = sin(p.y * 2.05 + time * 0.24) * 0.065
        + sin(p.y * 4.7 - time * 0.15) * 0.025 + yaw * p.y * 0.035;
    float knotScale = 0.90 + uShape.x * 0.20;
    float width = (0.13
        + 0.25 * exp(-pow((p.y - 0.72) / 0.46, 2.0))
        + 0.42 * exp(-pow((p.y + 0.10) / 0.72, 2.0))
        + 0.16 * exp(-pow((p.y + 1.05) / 0.50, 2.0))) * knotScale;
    float bodyDistance = abs(p.x - center) - width;
    float inside = smoothstep(0.035, -0.035, bodyDistance) * verticalMask;
    float glassEdge = exp(-abs(bodyDistance) * 45.0) * verticalMask;
    float internalNoise = noise21(p * vec2(8.0, 11.0) + vec2(time * 0.16, -time * 0.11));

    vec3 background = vec3(0.007, 0.016, 0.020);
    background += vec3(0.008, 0.022, 0.025) * (1.0 - smoothstep(0.2, 1.8, length(p)));
    vec3 color = background;

    // Layered smoky glass. Thin highlights imply refraction without a costly scene texture pass.
    float leftLobe = abs(p.x - (center - width * 0.52 + sin(p.y * 3.3 + time * 0.19) * 0.055));
    float rightLobe = abs(p.x - (center + width * 0.58 + sin(p.y * 2.7 - time * 0.21) * 0.045));
    float crossingLobe = abs(p.x - (center + sin(p.y * 3.8 + time * 0.17) * width * 0.52));
    float lobeLight = (exp(-leftLobe * 60.0) + exp(-rightLobe * 62.0)
        + exp(-crossingLobe * 72.0) * 0.65) * inside;
    color += inside * vec3(0.018, 0.025, 0.027) * (0.7 + internalNoise * 0.9);
    color += glassEdge * vec3(0.38, 0.44, 0.42) * (0.34 + internalNoise * 0.45);
    color += lobeLight * vec3(0.48, 0.42, 0.31) * 0.42;

    // Nine independently moving thought filaments create the dense golden interior flow.
    float filaments = 0.0;
    for (int index = 0; index < 9; index++) {
        float fi = float(index);
        float phase = fi * 0.77;
        float spread = (fi - 4.0) * width * 0.105;
        float flowX = center + spread
            + sin(p.y * (2.4 + fi * 0.08) + phase + time * (0.18 + fi * 0.012))
                * width * (0.20 + mod(fi, 3.0) * 0.055);
        float strand = exp(-abs(p.x - flowX) * (115.0 + fi * 4.0));
        float broken = 0.35 + 0.65 * noise21(vec2(p.y * 9.0 - time * 0.7, phase));
        filaments += strand * broken * inside;
    }
    color += AMBER * filaments * (0.25 + uExpression.w * 0.30);
    color += WARM_WHITE * pow(filaments / 9.0, 1.6) * 2.4;

    // Shader-generated moving motes; two grids give depth without a particle texture.
    vec2 moteSpace = (p - vec2(time * 0.018, -time * 0.035)) / vec2(0.115, 0.135);
    vec2 moteCell = floor(moteSpace);
    vec2 moteLocal = fract(moteSpace) - 0.5;
    float moteRandom = hash21(moteCell);
    vec2 moteOffset = vec2(hash21(moteCell + 4.1), hash21(moteCell + 9.7)) - 0.5;
    float mote = (1.0 - smoothstep(0.018, 0.065, length(moteLocal - moteOffset * 0.55)))
        * step(0.54, moteRandom) * inside;
    color += accent * mote * (0.7 + moteRandom * 1.5);

    vec2 dustSpace = (p + vec2(time * 0.010, time * 0.018)) / vec2(0.235, 0.255);
    vec2 dustCell = floor(dustSpace);
    vec2 dustLocal = fract(dustSpace) - 0.5;
    float dustRandom = hash21(dustCell + 31.0);
    vec2 dustOffset = vec2(hash21(dustCell + 17.0), hash21(dustCell + 27.0)) - 0.5;
    float dust = (1.0 - smoothstep(0.012, 0.045, length(dustLocal - dustOffset * 0.7)))
        * step(0.67, dustRandom) * (1.0 - smoothstep(0.38, 1.18, abs(bodyDistance)));
    color += accent * dust * 0.85;

    // A stable memory constellation floats around the deforming body.
    float drift = sin(time * 0.31) * 0.025;
    vec2 n0 = vec2(-0.63 + drift, 1.05);
    vec2 n1 = vec2(-0.27, 1.34 + drift);
    vec2 n2 = vec2(0.18 + drift, 1.25);
    vec2 n3 = vec2(0.59, 0.98 - drift);
    vec2 n4 = vec2(0.76 + drift, 0.48);
    vec2 n5 = vec2(0.68, -0.42 + drift);
    vec2 n6 = vec2(0.44 - drift, -1.12);
    vec2 n7 = vec2(-0.38, -1.27 - drift);
    vec2 n8 = vec2(-0.72 - drift, -0.62);
    vec2 n9 = vec2(-0.78, 0.22 + drift);
    float links = 0.0;
    links += exp(-sdSegment(p, n0, n1) * 90.0);
    links += exp(-sdSegment(p, n1, n2) * 90.0);
    links += exp(-sdSegment(p, n2, n3) * 90.0);
    links += exp(-sdSegment(p, n3, n4) * 90.0);
    links += exp(-sdSegment(p, n4, n5) * 90.0);
    links += exp(-sdSegment(p, n5, n6) * 90.0);
    links += exp(-sdSegment(p, n6, n7) * 90.0);
    links += exp(-sdSegment(p, n7, n8) * 90.0);
    links += exp(-sdSegment(p, n8, n9) * 90.0);
    links += exp(-sdSegment(p, n9, n0) * 90.0);
    links += exp(-sdSegment(p, n0, n3) * 105.0) * 0.7;
    links += exp(-sdSegment(p, n4, n8) * 105.0) * 0.6;
    float nodes = nodeGlow(p, n0) + nodeGlow(p, n1) + nodeGlow(p, n2) + nodeGlow(p, n3)
        + nodeGlow(p, n4) + nodeGlow(p, n5) + nodeGlow(p, n6) + nodeGlow(p, n7)
        + nodeGlow(p, n8) + nodeGlow(p, n9);
    float outsideField = 1.0 - inside * 0.55;
    color += accent * links * outsideField * 0.12;
    color += accent * nodes * outsideField * 0.48;

    // The amber core remains the identity anchor across every emotion and gesture.
    vec2 corePosition = vec2(center, -0.10);
    vec2 coreVector = (p - corePosition) / vec2(1.0, 1.16);
    float coreRadius = length(coreVector) / (0.85 + uShape.y * 0.30);
    float coreGlow = exp(-coreRadius * 4.8) * 0.42 + exp(-coreRadius * 19.0) * 2.2;
    float starBurst = exp(-abs(p.x - corePosition.x) * 115.0) * exp(-abs(p.y - corePosition.y) * 3.2)
        + exp(-abs(p.y - corePosition.y) * 115.0) * exp(-abs(p.x - corePosition.x) * 3.2);
    color += AMBER * coreGlow * (0.78 + uExpression.w * 0.42);
    color += WARM_WHITE * starBurst * 0.62;

    // Optical expression condenses only while interaction state asks for it.
    float facePresence = uExpression.z;
    float eyeGap = (0.10 + uShape.z * 0.09) * max(cos(radians(uYaw)), 0.35);
    float eyeY = 0.72 + uGaze.y * 0.05;
    float eyeX = center + uGaze.x * 0.055;
    float blink = step(0.12, abs(sin(time * 0.69)));
    vec2 leftEyeVector = (p - vec2(eyeX - eyeGap, eyeY)) / vec2(0.045 + uShape.w * 0.025, 0.042 * blink + 0.006);
    vec2 rightEyeVector = (p - vec2(eyeX + eyeGap, eyeY)) / vec2(0.045 + uShape.w * 0.025, 0.042 * blink + 0.006);
    float eyes = exp(-length(leftEyeVector) * 4.0) + exp(-length(rightEyeVector) * 4.0);
    color += WARM_WHITE * eyes * facePresence * 2.1;

    float talkBeat = uExpression.x * (0.5 + 0.5 * sin(time * 9.0));
    float voiceY = 0.51 + sin((p.x - center) * 35.0 + time * 8.0) * 0.010 * talkBeat;
    float voiceWidth = 0.09 + uExpression.y * 0.08;
    float voiceMark = exp(-abs(p.y - voiceY) * 170.0)
        * (1.0 - smoothstep(voiceWidth * 0.65, voiceWidth, abs(p.x - center)));
    color += accent * voiceMark * facePresence * 1.45;

    // A hand-like ribbon is materialized only for active communication gestures.
    float gestureActive = max(1.0 - step(0.5, abs(uGesture - 3.0)), step(4.5, uGesture));
    gestureActive *= 1.0 - step(9.5, uGesture);
    gestureActive *= smoothstep(0.05, 0.35, uIntensity);
    vec2 gestureStart = vec2(center - 0.20, 0.05);
    vec2 gestureEnd = vec2(-0.83, 0.53 + sin(time * 6.2) * 0.05);
    if (abs(uGesture - 6.0) < 0.5) gestureEnd = vec2(0.86, 0.24);
    if (abs(uGesture - 7.0) < 0.5) gestureEnd = vec2(-0.73, 1.00 + sin(time * 4.0) * 0.04);
    if (abs(uGesture - 8.0) < 0.5) gestureEnd = vec2(-0.70, -0.02);
    if (abs(uGesture - 9.0) < 0.5) gestureEnd = vec2(-0.82, 0.42 + sin(time * 3.1) * 0.06);
    if (abs(uGesture - 3.0) < 0.5) gestureEnd = vec2(-0.66, 0.30 + sin(time * 3.4) * 0.05);
    vec2 gestureControl = vec2((gestureStart.x + gestureEnd.x) * 0.52,
        max(gestureStart.y, gestureEnd.y) + 0.28);
    float armDistance = sdBezier(p, gestureStart, gestureControl, gestureEnd);
    float armRibbon = exp(-armDistance * 38.0) + exp(-armDistance * 105.0) * 0.8;
    float palm = 1.0 - smoothstep(0.075, 0.15,
        length((p - gestureEnd) / vec2(1.0, 0.72)));
    float fingers = 0.0;
    fingers += exp(-sdSegment(p, gestureEnd + vec2(-0.02, 0.03), gestureEnd + vec2(-0.17, 0.15)) * 85.0);
    fingers += exp(-sdSegment(p, gestureEnd + vec2(0.01, 0.04), gestureEnd + vec2(-0.08, 0.20)) * 85.0);
    fingers += exp(-sdSegment(p, gestureEnd + vec2(0.04, 0.02), gestureEnd + vec2(0.02, 0.19)) * 85.0);
    if (abs(uGesture - 6.0) < 0.5) {
        fingers += exp(-sdSegment(p, gestureEnd, gestureEnd + vec2(0.25, 0.02)) * 95.0) * 1.8;
    }
    color += vec3(0.14, 0.11, 0.07) * armRibbon * gestureActive;
    color += AMBER * (armRibbon * 0.28 + palm * 0.55 + fingers * 0.32) * gestureActive;
    color += WARM_WHITE * fingers * gestureActive * 0.12;

    // Filmic compression and vignette keep highlights readable on OLED displays.
    float vignette = 1.0 - smoothstep(0.72, 1.56, length(vec2(p.x, p.y / max(aspect, 1.0))));
    color *= 0.74 + vignette * 0.26;
    color = vec3(1.0) - exp(-color * 1.16);
    color = pow(max(color, 0.0), vec3(0.92));
    gl_FragColor = vec4(color, 1.0);
}
