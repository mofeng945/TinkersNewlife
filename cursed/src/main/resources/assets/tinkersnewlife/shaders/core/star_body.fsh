#version 150

// TNL940 \u2605\u2605 \u661f\u9668\u60b2\u53f9\u7684**\u9ed1\u5f13\u8eab\u661f\u7a7a** \u2713
//   \u2605\u53c2\u8003\uff1a\u2605\u8be1\u5384\u9057\u7269\u7684\u4e24\u9897\u77ff\u77f3\uff08\u2605``nebula_anomaly_item`` / ``mirror_anomaly_item`` \u2713\uff09
//   \u2605\u505a\u6cd5\uff1a\u2605\u7528**fbm \u566a\u58f0**\u7b97\u51fa\u661f\u4e91\u4e0e\u661f\u70b9 \u2713\uff0c
//     \u2605\u5e76\u7528**\u8d34\u56fe\u4eae\u5ea6\u505a\u906e\u7f69** \u2713 \u2014\u2014
//     \u2605\u53ea\u6709"\u9ed1\u8272\u90e8\u5206"\u4f1a\u53d8\u661f\u7a7a \u2713\uff0c\u5176\u4f59\u50cf\u7d20\u539f\u6837\u8f93\u51fa \u2713
uniform sampler2D Sampler0;
uniform float EffectTime;
uniform float MaskThreshold;
uniform vec4 ColorModulator;

in vec2 vUv;
in vec4 vColor;
in vec2 vLight;

out vec4 fragColor;

float hash2(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

float noise(vec2 x) {
    vec2 i = floor(x);
    vec2 f = fract(x);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = hash2(i);
    float b = hash2(i + vec2(1.0, 0.0));
    float c = hash2(i + vec2(0.0, 1.0));
    float d = hash2(i + vec2(1.0, 1.0));
    return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
}

float fbm(vec2 x) {
    float v = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 4; i++) {
        v += amp * noise(x);
        x = x * 2.03 + vec2(1.7, 9.2);
        amp *= 0.5;
    }
    return v;
}

void main() {
    vec4 tex = texture(Sampler0, vUv);
    if (tex.a < 0.08) {
        discard;
    }
    float luma = dot(tex.rgb, vec3(0.299, 0.587, 0.114));
    vec3 col;
    if (luma < MaskThreshold) {
        // \u2605\u9ed1\u8272\u90e8\u5206 \u21d2 \u2605\u661f\u7a7a \u2713
        float neb = fbm(vUv * 22.0 + vec2(EffectTime * 0.035, -EffectTime * 0.022));
        float neb2 = fbm(vUv * 7.0 - vec2(EffectTime * 0.012, EffectTime * 0.02));
        float stars = smoothstep(0.90, 1.0, noise(vUv * 120.0 + vec2(EffectTime * 0.55, 0.0)));
        float bright = smoothstep(0.97, 1.0, noise(vUv.yx * 55.0 - vec2(0.0, EffectTime * 0.3)));
        vec3 deep = vec3(0.02, 0.025, 0.06);
        vec3 cloud = vec3(0.35, 0.42, 0.80) * neb * 0.55 + vec3(0.55, 0.35, 0.75) * neb2 * 0.35;
        col = deep + cloud + vec3(1.0) * stars * 0.9 + vec3(0.75, 0.82, 1.0) * bright;
    } else {
        col = tex.rgb;
    }
    float light = max(max(vLight.x, vLight.y), 0.55);
    fragColor = vec4(col * light, tex.a) * vColor * ColorModulator;
}
