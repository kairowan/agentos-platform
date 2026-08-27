// Memory constellation in real 3D: nodes drift on a shell around the figure,
// links connect nearest neighbours, and hub nodes render as four-point stars.
// Real projection gives parallax when the camera orbits.

in vec3 aPosition;
in float aSeed;
uniform mat4 uViewProjection;
uniform vec3 uCameraPosition;
uniform float uMode;
uniform float uPixelScale;
uniform float uLinkGain;
out vec3 vColor;
out float vStar;

void main() {
    vec3 pos = aPosition + 0.045 * vec3(
        sin(uTime * 0.10 + aSeed * 9.0),
        sin(uTime * 0.13 + aSeed * 17.0),
        0.5 * sin(uTime * 0.07 + aSeed * 5.0));
    float depthFade = clamp(1.8 / (distance(uCameraPosition, pos) * 0.6), 0.35, 1.10);
    float shimmer = 0.65 + 0.35 * sin(uTime * 0.4 + aSeed * 23.0);
    vStar = aSeed > 0.86 ? 1.0 : 0.0;
    vec3 tint = mix(vec3(0.86, 0.78, 0.62), vec3(1.0, 0.92, 0.72), aSeed);
    if (uMode < 0.5) {
        vColor = tint * 0.055 * depthFade * shimmer * uLinkGain;
    } else {
        float base = 0.16 + 0.30 * pow(aSeed, 2.0);
        vColor = tint * base * depthFade * uLinkGain;
    }
    gl_Position = uViewProjection * vec4(pos, 1.0);
    if (uMode > 0.5) {
        float size = vStar > 0.5 ? 26.0 : 2.5 + 3.5 * pow(aSeed, 2.0);
        float perspective = clamp(2.6 / max(gl_Position.w, 0.5), 0.55, 1.6);
        gl_PointSize = clamp(size * perspective, 1.5, 30.0);
    } else {
        gl_PointSize = 1.0;
    }
}
