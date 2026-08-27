#version 300 es
precision highp float;

// Port of thought_field_surface_vertex.glsl to GLSL ES 3.00 for the WebGL2
// particle pipeline. Deformation math is kept byte-for-byte equivalent so the
// native GLES fallback and the WebView renderer share one silhouette.

in vec3 aParam;
uniform mat4 uMvp;
uniform mat4 uModel;
uniform float uTime;
uniform vec2 uFieldShape;
uniform float uLayerPhase;
uniform float uLayerScale;
uniform vec3 uInverseScale;
out vec3 vNormal;
out vec3 vWorldPosition;
out vec3 vLocal;

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
    width *= smoothstep(-1.72, -1.63, y) * (1.0 - smoothstep(1.69, 1.78, y));
    return width * (0.90 + uFieldShape.x * 0.20);
}

float fieldCenter(float y) {
    float lowerBody = 1.0 - smoothstep(-0.15, 0.58, y);
    return sin(y * 2.02 + uTime * 0.24) * 0.055
        + sin(y * 4.45 - uTime * 0.15) * 0.022
        + sin((y + 1.05) * 2.7 - uTime * 0.10) * lowerBody * 0.140
        - smoothstep(0.68, 1.72, y) * 0.050;
}

void main() {
    float heightScale = 0.94 + uFieldShape.y * 0.12;
    float y = mix(1.78, -1.72, aParam.x) * heightScale;
    float twist = y * 0.62 + sin(y * 2.3 - uTime * 0.17) * 0.16 + uLayerPhase;
    float twistCos = cos(twist);
    float twistSin = sin(twist);
    float ringX = aParam.y * twistCos - aParam.z * twistSin;
    float ringZ = aParam.y * twistSin + aParam.z * twistCos;
    float width = fieldWidth(y);
    width *= uLayerScale;
    width *= 1.0 + sign(ringX) * 0.09 * sin(y * 2.75 + uTime * 0.17);
    width *= 0.93 + 0.07 * sin(y * 5.1 + ringX * 3.7 + ringZ * 2.4 - uTime * 0.22);
    float depthScale = 0.66 + sin(y * 1.9 + uTime * 0.11) * 0.045;
    float center = fieldCenter(y);
    vec3 localPosition = vec3(center + ringX * width, y, ringZ * width * depthScale);

    float sampleStep = 0.018;
    float widthSlope = (fieldWidth(y + sampleStep) - fieldWidth(y - sampleStep)) / (sampleStep * 2.0);
    float centerSlope = (fieldCenter(y + sampleStep) - fieldCenter(y - sampleStep)) / (sampleStep * 2.0);
    vec3 localNormal = normalize(vec3(
        ringX,
        -widthSlope - centerSlope * ringX,
        ringZ / max(depthScale, 0.01)
    ));
    vec4 worldPosition = uModel * vec4(localPosition, 1.0);
    vWorldPosition = worldPosition.xyz;
    vNormal = normalize(mat3(uModel) * (localNormal * uInverseScale));
    vLocal = vec3(ringX, y, ringZ);
    gl_Position = uMvp * vec4(localPosition, 1.0);
}
