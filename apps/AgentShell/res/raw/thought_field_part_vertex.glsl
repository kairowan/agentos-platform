precision highp float;

attribute vec3 aPosition;
attribute vec3 aNormal;
uniform mat4 uMvp;
uniform mat4 uModel;
uniform float uTime;
uniform float uDeform;
uniform vec3 uInverseScale;
varying vec3 vNormal;
varying vec3 vWorldPosition;
varying vec3 vLocal;

void main() {
    float ripple = sin(aPosition.y * 7.0 + uTime * 1.4)
        + sin(aPosition.x * 8.3 - uTime * 1.1);
    vec3 displaced = aPosition + aNormal * ripple * uDeform;
    vec4 worldPosition = uModel * vec4(displaced, 1.0);
    vWorldPosition = worldPosition.xyz;
    vNormal = normalize(mat3(uModel) * (aNormal * uInverseScale));
    vLocal = displaced;
    gl_Position = uMvp * vec4(displaced, 1.0);
}
