// Shared math for the Thought Field particle pipeline. The runtime concatenates
// this after the "#version 300 es" and precision lines; never compiled standalone.

uniform float uTime;
uniform vec2 uFieldShape;

const float TAU = 6.2831853;
const vec3 AMBER = vec3(1.0, 0.55, 0.20);
const vec3 WARM_WHITE = vec3(1.0, 0.93, 0.76);

float hash11(float p) {
    p = fract(p * 0.1031);
    p *= p + 33.33;
    p *= p + p;
    return fract(p);
}

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

vec3 moodColor(float mood) {
    vec3 result = AMBER;
    if (abs(mood - 2.0) < 0.5) result = vec3(0.32, 1.0, 0.78);
    if (abs(mood - 3.0) < 0.5) result = vec3(0.48, 0.67, 1.0);
    if (abs(mood - 5.0) < 0.5) result = vec3(1.0, 0.38, 0.30);
    if (abs(mood - 1.0) < 0.5) result = vec3(1.0, 0.73, 0.30);
    return result;
}

float fieldWidth(float y) {
    float headUnit = clamp(1.0 - pow((y - 1.22) / 0.58, 2.0), 0.0, 1.0);
    float torsoUnit = clamp(1.0 - pow((y + 0.08) / 1.27, 2.0), 0.0, 1.0);
    float tailUnit = clamp(1.0 - pow((y + 1.38) / 0.55, 2.0), 0.0, 1.0);
    float headWidth = 0.46 * sqrt(headUnit);
    float torsoWidth = 0.50 * sqrt(torsoUnit);
    float tailWidth = 0.19 * sqrt(tailUnit);
    float neckWidth = mix(0.205, 0.39, 1.0 - smoothstep(0.44, 0.61, y));
    float width = mix(torsoWidth, neckWidth, smoothstep(0.43, 0.64, y));
    width = mix(width, headWidth, smoothstep(0.61, 0.77, y));
    width = mix(max(torsoWidth, tailWidth), width, smoothstep(-1.34, -1.16, y));
    width *= 1.0 - 0.29 * exp(-pow((y + 0.59) / 0.21, 2.0));
    return width * (0.90 + uFieldShape.x * 0.20);
}

float fieldCenter(float y) {
    float lowerBody = 1.0 - smoothstep(-0.15, 0.58, y);
    return sin(y * 2.02 + uTime * 0.24) * 0.055
        + sin(y * 4.45 - uTime * 0.15) * 0.022
        + sin((y + 1.05) * 2.7 - uTime * 0.10) * lowerBody * 0.140
        - smoothstep(0.68, 1.72, y) * 0.050;
}

vec3 bodyPoint(float y, float theta, float radial, float depthScale) {
    float width = fieldWidth(y) * radial;
    float center = fieldCenter(y);
    float cosine = cos(theta);
    float sine = sin(theta);
    return vec3(center + cosine * width, y, sine * width * depthScale);
}
