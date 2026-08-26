precision highp float;

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
uniform float uSurfaceLayer;
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

float fbm(vec2 p) {
    float value = 0.0;
    value += noise21(p) * 0.56;
    p = p * 2.03 + 13.7;
    value += noise21(p) * 0.29;
    p = p * 2.01 + 7.1;
    value += noise21(p) * 0.15;
    return value;
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

float sdShortBezier(vec2 p, vec2 a, vec2 b, vec2 c) {
    float distanceToCurve = 10.0;
    vec2 previous = a;
    for (int index = 1; index <= 6; index++) {
        float progress = float(index) / 6.0;
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
    float verticalMask = smoothstep(-1.82, -1.62, p.y) * (1.0 - smoothstep(1.70, 1.80, p.y));
    float lowerBody = 1.0 - smoothstep(-0.15, 0.58, p.y);
    float center = sin(p.y * 2.02 + time * 0.24) * 0.055
        + sin(p.y * 4.45 - time * 0.15) * 0.022
        + sin((p.y + 1.05) * 2.7 - time * 0.10) * lowerBody * 0.140
        + yaw * p.y * 0.035;
    center -= smoothstep(0.68, 1.72, p.y) * 0.050;
    float knotScale = 0.90 + uShape.x * 0.20;
    float headUnit = clamp(1.0 - pow((p.y - 1.22) / 0.58, 2.0), 0.0, 1.0);
    float torsoUnit = clamp(1.0 - pow((p.y + 0.08) / 1.27, 2.0), 0.0, 1.0);
    float tailUnit = clamp(1.0 - pow((p.y + 1.38) / 0.55, 2.0), 0.0, 1.0);
    float headWidth = 0.46 * sqrt(headUnit);
    float torsoWidth = 0.50 * sqrt(torsoUnit);
    float tailWidth = 0.19 * sqrt(tailUnit);
    float neckWidth = mix(0.205, 0.39, 1.0 - smoothstep(0.44, 0.61, p.y));
    float shoulderBlend = smoothstep(0.43, 0.64, p.y);
    float headBlend = smoothstep(0.61, 0.77, p.y);
    float width = mix(torsoWidth, neckWidth, shoulderBlend);
    width = mix(width, headWidth, headBlend);
    width = mix(max(torsoWidth, tailWidth), width, smoothstep(-1.34, -1.16, p.y));
    width *= 1.0 - 0.29 * exp(-pow((p.y + 0.59) / 0.21, 2.0));
    width *= knotScale;
    float side = p.x - center;
    float asymmetricWidth = width * (1.0 + sign(side) * 0.09
        * sin(p.y * 2.75 + time * 0.17));
    float bodyDistance = abs(side) - asymmetricWidth;
    float inside = (1.0 - smoothstep(-0.035, 0.035, bodyDistance)) * verticalMask;
    float edgeVariation = 0.38 + 0.62 * fbm(vec2(p.y * 5.2 - time * 0.08, side * 4.0));
    float glassEdge = exp(-abs(bodyDistance) * 48.0) * verticalMask * edgeVariation;
    float radialPosition = clamp(abs(side) / max(asymmetricWidth, 0.001), 0.0, 1.0);
    float volumeDepth = sqrt(max(0.0, 1.0 - radialPosition * radialPosition));
    vec2 glassWarp = p * vec2(7.0, 10.0);
    glassWarp += vec2(fbm(p * 3.2 + time * 0.04), fbm(p * 3.7 - time * 0.035)) * 2.4;
    float internalNoise = fbm(glassWarp + vec2(time * 0.13, -time * 0.09));

    vec3 background = vec3(0.007, 0.016, 0.020);
    background += vec3(0.008, 0.022, 0.025) * (1.0 - smoothstep(0.2, 1.8, length(p)));
    vec3 color = background;

    // Layered smoky glass. Thin highlights imply refraction without a costly scene texture pass.
    float leftLobe = abs(p.x - (center - width * 0.55 + sin(p.y * 3.3 + time * 0.19) * 0.060));
    float rightLobe = abs(p.x - (center + width * 0.61 + sin(p.y * 2.7 - time * 0.21) * 0.052));
    float crossingLobe = abs(p.x - (center + sin(p.y * 3.8 + time * 0.17) * width * 0.56));
    float innerLobe = abs(p.x - (center - sin(p.y * 2.9 - time * 0.12) * width * 0.31));
    float lobeLight = (exp(-leftLobe * 76.0) + exp(-rightLobe * 78.0)
        + exp(-crossingLobe * 90.0) * 0.76 + exp(-innerLobe * 105.0) * 0.42) * inside;
    float glassCaustic = pow(internalNoise, 3.2) * inside;
    float secondaryEdge = exp(-abs(bodyDistance + sin(p.y * 8.0 - time * 0.22) * 0.018) * 92.0)
        * verticalMask;
    color += inside * vec3(0.016, 0.023, 0.024)
        * (0.72 + internalNoise * 1.05 + volumeDepth * 0.32);
    color += glassCaustic * vec3(0.30, 0.25, 0.17) * 0.32;
    float screenSurface = mix(1.0, 0.24, uSurfaceLayer);
    color += glassEdge * vec3(0.66, 0.72, 0.69) * (0.34 + internalNoise * 0.52) * screenSurface;
    color += secondaryEdge * vec3(0.28, 0.32, 0.31) * 0.26 * screenSurface;
    color += lobeLight * vec3(0.64, 0.52, 0.34) * 0.54;

    // Fine multi-speed thought filaments create the dense golden interior flow.
    float filaments = 0.0;
    for (int index = 0; index < 16; index++) {
        float fi = float(index);
        float phase = fi * 0.61;
        float spread = (fi - 7.5) * width * 0.060;
        float turbulence = (noise21(vec2(p.y * 2.1 + phase, time * 0.08 + fi)) - 0.5)
            * width * 0.30;
        float flowX = center + spread
            + sin(p.y * (2.2 + fi * 0.055) + phase + time * (0.15 + fi * 0.009))
                * width * (0.17 + mod(fi, 4.0) * 0.038) + turbulence;
        float strand = exp(-abs(p.x - flowX) * (150.0 + fi * 5.0));
        float broken = 0.25 + 0.75 * noise21(vec2(p.y * 9.0 - time * 0.62, phase));
        filaments += strand * broken * inside;
    }
    color += AMBER * filaments * (0.22 + uExpression.w * 0.28);
    color += WARM_WHITE * pow(filaments / 16.0, 1.32) * 3.2;

    float fineThreadPhase = side / max(width, 0.035) * 21.0 + p.y * 4.6
        + sin(p.y * 7.0 - time * 0.18) * 2.1 + internalNoise * 3.2;
    float fineThreads = pow(abs(sin(fineThreadPhase)), 24.0)
        * (0.18 + 0.82 * noise21(vec2(p.y * 13.0 + time * 0.31, side * 18.0))) * inside;
    color += (WARM_WHITE * 0.20 + AMBER * 0.12) * fineThreads;

    vec2 grainSpace = (p - vec2(time * 0.008, -time * 0.025)) / vec2(0.034, 0.044);
    vec2 grainCell = floor(grainSpace);
    vec2 grainLocal = fract(grainSpace) - 0.5;
    float grainRandom = hash21(grainCell + 71.0);
    vec2 grainOffset = vec2(hash21(grainCell + 3.7), hash21(grainCell + 8.3)) - 0.5;
    float microGrain = (1.0 - smoothstep(0.025, 0.085,
        length((grainLocal - grainOffset * 0.62) * vec2(0.72, 1.8)))) * step(0.42, grainRandom);
    color += AMBER * microGrain * inside * (0.28 + grainRandom * 0.64);

    // Shader-generated moving motes; two grids give depth without a particle texture.
    vec2 moteSpace = (p - vec2(time * 0.018, -time * 0.035)) / vec2(0.092, 0.105);
    vec2 moteCell = floor(moteSpace);
    vec2 moteLocal = fract(moteSpace) - 0.5;
    float moteRandom = hash21(moteCell);
    vec2 moteOffset = vec2(hash21(moteCell + 4.1), hash21(moteCell + 9.7)) - 0.5;
    float mote = (1.0 - smoothstep(0.014, 0.052, length(moteLocal - moteOffset * 0.55)))
        * step(0.50, moteRandom) * inside;
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
    vec2 m0 = vec2(-0.48, 0.76 + drift);
    vec2 m1 = vec2(-0.24 + drift, 1.08);
    vec2 m2 = vec2(0.39, 0.84 - drift);
    vec2 m3 = vec2(0.50 + drift, 0.25);
    vec2 m4 = vec2(0.34, -0.52 + drift);
    vec2 m5 = vec2(-0.46 - drift, -0.41);
    links += exp(-sdSegment(p, m0, m1) * 118.0) * 0.65;
    links += exp(-sdSegment(p, m1, m2) * 118.0) * 0.65;
    links += exp(-sdSegment(p, m2, m3) * 118.0) * 0.65;
    links += exp(-sdSegment(p, m3, m4) * 118.0) * 0.65;
    links += exp(-sdSegment(p, m4, m5) * 118.0) * 0.65;
    links += exp(-sdSegment(p, m5, m0) * 118.0) * 0.65;
    links += exp(-sdSegment(p, m0, m3) * 130.0) * 0.42;
    links += exp(-sdSegment(p, m2, m5) * 130.0) * 0.42;
    float nodes = nodeGlow(p, n0) + nodeGlow(p, n1) + nodeGlow(p, n2) + nodeGlow(p, n3)
        + nodeGlow(p, n4) + nodeGlow(p, n5) + nodeGlow(p, n6) + nodeGlow(p, n7)
        + nodeGlow(p, n8) + nodeGlow(p, n9)
        + nodeGlow(p, m0) + nodeGlow(p, m1) + nodeGlow(p, m2)
        + nodeGlow(p, m3) + nodeGlow(p, m4) + nodeGlow(p, m5);
    float outsideField = 1.0 - inside * 0.55;
    color += accent * links * outsideField * 0.12;
    color += accent * nodes * outsideField * 0.48;

    // The amber core remains the identity anchor across every emotion and gesture.
    vec2 corePosition = vec2(center, 0.15);
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
    float eyeY = 1.20 + uGaze.y * 0.05;
    float eyeX = center + uGaze.x * 0.055;
    float blink = step(0.12, abs(sin(time * 0.69)));
    vec2 leftEyeVector = (p - vec2(eyeX - eyeGap, eyeY)) / vec2(0.045 + uShape.w * 0.025, 0.042 * blink + 0.006);
    vec2 rightEyeVector = (p - vec2(eyeX + eyeGap, eyeY)) / vec2(0.045 + uShape.w * 0.025, 0.042 * blink + 0.006);
    float leftEyeDistance = length(leftEyeVector);
    float rightEyeDistance = length(rightEyeVector);
    float eyes = exp(-leftEyeDistance * 4.8) + exp(-rightEyeDistance * 4.8);
    float eyeRings = exp(-abs(leftEyeDistance - 0.78) * 12.0)
        + exp(-abs(rightEyeDistance - 0.78) * 12.0);
    color += WARM_WHITE * (eyes * 1.8 + eyeRings * 0.20) * facePresence;

    float talkBeat = uExpression.x * (0.5 + 0.5 * sin(time * 9.0));
    float voiceY = 0.98 + sin((p.x - center) * 35.0 + time * 8.0) * 0.010 * talkBeat;
    float voiceWidth = 0.09 + uExpression.y * 0.08;
    float voiceMark = exp(-abs(p.y - voiceY) * 170.0)
        * (1.0 - smoothstep(voiceWidth * 0.65, voiceWidth, abs(p.x - center)));
    color += accent * voiceMark * facePresence * 1.45;

    // A hand-like ribbon is materialized only for active communication gestures.
    float gestureActive = max(1.0 - step(0.5, abs(uGesture - 3.0)), step(4.5, uGesture));
    gestureActive *= 1.0 - step(9.5, uGesture);
    gestureActive *= smoothstep(0.05, 0.35, uIntensity);
    gestureActive *= 1.0 - uSurfaceLayer;
    vec2 gestureStart = vec2(center - 0.31, 0.25);
    vec2 gestureEnd = vec2(-0.64, 0.76 + sin(time * 6.2) * 0.035);
    if (abs(uGesture - 6.0) < 0.5) gestureEnd = vec2(0.86, 0.24);
    if (abs(uGesture - 7.0) < 0.5) gestureEnd = vec2(-0.73, 1.00 + sin(time * 4.0) * 0.04);
    if (abs(uGesture - 8.0) < 0.5) gestureEnd = vec2(-0.70, -0.02);
    if (abs(uGesture - 9.0) < 0.5) gestureEnd = vec2(-0.82, 0.42 + sin(time * 3.1) * 0.06);
    if (abs(uGesture - 3.0) < 0.5) gestureEnd = vec2(-0.66, 0.30 + sin(time * 3.4) * 0.05);
    vec2 gestureWrist = gestureEnd + vec2(0.036, -0.026);
    vec2 gestureControl = vec2((gestureStart.x + gestureWrist.x) * 0.54,
        max(gestureStart.y, gestureWrist.y) + 0.20);
    float armDistance = sdBezier(p, gestureStart, gestureControl, gestureWrist);
    float armFill = 1.0 - smoothstep(0.042, 0.070, armDistance);
    float armEdge = exp(-abs(armDistance - 0.050) * 130.0);
    vec2 palmCenter = gestureEnd;
    vec2 palmVector = p - palmCenter;
    vec2 palmLocal = vec2(
        palmVector.x * 0.94 - palmVector.y * 0.34,
        palmVector.x * 0.34 + palmVector.y * 0.94
    );
    float palmDistance = length(palmLocal / vec2(0.122, 0.086));
    float palmFill = 1.0 - smoothstep(0.80, 1.06, palmDistance);
    float palmEdge = exp(-abs(palmDistance - 0.92) * 18.0);
    vec2 fingerBase = gestureEnd + vec2(-0.006, 0.055);
    float fingerDistance = 10.0;
    fingerDistance = min(fingerDistance, sdShortBezier(p,
        fingerBase + vec2(-0.048, -0.010), fingerBase + vec2(-0.145, 0.018), fingerBase + vec2(-0.225, 0.070)));
    fingerDistance = min(fingerDistance, sdShortBezier(p,
        fingerBase + vec2(-0.023, 0.012), fingerBase + vec2(-0.125, 0.105), fingerBase + vec2(-0.185, 0.185)));
    fingerDistance = min(fingerDistance, sdShortBezier(p,
        fingerBase + vec2(0.010, 0.018), fingerBase + vec2(-0.055, 0.150), fingerBase + vec2(-0.075, 0.220)));
    fingerDistance = min(fingerDistance, sdShortBezier(p,
        fingerBase + vec2(0.043, 0.008), fingerBase + vec2(0.005, 0.135), fingerBase + vec2(0.020, 0.205)));
    fingerDistance = min(fingerDistance, sdShortBezier(p,
        fingerBase + vec2(0.070, -0.014), fingerBase + vec2(0.082, 0.090), fingerBase + vec2(0.120, 0.155)));
    float fingerFill = 1.0 - smoothstep(0.009, 0.020, fingerDistance);
    float fingerEdge = exp(-abs(fingerDistance - 0.013) * 180.0);
    float fingerGlow = exp(-fingerDistance * 125.0);
    if (abs(uGesture - 6.0) < 0.5) {
        float pointing = sdSegment(p, gestureEnd, gestureEnd + vec2(0.25, 0.02));
        fingerFill = max(fingerFill, 1.0 - smoothstep(0.010, 0.025, pointing));
        fingerGlow += exp(-pointing * 105.0) * 1.6;
    }
    color += vec3(0.014, 0.016, 0.015) * (armFill + palmFill + fingerFill) * gestureActive;
    color += vec3(0.68, 0.59, 0.45) * (armEdge * 0.27 + palmEdge * 0.25 + fingerEdge * 0.18) * gestureActive;
    color += AMBER * (armFill * 0.070 + palmFill * 0.095 + fingerGlow * 0.14) * gestureActive;
    color += WARM_WHITE * fingerFill * gestureActive * 0.10;
    float handFill = clamp(armFill + palmFill + fingerFill, 0.0, 1.0) * gestureActive;
    color += WARM_WHITE * handFill * pow(internalNoise, 5.0) * 0.23;

    // Filmic compression and vignette keep highlights readable on OLED displays.
    float vignette = 1.0 - smoothstep(0.72, 1.56, length(vec2(p.x, p.y / max(aspect, 1.0))));
    color *= 0.74 + vignette * 0.26;
    color = vec3(1.0) - exp(-color * 1.16);
    color = pow(max(color, 0.0), vec3(0.92));
    gl_FragColor = vec4(color, 1.0);
}
