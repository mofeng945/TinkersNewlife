#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float EffectTime;
uniform float MaskThreshold;

in vec2 vUv;
in vec4 vColor;
in vec2 vLight;
in vec3 vView;

out vec4 fragColor;

// TNL1024 \u2605\u2605 \u6052\u6b63\u79e9\u5668\uff1a\u2605\u7eaf\u9ed1\u90e8\u5206\u5f53\u906e\u7f69 \u2713
//   \u2605\u906e\u7f69\u91cc\u753b \u2713\uff1a\u2605**\u7ffb\u6eda\u7684\u9ed1\u96fe** \u002b \u2605**\u4e0d\u65ad\u7728\u773c\u7684\u773c\u775b** \u2713\uff08\u2605\u900f\u955c\u611f \u2713\uff09
float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float n00 = hash(i);
    float n10 = hash(i + vec2(1.0, 0.0));
    float n01 = hash(i + vec2(0.0, 1.0));
    float n11 = hash(i + vec2(1.0, 1.0));
    return mix(mix(n00, n10, f.x), mix(n01, n11, f.x), f.y);
}

float fbm(vec2 p) {
    float sum = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 5; i++) {
        sum += amp * noise(p);
        p *= 2.02;
        amp *= 0.5;
    }
    return sum;
}

void main() {
    vec4 tex = texture(Sampler0, vUv);
    if (tex.a < 0.08) {
        discard;
    }
    float luma = dot(tex.rgb, vec3(0.299, 0.587, 0.114));
    float light = max(max(vLight.x, vLight.y), 0.55);
    if (luma >= MaskThreshold) {
        fragColor = tex * vColor * ColorModulator * light;
        return;
    }

    // \u2605\u8fd9\u91cc\u5c31\u662f"\u900f\u955c"\uff1a\u2605\u7528\u89c6\u7a7a\u95f4\u65b9\u5411\u505a\u5750\u6807 \u2713
    vec2 uvp = vUv * 16.0;
    vec3 dir = normalize(vView + vec3(1.0e-4));
    vec2 flow = uvp + vec2(EffectTime * 0.35, -EffectTime * 0.22) + dir.xy * 2.0;

    // \u2605\u2460 \u7ffb\u6eda\u7684\u9ed1\u96fe\uff08\u2605\u4e24\u5c42\u4e0d\u540c\u65cb\u5411 \u2713\uff09
    float m1 = fbm(flow * 0.55);
    float m2 = fbm(flow * 1.10 + vec2(-EffectTime * 0.25, EffectTime * 0.18));
    float mist = clamp(m1 * 0.8 + m2 * 0.4, 0.0, 1.0);
    vec3 col = mix(vec3(0.01, 0.01, 0.02), vec3(0.10, 0.09, 0.13), mist);

    // \u2605\u2461 \u4e0d\u65ad\u7728\u773c\u7684\u773c\u775b\uff08\u2605\u683c\u70b9 \u002b \u2605\u968f\u673a\u7728\u773c\u5468\u671f \u2713\uff09
    vec2 g = flow * 1.6;
    vec2 cell = floor(g);
    vec2 fc = fract(g) - vec2(0.5);
    float h1 = hash(cell);
    if (h1 > 0.62) {                          // \u2605\u53ea\u6709\u4e00\u90e8\u5206\u683c\u5b50\u6709\u773c\u775b \u2713
        vec2 eye = vec2(0.0, 0.0);
        eye.x = (hash(cell + vec2(3.1, 7.7)) - 0.5) * 0.22;
        eye.y = (hash(cell + vec2(9.3, 1.7)) - 0.5) * 0.22;
        float d = length(fc - eye);
        // \u2605\u7728\u773c\uff1a\u2605\u6bcf\u4e2a\u773c\u775b\u81ea\u5df1\u7684\u5468\u671f \u2713
        float phase = hash(cell + vec2(5.5, 2.2)) * 6.2831;
        float blink = fract(EffectTime * 0.28 + hash(cell + vec2(1.1, 4.4)));
        float open = smoothstep(0.0, 0.12, blink) * smoothstep(0.30, 0.18, blink) + 0.15;
        open = clamp(open, 0.12, 1.0);
        float iris = smoothstep(0.20, 0.02, length((fc - eye) * vec2(1.0, 1.0 / max(open, 0.12))));
        float pupil = smoothstep(0.09, 0.0, length((fc - eye) * vec2(1.0, 1.0 / max(open, 0.12))));
        vec3 eyeCol = vec3(0.95, 0.93, 0.80) * iris - vec3(0.85, 0.85, 0.85) * pupil;
        col += eyeCol * step(d, 0.28) * 1.25;
    }

    // \u2605\u52a0\u4e00\u70b9\u6de1\u7d2b\u6c14\u6c1b \u2713
    col += vec3(0.06, 0.02, 0.10) * mist;
    fragColor = vec4(col * light, max(tex.a, 0.92)) * vColor * ColorModulator;
}
