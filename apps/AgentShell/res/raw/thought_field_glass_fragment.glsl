precision highp float;

uniform vec3 uCameraPosition;
uniform float uTime;
uniform float uBackFace;
uniform float uIntensity;
uniform float uGlow;
uniform float uSurfacePass;
varying vec3 vNormal;
varying vec3 vWorldPosition;
varying vec3 vLocal;

const vec3 AMBER = vec3(1.0, 0.51, 0.16);
const vec3 WARM_WHITE = vec3(1.0, 0.92, 0.75);
const vec3 GLASS = vec3(0.58, 0.72, 0.72);

float hash31(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}

void main() {
    vec3 normal = normalize(vNormal);
    vec3 viewDirection = normalize(uCameraPosition - vWorldPosition);
    vec3 lightDirection = normalize(vec3(-0.42, 0.72, 0.86));
    float facing = abs(dot(normal, viewDirection));
    float fresnel = pow(1.0 - facing, 2.65);
    float diffuse = max(dot(normal, lightDirection), 0.0);
    float specular = pow(max(dot(reflect(-lightDirection, normal), viewDirection), 0.0), 42.0);
    float flow = pow(abs(sin(vLocal.y * 8.0 + vLocal.x * 13.0
        + vLocal.z * 9.0 - uTime * 0.72)), 18.0);
    float crossing = pow(abs(sin(vLocal.y * 13.0 - vLocal.x * 19.0
        + uTime * 0.43)), 28.0);
    float grain = step(0.82, hash31(floor(vLocal * vec3(48.0, 64.0, 48.0)
        + vec3(0.0, -uTime * 2.0, 0.0))));

    vec3 color = vec3(0.012, 0.018, 0.019);
    color += GLASS * fresnel * (0.82 + uGlow * 0.34);
    color += WARM_WHITE * specular * 1.55;
    color += AMBER * (flow * 0.32 + crossing * 0.19 + grain * 0.16);
    color += AMBER * diffuse * 0.028;
    color += (AMBER * 0.62 + WARM_WHITE * (flow + grain) * 0.12) * (1.0 - uSurfacePass);
    color *= mix(0.58, 1.0, 1.0 - uBackFace);

    float alpha = 0.040 + fresnel * 0.54 + specular * 0.26
        + flow * 0.055 + crossing * 0.035 + grain * 0.045;
    alpha *= mix(0.58, 1.0, 1.0 - uBackFace) * (0.72 + uIntensity * 0.28);
    alpha += (1.0 - uSurfacePass) * 0.16;
    float endFade = smoothstep(-1.70, -1.53, vLocal.y)
        * (1.0 - smoothstep(1.56, 1.74, vLocal.y));
    float layerFade = mix(1.0, endFade, uSurfacePass);
    color *= layerFade;
    alpha *= layerFade;
    gl_FragColor = vec4(color, clamp(alpha, 0.0, 0.72));
}
